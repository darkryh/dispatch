package com.ead.koog.benchmark.koog

import com.ead.koog.benchmark.core.BenchmarkRunStatus
import com.ead.koog.benchmark.core.BenchmarkRunSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KoogBenchmarkRegistryTest {

    @Test
    fun `registry stores and retrieves summaries`() {
        KoogBenchmarkRegistry.clear()

        val run = BenchmarkRunSummary(
            runId = "run-1",
            agentId = "chat-agent",
            strategyName = "chat-mode.planner",
            status = BenchmarkRunStatus.SUCCESS,
            startedAtEpochMs = 10,
            finishedAtEpochMs = 40,
            durationMs = 30,
            llmCallCount = 1,
            llmCallFailedCount = 0,
            llmLatencyMsTotal = 12,
            toolCallCount = 1,
            toolCallFailedCount = 0,
            toolValidationFailedCount = 0,
            toolLatencyMsTotal = 8,
            nodeExecutionCount = 5,
            nodeExecutionFailedCount = 0,
            streamingSessionCount = 1,
            streamingFrameCount = 4,
            streamingLatencyMsTotal = 14,
            inputTokens = 100,
            outputTokens = 50,
            totalTokens = 150,
            provider = "deepseek",
            modelId = "deepseek-chat",
        )

        KoogBenchmarkRegistry.record(run)

        assertNotNull(KoogBenchmarkRegistry.byRunId("run-1"))
        assertEquals(1, KoogBenchmarkRegistry.byAgent("chat-agent").size)
        assertEquals("run-1", KoogBenchmarkRegistry.latestByAgent("chat-agent")?.runId)
    }
}
