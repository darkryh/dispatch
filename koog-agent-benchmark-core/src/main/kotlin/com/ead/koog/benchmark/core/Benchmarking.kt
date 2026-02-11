package com.ead.koog.benchmark.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.ceil

enum class BenchmarkRunStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class BenchmarkRunSummary(
    val runId: String,
    val agentId: String,
    val strategyName: String,
    val status: BenchmarkRunStatus,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val durationMs: Long,
    val llmCallCount: Int,
    val llmCallFailedCount: Int,
    val llmLatencyMsTotal: Long,
    val toolCallCount: Int,
    val toolCallFailedCount: Int,
    val toolValidationFailedCount: Int,
    val toolLatencyMsTotal: Long,
    val nodeExecutionCount: Int,
    val nodeExecutionFailedCount: Int,
    val streamingSessionCount: Int,
    val streamingFrameCount: Int,
    val streamingLatencyMsTotal: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val provider: String,
    val modelId: String,
    val attributes: Map<String, String> = emptyMap(),
    val errors: List<String> = emptyList(),
)

data class BenchmarkAggregate(
    val runCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val cancelledCount: Int,
    val successRate: Double,
    val averageDurationMs: Double,
    val p95DurationMs: Long,
    val averageLlmCalls: Double,
    val averageToolCalls: Double,
    val averageLlmLatencyMs: Double,
    val averageToolLatencyMs: Double,
    val averageInputTokens: Double,
    val averageOutputTokens: Double,
    val averageTotalTokens: Double,
    val llmFailureRate: Double,
    val toolFailureRate: Double,
    val nodeFailureRate: Double,
)

data class BenchmarkGateConfig(
    val minSuccessRate: Double? = null,
    val maxAverageDurationMs: Double? = null,
    val maxP95DurationMs: Long? = null,
    val maxAverageTotalTokens: Double? = null,
    val maxLlmFailureRate: Double? = null,
    val maxToolFailureRate: Double? = null,
    val maxNodeFailureRate: Double? = null,
)

data class BenchmarkGateResult(
    val passed: Boolean,
    val failures: List<String>,
    val aggregate: BenchmarkAggregate,
)

object BenchmarkAnalyzer {
    fun aggregate(runs: List<BenchmarkRunSummary>): BenchmarkAggregate {
        if (runs.isEmpty()) {
            return BenchmarkAggregate(
                runCount = 0,
                successCount = 0,
                failedCount = 0,
                cancelledCount = 0,
                successRate = 0.0,
                averageDurationMs = 0.0,
                p95DurationMs = 0,
                averageLlmCalls = 0.0,
                averageToolCalls = 0.0,
                averageLlmLatencyMs = 0.0,
                averageToolLatencyMs = 0.0,
                averageInputTokens = 0.0,
                averageOutputTokens = 0.0,
                averageTotalTokens = 0.0,
                llmFailureRate = 0.0,
                toolFailureRate = 0.0,
                nodeFailureRate = 0.0,
            )
        }

        val runCount = runs.size
        val successCount = runs.count { it.status == BenchmarkRunStatus.SUCCESS }
        val failedCount = runs.count { it.status == BenchmarkRunStatus.FAILED }
        val cancelledCount = runs.count { it.status == BenchmarkRunStatus.CANCELLED }

        val sumDuration = runs.sumOf { it.durationMs }
        val sumLlmCalls = runs.sumOf { it.llmCallCount }
        val sumToolCalls = runs.sumOf { it.toolCallCount }
        val sumInputTokens = runs.sumOf { it.inputTokens }
        val sumOutputTokens = runs.sumOf { it.outputTokens }
        val sumTotalTokens = runs.sumOf { it.totalTokens }

        val llmCallCount = sumLlmCalls.coerceAtLeast(1)
        val toolCallCount = sumToolCalls.coerceAtLeast(1)
        val nodeExecutionCount = runs.sumOf { it.nodeExecutionCount }.coerceAtLeast(1)

        val llmFailureRate = runs.sumOf { it.llmCallFailedCount }.toDouble() / llmCallCount.toDouble()
        val toolFailureRate = (runs.sumOf { it.toolCallFailedCount } + runs.sumOf { it.toolValidationFailedCount }).toDouble() /
            toolCallCount.toDouble()
        val nodeFailureRate = runs.sumOf { it.nodeExecutionFailedCount }.toDouble() / nodeExecutionCount.toDouble()

        return BenchmarkAggregate(
            runCount = runCount,
            successCount = successCount,
            failedCount = failedCount,
            cancelledCount = cancelledCount,
            successRate = successCount.toDouble() / runCount.toDouble(),
            averageDurationMs = sumDuration.toDouble() / runCount.toDouble(),
            p95DurationMs = percentile(runs.map { it.durationMs }, percentile = 0.95),
            averageLlmCalls = sumLlmCalls.toDouble() / runCount.toDouble(),
            averageToolCalls = sumToolCalls.toDouble() / runCount.toDouble(),
            averageLlmLatencyMs = runs.sumOf { it.llmLatencyMsTotal }.toDouble() / llmCallCount.toDouble(),
            averageToolLatencyMs = runs.sumOf { it.toolLatencyMsTotal }.toDouble() / toolCallCount.toDouble(),
            averageInputTokens = sumInputTokens.toDouble() / runCount.toDouble(),
            averageOutputTokens = sumOutputTokens.toDouble() / runCount.toDouble(),
            averageTotalTokens = sumTotalTokens.toDouble() / runCount.toDouble(),
            llmFailureRate = llmFailureRate,
            toolFailureRate = toolFailureRate,
            nodeFailureRate = nodeFailureRate,
        )
    }

