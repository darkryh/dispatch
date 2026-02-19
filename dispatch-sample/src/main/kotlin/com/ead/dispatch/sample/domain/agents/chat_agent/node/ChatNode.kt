package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import com.ead.dispatch.sample.domain.agents.chat_agent.chatAgentPrompt
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.*
import com.ead.dispatch.sample.domain.agents.chat_agent.util.saveCheckpointForHistory
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.koog.context.orchestrator.api.*
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSetupAndStreamChatMode(
    name: String? = null,
    repository: StructuredIndexRepository,
    ragContextService: RagContextService,
    contextOrchestrator: KoogContextOrchestrator? = null,
): AIAgentNodeDelegate<ChatTurnInput, ContextualResponse<Flow<StreamFrame>>> =
    node(name) {
        turnInput ->
        val snapshots = MutableStateFlow<ContextSnapshot?>(null)

        val response = setupAndStreamChatMode(
            repository = repository,
            ragContextService = ragContextService,
            contextOrchestrator = contextOrchestrator,
            turnInput = turnInput,
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
    turnInput: ChatTurnInput,
    onContextSnapshot: (ContextSnapshot) -> Unit,
): Flow<StreamFrame> {
    val nodePath = executionInfo.path()
    return channelFlow {
        val agentContext = this@setupAndStreamChatMode
        val runtimeOrchestrator = agentContext.resolveContextOrchestrator(contextOrchestrator)
        val request = turnInput.request

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
            val loadedUserPreferencesContext = agentContext.currentLoadedUserPreferencesContext()

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
                        turnPolicy = turnInput.policy,
                        loadedUserPreferencesContext = loadedUserPreferencesContext,
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
                    hints = baseHints(turnInput, previousToolCalls)
                )
                publishSnapshot()

                val toolCalls = llm.writeSession {
                    val currentToolCalls = mutableListOf<Message.Tool.Call>()
                    val responseBuffer = StringBuilder()
                    val originalTools = tools
                    tools = originalTools.filterAllowedByPolicy(turnInput.policy)

                    try {
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
                    } finally {
                        tools = originalTools
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
                val decisionToolCall = toolCalls.firstOrNull { isDecisionToolName(it.tool) }
                val effectiveToolCalls = decisionToolCall?.let { listOf(it) } ?: toolCalls

                runtimeOrchestrator.beforeToolLoop(
                    context = agentContext,
                    hints = baseHints(turnInput, effectiveToolCalls.size)
                )
                publishSnapshot()

                agentContext.updateChatTurnMetrics { metrics ->
                    metrics.requestedToolCalls += effectiveToolCalls.size
                    effectiveToolCalls.forEach { call ->
                        if (isDecisionToolName(call.tool)) {
                            metrics.decisionToolCalls += 1
                            metrics.selectorShown = true
                        }
                        if (isWriteToolName(call.tool)) {
                            metrics.writeToolCalls += 1
                        }
                    }
                }

                val toolResults = effectiveToolCalls.map { executeToolWithFix(it) }
                toolResults
                    .filter { it.resultKind !is ToolResultKind.Success }
                    .forEach { result ->
                        val payload = result.content.takeIf { it.isNotBlank() } ?: "{\"error\":\"Tool failed\"}"
                        send(StreamFrame.ToolCall(result.id ?: "", result.tool, payload))
                    }

                llm.writeSession {
                    appendPrompt {
                        tool {
                            effectiveToolCalls.forEach { call(it) }
                            toolResults.forEach { result(it) }
                        }
                    }
                }

                previousToolCalls = effectiveToolCalls.size
                runtimeOrchestrator.afterToolLoop(agentContext)
                publishSnapshot()
                if (decisionToolCall != null) break
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
    turnInput: ChatTurnInput,
    recentToolCalls: Int,
): ContextHints {
    val request = turnInput.request
    val policy = turnInput.policy
    val continuity = ContinuityPacket(
        objective = "Follow decision path ${policy.decisionPath.name}/${policy.resolvedAction.name} for this turn.",
        constraints = listOf(
            "Story scope must remain within storyId=${request.storyId}",
            "write_tools_allowed=${policy.allowWriteTools}",
            "require_selector_for_destructive=${policy.requireSelectorForDestructive}",
            "confidence_band=${policy.confidenceBand.name}",
            "risk_class=${policy.riskClass.name}",
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
    val blockedReason = policyBlockReason(call.tool)
    if (blockedReason != null) {
        updateChatTurnMetrics { metrics ->
            metrics.blockedToolCalls += 1
            if (isWriteToolName(call.tool)) {
                metrics.writeToolCallsBlocked += 1
            }
        }
        return blockedToolResult(call, blockedReason)
    }

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

    val result = environment.executeTool(validated)

    updateChatTurnMetrics { metrics ->
        metrics.executedToolCalls += 1
        if (result.resultKind !is ToolResultKind.Success) {
            metrics.failedToolCalls += 1
        }
        mutationAction(result.content)?.let { action ->
            when (action.lowercase()) {
                "create" -> metrics.mutationCreateCount += 1
                "update" -> metrics.mutationUpdateCount += 1
                "delete" -> metrics.mutationDeleteCount += 1
            }
        }
    }

    return result
}

private suspend fun AIAgentGraphContextBase.policyBlockReason(toolName: String?): String? {
    val policy = currentChatTurnPolicy() ?: return null
    if (!isToolAllowedForTurn(policy, toolName)) {
        if (!policy.allowWriteTools && isWriteToolName(toolName)) {
            return "Write tool call blocked: explicit write intent is required for this turn."
        }
        if (policy.requireSelectorForDestructive && isWriteToolName(toolName)) {
            return "Write tool call blocked: call requestUserChoice first for this destructive turn."
        }
        return "Tool call blocked by chat turn policy."
    }
    return null
}

private fun List<ToolDescriptor>.filterAllowedByPolicy(policy: ChatTurnPolicy): List<ToolDescriptor> =
    filter { descriptor -> isToolAllowedForTurn(policy, descriptor.name) }

private fun blockedToolResult(
    call: Message.Tool.Call,
    reason: String,
): ReceivedToolResult {
    val payloadJson = buildJsonObject {
        put("type", JsonPrimitive("failure"))
        put(
            "error",
            buildJsonObject {
                put("code", JsonPrimitive("POLICY_BLOCKED"))
                put("details", JsonPrimitive(reason))
            }
        )
        put("message", JsonPrimitive(reason))
    }

    val toolArgs = runCatching { call.contentJson }.getOrElse { buildJsonObject { } }

    return ReceivedToolResult(
        id = call.id,
        tool = call.tool,
        toolArgs = toolArgs,
        toolDescription = "Blocked by chat policy guard.",
        content = payloadJson.toString(),
        resultKind = ToolResultKind.ValidationError(
            AIAgentError(
                message = reason,
                stackTrace = "",
                cause = "POLICY_GUARD",
            )
        ),
        result = payloadJson,
    )
}

private fun mutationAction(rawResult: String): String? {
    val root = runCatching { Json.parseToJsonElement(rawResult) }.getOrNull() as? JsonObject ?: return null
    if (root["type"]?.jsonPrimitive?.content != "success") return null
    val data = root["data"] as? JsonObject ?: return null
    return data["action"]?.jsonPrimitive?.content
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
                this.model = AIProvider.Chat.fixer
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
