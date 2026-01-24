package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.environment.result
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.chatAgentPrompt
import com.ead.dispatch.sample.domain.agents.chat_agent.util.saveCheckpointForHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSetupAndStreamChatMode(
    name: String? = null,
    repository: StructuredIndexRepository
): AIAgentNodeDelegate<ChatRequest, Flow<StreamFrame>> =
    node(name) { request -> setupAndStreamChatMode(repository,request) }

/**
 * Streams assistant output while keeping prompt history and tool calls in sync.
 * The node rewrites the prompt to inject updated context, then loops until no
 * tool calls are returned in a streaming pass.
 */
private fun AIAgentGraphContextBase.setupAndStreamChatMode(
    repository: StructuredIndexRepository,
    request: ChatRequest
): Flow<StreamFrame> {
    val nodePath = executionInfo.path()
    return channelFlow {
        val agentContext = this@setupAndStreamChatMode
        try {
            llm.writeSession {
            val context = repository.getChatContextForAgent(request.storyId)

            rewritePrompt { existing ->
                val messageHistory = existing.messages.filterNot { it is Message.System }

                val userInputMessage = request.text.trim()
                    .takeIf { it.isNotEmpty() }
                    ?: (messageHistory.lastOrNull { it is Message.User } as? Message.User)?.content

                val basePrompt = chatAgentPrompt(
                    context = context,
                    inputRequest = request,
                )

                basePrompt.withMessages { baseMessages ->
                    val systemMessages = baseMessages.filterIsInstance<Message.System>()

                    buildList {
                        addAll(systemMessages)

                        addAll(messageHistory)

                        if (userInputMessage != null) {
                            add(Message.User(userInputMessage, RequestMetaInfo.create(clock)))
                        }
                    }
                }
            }

            while (true) {
                val toolCalls = mutableListOf<Message.Tool.Call>()

                val responseBuffer = StringBuilder()

                requestLLMStreaming().collect { frame ->
                    if (frame is StreamFrame.ToolCall) {
                        toolCalls.add(
                            Message.Tool.Call(
                                id = frame.id,
                                tool = frame.name,
                                content = frame.content,
                                metaInfo = ResponseMetaInfo.create(clock)
                            )
                        )
                    }

                    if (frame is StreamFrame.Append) {
                        responseBuffer.append(frame.text)
                    }

                    send(frame)
                }

                val responseText = responseBuffer.toString().trim()

                if (responseText.isNotEmpty()) {
                    appendPrompt {
                        assistant(responseText)
                    }
                }

                if (toolCalls.isEmpty()) break

                val toolResults = toolCalls.map { executeToolWithFix(it) }
                toolResults.forEach { result ->
                    if (result.resultKind !is ToolResultKind.Success) {
                        // Avoid stdout prints that corrupt the terminal UI.
                        // Hook up a structured logger if needed.
                    }
                }

                appendPrompt {
                    tool {
                        toolCalls.forEach { call(it) }
                        toolResults.forEach { result(it) }
                    }
                }
            }
        }
        } finally {
            saveCheckpointForHistory(agentContext, request, nodePath)
        }
    }
}

private suspend fun AIAgentGraphContextBase.executeToolWithFix(
    call: Message.Tool.Call,
    retries: Int = 2,
): ai.koog.agents.core.environment.ReceivedToolResult {
    val validated = try {
        call.contentJson
        call
    } catch (error: Exception) {
        val normalized = normalizeToolArgsJson(call.content)
        val normalizedCall = Message.Tool.Call(
            id = call.id,
            tool = call.tool,
            content = normalized,
            metaInfo = call.metaInfo,
        )
        val fallback = try {
            normalizedCall.contentJson
            normalizedCall
        } catch (_: Exception) {
            val fixed = fixToolCallJson(call.tool, call.content, error, retries)
            Message.Tool.Call(
                id = call.id,
                tool = call.tool,
                content = fixed,
                metaInfo = call.metaInfo,
            )
        }
        fallback
    }

    return environment.executeTool(validated)
}

private fun normalizeToolArgsJson(raw: String): String {
    if (raw.isBlank()) return raw
    val result = StringBuilder(raw.length)
    var inString = false
    var escaped = false

    raw.forEach { ch ->
        if (escaped) {
            result.append(ch)
            escaped = false
            return@forEach
        }

        when (ch) {
            '\\' -> {
                result.append(ch)
                escaped = true
            }
            '"' -> {
                result.append(ch)
                inString = !inString
            }
            '\n' -> {
                if (inString) {
                    result.append("\\n")
                } else {
                    result.append(ch)
                }
            }
            '\r' -> {
                if (inString) {
                    result.append("\\r")
                } else {
                    result.append(ch)
                }
            }
            else -> result.append(ch)
        }
    }

    return result.toString()
}

private suspend fun AIAgentGraphContextBase.fixToolCallJson(
    toolName: String,
    content: String,
    exception: Exception,
    retries: Int,
): String {
    var current = content
    var lastError: Exception = exception
    var attempt = 0

    while (attempt++ <= retries) {
        val fixed = llm.writeSession {
            val originalPrompt = prompt
            val originalModel = model
            try {
                rewritePrompt {
                    prompt("tool-args-fixing") {
                        system(
                            """
                            You fix invalid JSON arguments for tool calls.
                            Output ONLY a valid JSON object. No commentary, no backticks.
                            Preserve values; only repair JSON formatting and escaping.
                            """.trimIndent()
                        )
                        user(
                            """
                            TOOL: $toolName
                            ERROR: ${lastError.message ?: "unknown error"}
                            CONTENT:
                            $current
                            """.trimIndent()
                        )
                    }
                }

                val response = requestLLMWithoutTools()
                (response as Message.Assistant).content
            } finally {
                rewritePrompt { originalPrompt }
                model = originalModel
            }
        }.trim()

        try {
            val element = Json.parseToJsonElement(fixed)
            if (element !is kotlinx.serialization.json.JsonObject) {
                throw SerializationException("Tool args must be a JSON object.")
            }
            return fixed
        } catch (parseError: SerializationException) {
            lastError = parseError
            current = fixed
        }
    }

    throw lastError
}