    fun evaluateGates(
        runs: List<BenchmarkRunSummary>,
        gates: BenchmarkGateConfig,
    ): BenchmarkGateResult {
        val aggregate = aggregate(runs)
        val failures = buildList {
            gates.minSuccessRate?.let { minValue ->
                if (aggregate.successRate < minValue) {
                    add("success_rate=${format(aggregate.successRate)} < required=${format(minValue)}")
                }
            }
            gates.maxAverageDurationMs?.let { maxValue ->
                if (aggregate.averageDurationMs > maxValue) {
                    add("avg_duration_ms=${format(aggregate.averageDurationMs)} > limit=${format(maxValue)}")
                }
            }
            gates.maxP95DurationMs?.let { maxValue ->
                if (aggregate.p95DurationMs > maxValue) {
                    add("p95_duration_ms=${aggregate.p95DurationMs} > limit=$maxValue")
                }
            }
            gates.maxAverageTotalTokens?.let { maxValue ->
                if (aggregate.averageTotalTokens > maxValue) {
                    add("avg_total_tokens=${format(aggregate.averageTotalTokens)} > limit=${format(maxValue)}")
                }
            }
            gates.maxLlmFailureRate?.let { maxValue ->
                if (aggregate.llmFailureRate > maxValue) {
                    add("llm_failure_rate=${format(aggregate.llmFailureRate)} > limit=${format(maxValue)}")
                }
            }
            gates.maxToolFailureRate?.let { maxValue ->
                if (aggregate.toolFailureRate > maxValue) {
                    add("tool_failure_rate=${format(aggregate.toolFailureRate)} > limit=${format(maxValue)}")
                }
            }
            gates.maxNodeFailureRate?.let { maxValue ->
                if (aggregate.nodeFailureRate > maxValue) {
                    add("node_failure_rate=${format(aggregate.nodeFailureRate)} > limit=${format(maxValue)}")
                }
            }
        }

        return BenchmarkGateResult(
            passed = failures.isEmpty(),
            failures = failures,
            aggregate = aggregate,
        )
    }

    private fun percentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val rank = ceil(percentile * sorted.size).toInt().coerceAtLeast(1)
        return sorted[(rank - 1).coerceAtMost(sorted.lastIndex)]
    }

    private fun format(value: Double): String = "%.4f".format(value)
}

interface BenchmarkRecorder {
    suspend fun record(runSummary: BenchmarkRunSummary)
}

class InMemoryBenchmarkRecorder : BenchmarkRecorder {
    private val mutex = Mutex()
    private val summaries = mutableListOf<BenchmarkRunSummary>()

    override suspend fun record(runSummary: BenchmarkRunSummary) {
        mutex.withLock {
            summaries += runSummary
        }
    }

    suspend fun snapshot(): List<BenchmarkRunSummary> = mutex.withLock { summaries.toList() }

    suspend fun clear() {
        mutex.withLock {
            summaries.clear()
        }
    }
}

class JsonlBenchmarkRecorder(
    private val outputDirectory: Path,
    private val fileNamePrefix: String = "agent-benchmark",
    private val json: Json = Json,
) : BenchmarkRecorder {
    private val mutex = Mutex()

    override suspend fun record(runSummary: BenchmarkRunSummary) {
        val line = json.encodeToString(JsonObject.serializer(), runSummary.toJson())
        val outputPath = outputDirectory.resolve("$fileNamePrefix.jsonl")

        mutex.withLock {
            Files.createDirectories(outputDirectory)
            Files.writeString(
                outputPath,
                "$line\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun BenchmarkRunSummary.toJson(): JsonObject {
        val attributesJson = JsonObject(attributes.mapValues { JsonPrimitive(it.value) })
        val errorsJson = JsonArray(errors.map(::JsonPrimitive))

        return JsonObject(
            mapOf(
                "runId" to JsonPrimitive(runId),
                "agentId" to JsonPrimitive(agentId),
                "strategyName" to JsonPrimitive(strategyName),
                "status" to JsonPrimitive(status.name),
                "startedAtEpochMs" to JsonPrimitive(startedAtEpochMs),
                "finishedAtEpochMs" to JsonPrimitive(finishedAtEpochMs),
                "durationMs" to JsonPrimitive(durationMs),
                "llmCallCount" to JsonPrimitive(llmCallCount),
                "llmCallFailedCount" to JsonPrimitive(llmCallFailedCount),
                "llmLatencyMsTotal" to JsonPrimitive(llmLatencyMsTotal),
                "toolCallCount" to JsonPrimitive(toolCallCount),
                "toolCallFailedCount" to JsonPrimitive(toolCallFailedCount),
                "toolValidationFailedCount" to JsonPrimitive(toolValidationFailedCount),
                "toolLatencyMsTotal" to JsonPrimitive(toolLatencyMsTotal),
                "nodeExecutionCount" to JsonPrimitive(nodeExecutionCount),
                "nodeExecutionFailedCount" to JsonPrimitive(nodeExecutionFailedCount),
                "streamingSessionCount" to JsonPrimitive(streamingSessionCount),
                "streamingFrameCount" to JsonPrimitive(streamingFrameCount),
                "streamingLatencyMsTotal" to JsonPrimitive(streamingLatencyMsTotal),
                "inputTokens" to JsonPrimitive(inputTokens),
                "outputTokens" to JsonPrimitive(outputTokens),
                "totalTokens" to JsonPrimitive(totalTokens),
                "provider" to JsonPrimitive(provider),
                "modelId" to JsonPrimitive(modelId),
                "attributes" to attributesJson,
                "errors" to errorsJson,
            )
        )
    }
}
