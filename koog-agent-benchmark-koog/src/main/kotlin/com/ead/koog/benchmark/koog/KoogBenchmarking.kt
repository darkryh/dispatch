package com.ead.koog.benchmark.koog

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.prompt.message.ResponseMetaInfo
import com.ead.koog.benchmark.core.BenchmarkRecorder
import com.ead.koog.benchmark.core.BenchmarkRunStatus
import com.ead.koog.benchmark.core.BenchmarkRunSummary
import com.ead.koog.benchmark.core.InMemoryBenchmarkRecorder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class KoogBenchmarkConfig : FeatureConfig() {
    var enabled: Boolean = false
    var recorder: BenchmarkRecorder = InMemoryBenchmarkRecorder()
    var storeInRegistry: Boolean = true
    var staticAttributes: Map<String, String> = emptyMap()
    var extraAttributesProvider: suspend (AIAgentContext) -> Map<String, String> = { emptyMap() }
}

/**
 * In-memory registry for recent benchmark run summaries.
 */
object KoogBenchmarkRegistry {
    private val byRunId = ConcurrentHashMap<String, BenchmarkRunSummary>()
    private val ordered = ConcurrentLinkedDeque<BenchmarkRunSummary>()

    fun record(summary: BenchmarkRunSummary) {
        byRunId[summary.runId] = summary
        ordered.addLast(summary)
    }

    fun byRunId(runId: String): BenchmarkRunSummary? = byRunId[runId]

    fun all(): List<BenchmarkRunSummary> = ordered.toList()

    fun byAgent(agentId: String): List<BenchmarkRunSummary> = ordered.filter { it.agentId == agentId }

    fun latestByAgent(agentId: String): BenchmarkRunSummary? = ordered.lastOrNull { it.agentId == agentId }

    fun clear() {
        byRunId.clear()
        ordered.clear()
    }
}

data class KoogBenchmarkFeatureHandle(
    val enabled: Boolean,
    val recorder: BenchmarkRecorder,
)

object KoogBenchmark {
    object Feature : AIAgentGraphFeature<KoogBenchmarkConfig, KoogBenchmarkFeatureHandle> {
        override val key: AIAgentStorageKey<KoogBenchmarkFeatureHandle> =
            AIAgentStorageKey("dispatch.koog.benchmark.feature")

        override fun createInitialConfig(): KoogBenchmarkConfig = KoogBenchmarkConfig()

        override fun install(
            config: KoogBenchmarkConfig,
            pipeline: AIAgentGraphPipeline,
        ): KoogBenchmarkFeatureHandle {
            val collector = KoogBenchmarkCollector(config)

            if (config.enabled) {
                pipeline.interceptAgentStarting(this) { collector.onAgentStarting(it) }
                pipeline.interceptAgentCompleted(this) { collector.onAgentCompleted(it) }
                pipeline.interceptAgentExecutionFailed(this) { collector.onAgentFailed(it) }

                pipeline.interceptLLMCallStarting(this) { collector.onLlmCallStarting(it) }
                pipeline.interceptLLMCallCompleted(this) { collector.onLlmCallCompleted(it) }

                pipeline.interceptToolCallStarting(this) { collector.onToolCallStarting(it) }
                pipeline.interceptToolCallCompleted(this) { collector.onToolCallCompleted(it) }
                pipeline.interceptToolCallFailed(this) { collector.onToolCallFailed(it) }
                pipeline.interceptToolValidationFailed(this) { collector.onToolValidationFailed(it) }

                pipeline.interceptLLMStreamingStarting(this) { collector.onStreamingStarting(it) }
                pipeline.interceptLLMStreamingFrameReceived(this) { collector.onStreamingFrame(it) }
                pipeline.interceptLLMStreamingCompleted(this) { collector.onStreamingCompleted(it) }
                pipeline.interceptLLMStreamingFailed(this) { collector.onStreamingFailed(it) }

                pipeline.interceptNodeExecutionCompleted(this) { collector.onNodeCompleted(it) }
                pipeline.interceptNodeExecutionFailed(this) { collector.onNodeFailed(it) }
            }

            return KoogBenchmarkFeatureHandle(
                enabled = config.enabled,
                recorder = config.recorder,
            )
        }
    }
}

