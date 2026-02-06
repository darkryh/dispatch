package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.environment.result
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.ContextualMetadata
import com.ead.koog.context.orchestrator.api.ContextualResponse
import com.ead.koog.context.orchestrator.api.KoogContextOrchestrator
import com.ead.koog.context.orchestrator.api.TaskPhase
import com.ead.koog.context.orchestrator.api.resolveContextOrchestrator
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import com.ead.dispatch.sample.domain.agents.chat_agent.chatAgentPrompt
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.agents.chat_agent.util.saveCheckpointForHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSetupAndStreamChatMode(
    name: String? = null,
    repository: StructuredIndexRepository,
    ragContextService: RagContextService,
    contextOrchestrator: KoogContextOrchestrator? = null,
): AIAgentNodeDelegate<ChatRequest, ContextualResponse<Flow<StreamFrame>>> =
    node(name) {
        request ->
        val snapshots = MutableStateFlow<ContextSnapshot?>(null)
        val response = setupAndStreamChatMode(
            repository = repository,
            ragContextService = ragContextService,
            contextOrchestrator = contextOrchestrator,
            request = request,
            onContextSnapshot = { snapshot -> snapshots.value = snapshot },
        )
        ContextualResponse(
            value = response,
            metadata = ContextualMetadata(
                contextSnapshots = snapshots.asStateFlow(),
            ),
        )
    }

/**
 * Streams assistant output while keeping prompt history and tool calls in sync.
 * The node rewrites the prompt to inject updated context, then loops until no
 * tool calls are returned in a streaming pass.
 */
private fun AIAgentGraphContextBase.setupAndStreamChatMode(
    repository: StructuredIndexRepository,
    ragContextService: RagContextService,
    contextOrchestrator: KoogContextOrchestrator?,
    request: ChatRequest,
    onContextSnapshot: (ContextSnapshot) -> Unit,
): Flow<StreamFrame> {
    val nodePath = executionInfo.path()
    return channelFlow {
        val agentContext = this@setupAndStreamChatMode
        val runtimeOrchestrator = agentContext.resolveContextOrchestrator(contextOrchestrator)

        fun publishSnapshot() {
            onContextSnapshot(runtimeOrchestrator.snapshot())
        }

        publishSnapshot()
        try {
            val context = withContext(Dispatchers.IO) {
                repository.getChatContextForAgent(request.storyId)
            }

            val ragQuery = request.text.trim()
            val ragContext = withContext(Dispatchers.IO) {
                ragContextService.getRagContextChunks(
                    storyId = request.storyId,
                    query = ragQuery,
                )
            }

            llm.writeSession {
                rewritePrompt { existing ->
                    val messageHistory = existing.messages.filterNot { it is Message.System }

                    val userInputMessage = request.text.trim()
                        .takeIf { it.isNotEmpty() }
                        ?: (messageHistory.lastOrNull { it is Message.User } as? Message.User)?.content

                    val basePrompt = chatAgentPrompt(
                        context = context,
                        inputRequest = request,
                        ragContext = ragContext,
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
            }

            var previousToolCalls = 0
            while (true) {
                runtimeOrchestrator.beforeLlmCall(
                    context = agentContext,
                    hints = baseHints(request, previousToolCalls)
                )
                publishSnapshot()

                val toolCalls = llm.writeSession {
                    val currentToolCalls = mutableListOf<Message.Tool.Call>()
                    val responseBuffer = StringBuilder()

                    requestLLMStreaming().collect { frame ->
                        if (frame is StreamFrame.ToolCall) {
                            currentToolCalls.add(
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

                    currentToolCalls
                }

                runtimeOrchestrator.afterLlmCall(agentContext)
                publishSnapshot()

                if (toolCalls.isEmpty()) break

                runtimeOrchestrator.beforeToolLoop(
                    context = agentContext,
                    hints = baseHints(request, toolCalls.size)
                )
                publishSnapshot()

                val toolResults = toolCalls.map { executeToolWithFix(it) }
                toolResults
                    .filter { it.resultKind !is ToolResultKind.Success }
                    .forEach { result ->
                        val payload = result.content.takeIf { it.isNotBlank() } ?: "{\"error\":\"Tool failed\"}"
                        send(StreamFrame.ToolCall(result.id ?: "", result.tool, payload))
                    }

                llm.writeSession {
                    appendPrompt {
                        tool {
                            toolCalls.forEach { call(it) }
                            toolResults.forEach { result(it) }
                        }
                    }
                }

                previousToolCalls = toolCalls.size
                runtimeOrchestrator.afterToolLoop(agentContext)
                publishSnapshot()
            }
        } finally {
            publishSnapshot()
            saveCheckpointForHistory(
                context = agentContext,
                request = request,
                nodePath = nodePath,
                contextSnapshot = runtimeOrchestrator.snapshot(),
            )
        }
    }
}

private fun baseHints(
    request: ChatRequest,
    recentToolCalls: Int,
): ContextHints {
    val continuity = ContinuityPacket(
        objective = "Respond to the user's request and only persist story changes on explicit write intent.",
        constraints = listOf(
            "Story scope must remain within storyId=${request.storyId}",
            "Do not fabricate tool outputs or IDs.",
        ),
        pendingActions = listOf("Process latest user input: ${request.text.take(140)}"),
        criticalReferences = listOf("storyId=${request.storyId}"),
    )

    return ContextHints(
        phase = TaskPhase.EXECUTION,
        recentToolCalls = recentToolCalls,
        factConcepts = PreferencesMemory.userConcepts,
        continuityPacket = continuity,
    )
}

private suspend fun AIAgentGraphContextBase.executeToolWithFix(
    call: Message.Tool.Call,
    retries: Int = 2,
): ReceivedToolResult {
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
            if (element !is JsonObject) {
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
