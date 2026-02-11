package com.ead.koog.benchmark.core

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BenchmarkingTest {

    @Test
    fun `aggregate computes rates and averages`() {
        val aggregate = BenchmarkAnalyzer.aggregate(
            listOf(
                sampleRun(runId = "r1", status = BenchmarkRunStatus.SUCCESS, durationMs = 100, totalTokens = 500),
                sampleRun(runId = "r2", status = BenchmarkRunStatus.FAILED, durationMs = 250, totalTokens = 700, llmCallFailedCount = 1),
            )
        )

        assertEquals(2, aggregate.runCount)
        assertEquals(1, aggregate.successCount)
        assertEquals(1, aggregate.failedCount)
        assertEquals(0.5, aggregate.successRate)
        assertEquals(175.0, aggregate.averageDurationMs)
        assertTrue(aggregate.p95DurationMs >= 100)
        assertTrue(aggregate.averageTotalTokens >= 600.0)
        assertTrue(aggregate.llmFailureRate > 0.0)
    }

    @Test
    fun `gate evaluation fails when thresholds exceeded`() {
        val runs = listOf(
            sampleRun(runId = "r1", status = BenchmarkRunStatus.SUCCESS, durationMs = 1100, totalTokens = 2200),
            sampleRun(runId = "r2", status = BenchmarkRunStatus.FAILED, durationMs = 900, totalTokens = 2100),
        )

        val result = BenchmarkAnalyzer.evaluateGates(
            runs = runs,
            gates = BenchmarkGateConfig(
                minSuccessRate = 0.9,
                maxAverageDurationMs = 800.0,
                maxAverageTotalTokens = 2000.0,
            ),
        )

        assertFalse(result.passed)
        assertTrue(result.failures.isNotEmpty())
    }

    @Test
    fun `jsonl recorder writes benchmark line`() = runBlocking {
        val tempDirectory = createTempDirectory(prefix = "bench-recorder-")
        val recorder = JsonlBenchmarkRecorder(
            outputDirectory = tempDirectory,
            fileNamePrefix = "chat-agent",
        )

        recorder.record(sampleRun(runId = "r-123"))

        val outputPath = tempDirectory.resolve("chat-agent.jsonl")
        assertTrue(outputPath.exists())
        val content = outputPath.readText()
        assertTrue(content.contains("\"runId\":\"r-123\""))
        assertTrue(content.contains("\"agentId\":\"agent-chat\""))
    }

    private fun sampleRun(
        runId: String,
        status: BenchmarkRunStatus = BenchmarkRunStatus.SUCCESS,
        durationMs: Long = 120,
        totalTokens: Int = 900,
        llmCallFailedCount: Int = 0,
    ): BenchmarkRunSummary = BenchmarkRunSummary(
        runId = runId,
        agentId = "agent-chat",
        strategyName = "chat-mode.planner",
        status = status,
        startedAtEpochMs = 1_000,
        finishedAtEpochMs = 1_000 + durationMs,
        durationMs = durationMs,
        llmCallCount = 2,
        llmCallFailedCount = llmCallFailedCount,
        llmLatencyMsTotal = 80,
        toolCallCount = 1,
        toolCallFailedCount = 0,
        toolValidationFailedCount = 0,
        toolLatencyMsTotal = 40,
        nodeExecutionCount = 5,
        nodeExecutionFailedCount = 0,
        streamingSessionCount = 1,
        streamingFrameCount = 12,
        streamingLatencyMsTotal = 100,
        inputTokens = totalTokens / 2,
        outputTokens = totalTokens / 2,
        totalTokens = totalTokens,
        provider = "deepseek",
        modelId = "deepseek-chat",
        attributes = mapOf("env" to "test"),
        errors = emptyList(),
    )
}