private class KoogBenchmarkCollector(
    private val config: KoogBenchmarkConfig,
) {
    private val runStates = ConcurrentHashMap<String, MutableRunState>()

    suspend fun onAgentStarting(context: AgentStartingContext) {
        val nowMs = System.currentTimeMillis()
        val state = MutableRunState(
            runId = context.runId,
            agentId = context.agent.id,
            strategyName = context.context.strategyName,
            startedAtEpochMs = nowMs,
            startedAtNano = System.nanoTime(),
        )
        synchronized(state) {
            state.attributes.putAll(config.staticAttributes)
        }
        runStates[context.runId] = state
    }

    suspend fun onAgentCompleted(context: AgentCompletedContext) {
        finalizeRun(
            runId = context.runId,
            fallbackAgentId = context.agentId,
            status = BenchmarkRunStatus.SUCCESS,
            context = context.context,
            error = null,
        )
    }

    suspend fun onAgentFailed(context: AgentExecutionFailedContext) {
        finalizeRun(
            runId = context.runId,
            fallbackAgentId = context.agentId,
            status = BenchmarkRunStatus.FAILED,
            context = context.context,
            error = context.throwable.message ?: context.throwable::class.simpleName.orEmpty(),
        )
    }

    suspend fun onLlmCallStarting(context: LLMCallStartingContext) {
        withState(context.runId) { state ->
            state.llmCallCount += 1
            state.provider = context.model.provider.toString()
            state.modelId = context.model.id
            state.pendingLlmStartNanos.addLast(System.nanoTime())
        }
    }

    suspend fun onLlmCallCompleted(context: LLMCallCompletedContext) {
        withState(context.runId) { state ->
            state.provider = context.model.provider.toString()
            state.modelId = context.model.id

            val startedAt = if (state.pendingLlmStartNanos.isEmpty()) null else state.pendingLlmStartNanos.removeFirst()
            if (startedAt != null) {
                state.llmLatencyMsTotal += elapsedMs(startedAt)
            }

            var anyStructuredTokenUsage = false
            context.responses.forEach { response ->
                val meta: ResponseMetaInfo = response.metaInfo
                val inputTokens = meta.inputTokensCount ?: 0
                val outputTokens = meta.outputTokensCount ?: 0
                val totalTokens = meta.totalTokensCount ?: (inputTokens + outputTokens)
                if (inputTokens > 0 || outputTokens > 0 || totalTokens > 0) {
                    anyStructuredTokenUsage = true
                }
                state.inputTokens += inputTokens
                state.outputTokens += outputTokens
                state.totalTokens += totalTokens
            }

            if (!anyStructuredTokenUsage) {
                val latestPromptUsage = context.prompt.latestTokenUsage
                if (latestPromptUsage > 0) {
                    state.totalTokens += latestPromptUsage
                }
            }
        }
    }

    suspend fun onToolCallStarting(context: ToolCallStartingContext) {
        withState(context.runId) { state ->
            state.toolCallCount += 1
            val toolCallId = context.toolCallId.orEmpty().ifBlank { context.eventId }
            state.pendingToolStartNanosById[toolCallId] = System.nanoTime()
        }
    }

    suspend fun onToolCallCompleted(context: ToolCallCompletedContext) {
        withState(context.runId) { state ->
            val toolCallId = context.toolCallId.orEmpty().ifBlank { context.eventId }
            val start = state.pendingToolStartNanosById.remove(toolCallId)
            if (start != null) {
                state.toolLatencyMsTotal += elapsedMs(start)
            }
        }
    }

    suspend fun onToolCallFailed(context: ToolCallFailedContext) {
        withState(context.runId) { state ->
            val toolCallId = context.toolCallId.orEmpty().ifBlank { context.eventId }
            if (!state.pendingToolStartNanosById.containsKey(toolCallId)) {
                state.toolCallCount += 1
            }
            state.toolCallFailedCount += 1
            state.pendingToolStartNanosById.remove(toolCallId)?.let { start ->
                state.toolLatencyMsTotal += elapsedMs(start)
            }
            val errorMessage = context.error?.message.orEmpty()
            state.errors += context.message.ifBlank { errorMessage }
        }
    }

    suspend fun onToolValidationFailed(context: ToolValidationFailedContext) {
        withState(context.runId) { state ->
            val toolCallId = context.toolCallId.orEmpty().ifBlank { context.eventId }
            if (!state.pendingToolStartNanosById.containsKey(toolCallId)) {
                state.toolCallCount += 1
            }
            state.toolValidationFailedCount += 1
            state.pendingToolStartNanosById.remove(toolCallId)?.let { start ->
                state.toolLatencyMsTotal += elapsedMs(start)
            }
            val errorMessage = context.error?.message.orEmpty()
            state.errors += context.message.ifBlank { errorMessage }
        }
    }

    suspend fun onStreamingStarting(context: LLMStreamingStartingContext) {
        withState(context.runId) { state ->
            state.streamingSessionCount += 1
            state.pendingStreamingStartNanos.addLast(System.nanoTime())
            state.provider = context.model.provider.toString()
            state.modelId = context.model.id
        }
    }

    suspend fun onStreamingFrame(context: LLMStreamingFrameReceivedContext) {
        withState(context.runId) { state ->
            state.streamingFrameCount += 1
        }
    }

    suspend fun onStreamingCompleted(context: LLMStreamingCompletedContext) {
        withState(context.runId) { state ->
            val start = if (state.pendingStreamingStartNanos.isEmpty()) null else state.pendingStreamingStartNanos.removeFirst()
            if (start != null) {
                state.streamingLatencyMsTotal += elapsedMs(start)
            }
            state.provider = context.model.provider.toString()
            state.modelId = context.model.id
        }
    }

    suspend fun onStreamingFailed(context: LLMStreamingFailedContext) {
        withState(context.runId) { state ->
            state.llmCallFailedCount += 1
            val start = if (state.pendingStreamingStartNanos.isEmpty()) null else state.pendingStreamingStartNanos.removeFirst()
            if (start != null) {
                state.streamingLatencyMsTotal += elapsedMs(start)
            }
            state.provider = context.model.provider.toString()
            state.modelId = context.model.id
            state.errors += (context.error.message ?: context.error::class.simpleName.orEmpty())
        }
    }

    suspend fun onNodeCompleted(context: NodeExecutionCompletedContext) {
        withState(context.context.runId) { state ->
            state.nodeExecutionCount += 1
        }
    }

    suspend fun onNodeFailed(context: NodeExecutionFailedContext) {
        withState(context.context.runId) { state ->
            state.nodeExecutionCount += 1
            state.nodeExecutionFailedCount += 1
            state.errors += (context.throwable.message ?: context.throwable::class.simpleName.orEmpty())
        }
    }

    private suspend fun finalizeRun(
        runId: String,
        fallbackAgentId: String,
        status: BenchmarkRunStatus,
        context: AIAgentContext,
        error: String?,
    ) {
        val existing = runStates.remove(runId)
        val fallbackState = MutableRunState(
            runId = runId,
            agentId = fallbackAgentId,
            strategyName = context.strategyName,
            startedAtEpochMs = System.currentTimeMillis(),
            startedAtNano = System.nanoTime(),
        )

        val state = existing ?: fallbackState

        val nowMs = System.currentTimeMillis()
        val durationMs = elapsedMs(state.startedAtNano)

        val attributes = buildMap {
            putAll(state.attributes)
            putAll(config.extraAttributesProvider(context))
        }

        val errors = buildList {
            addAll(state.errors.filter { it.isNotBlank() })
            if (!error.isNullOrBlank()) add(error)
        }

        val summary = BenchmarkRunSummary(
            runId = runId,
            agentId = state.agentId,
            strategyName = state.strategyName,
            status = status,
            startedAtEpochMs = state.startedAtEpochMs,
            finishedAtEpochMs = nowMs,
            durationMs = durationMs,
            llmCallCount = state.llmCallCount,
            llmCallFailedCount = state.llmCallFailedCount,
            llmLatencyMsTotal = state.llmLatencyMsTotal,
            toolCallCount = state.toolCallCount,
            toolCallFailedCount = state.toolCallFailedCount,
            toolValidationFailedCount = state.toolValidationFailedCount,
            toolLatencyMsTotal = state.toolLatencyMsTotal,
            nodeExecutionCount = state.nodeExecutionCount,
            nodeExecutionFailedCount = state.nodeExecutionFailedCount,
            streamingSessionCount = state.streamingSessionCount,
            streamingFrameCount = state.streamingFrameCount,
            streamingLatencyMsTotal = state.streamingLatencyMsTotal,
            inputTokens = state.inputTokens,
            outputTokens = state.outputTokens,
            totalTokens = state.totalTokens,
            provider = state.provider,
            modelId = state.modelId,
            attributes = attributes,
            errors = errors,
        )

        if (config.storeInRegistry) {
            KoogBenchmarkRegistry.record(summary)
        }

        runCatching {
            config.recorder.record(summary)
        }
    }

    private suspend fun withState(runId: String, block: (MutableRunState) -> Unit) {
        val state = runStates[runId] ?: return
        synchronized(state) {
            block(state)
        }
    }

    private fun elapsedMs(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)

    private data class MutableRunState(
        val runId: String,
        val agentId: String,
        val strategyName: String,
        val startedAtEpochMs: Long,
        val startedAtNano: Long,
        var llmCallCount: Int = 0,
        var llmCallFailedCount: Int = 0,
        var llmLatencyMsTotal: Long = 0,
        var toolCallCount: Int = 0,
        var toolCallFailedCount: Int = 0,
        var toolValidationFailedCount: Int = 0,
        var toolLatencyMsTotal: Long = 0,
        var nodeExecutionCount: Int = 0,
        var nodeExecutionFailedCount: Int = 0,
        var streamingSessionCount: Int = 0,
        var streamingFrameCount: Int = 0,
        var streamingLatencyMsTotal: Long = 0,
        var inputTokens: Int = 0,
        var outputTokens: Int = 0,
        var totalTokens: Int = 0,
        var provider: String = "",
        var modelId: String = "",
        val attributes: MutableMap<String, String> = linkedMapOf(),
        val errors: MutableList<String> = mutableListOf(),
        val pendingLlmStartNanos: ArrayDeque<Long> = ArrayDeque(),
        val pendingStreamingStartNanos: ArrayDeque<Long> = ArrayDeque(),
        val pendingToolStartNanosById: MutableMap<String, Long> = mutableMapOf(),
    )
}
