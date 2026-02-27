package com.ead.koog.context.orchestrator.policy

import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.async.ContextCompactionJob
import com.ead.koog.context.orchestrator.async.ContextCompactionJobStatus
import com.ead.koog.context.orchestrator.async.ContextCompactionWorker
import com.ead.koog.context.orchestrator.async.DeterministicContextCompactorBackend
import com.ead.koog.context.orchestrator.async.InMemoryContextCompactionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AsyncCompactionPipelineTest {

    @Test
    fun `store deduplicates same job key`() = runBlocking {
        val store = InMemoryContextCompactionStore()
        val first = ContextCompactionJob(
            agentId = "agent-1",
            sourceVersion = 10,
            sourceFingerprint = "fp-10",
            mode = CompressionMode.AGGRESSIVE,
            riskZone = ContextRiskZone.CRITICAL,
            hints = ContextHints(),
            promptMessages = listOf("hello"),
        )
        val duplicate = first.copy(id = "job-2")

        val acceptedFirst = store.enqueue(first)
        val acceptedDuplicate = store.enqueue(duplicate)

        assertTrue(acceptedFirst)
        assertFalse(acceptedDuplicate)
    }

    @Test
    fun `store rejects duplicate key while original job is running`() = runBlocking {
        val store = InMemoryContextCompactionStore()
        val job = ContextCompactionJob(
            id = "job-1",
            agentId = "agent-running",
            sourceVersion = 5,
            sourceFingerprint = "fp-5",
            mode = CompressionMode.STRUCTURED,
            riskZone = ContextRiskZone.WARNING,
            hints = ContextHints(),
            promptMessages = listOf("a"),
        )

        assertTrue(store.enqueue(job))
        val claimed = store.claimNext("worker-1")
        assertNotNull(claimed)

        val duplicateAccepted = store.enqueue(job.copy(id = "job-2"))
        assertFalse(duplicateAccepted)
    }

    @Test
    fun `worker produces latest artifact for agent`() = runBlocking {
        val store = InMemoryContextCompactionStore()
        val backend = DeterministicContextCompactorBackend(maxMessages = 4)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val worker = ContextCompactionWorker(
            store = store,
            backend = backend,
            workerId = "test-worker",
            pollingDelayMillis = 10,
        )
        val workerJob = worker.start(scope)

        val enqueued = store.enqueue(
            ContextCompactionJob(
                agentId = "agent-2",
                sourceVersion = 15,
                sourceFingerprint = "fp-15",
                mode = CompressionMode.EMERGENCY,
                riskZone = ContextRiskZone.EMERGENCY,
                hints = ContextHints(),
                promptMessages = listOf("u:hello", "a:world", "u:need summary"),
            ),
        )
        assertTrue(enqueued)

        repeat(50) {
            val artifact = store.latestArtifact("agent-2")
            if (artifact != null) {
                assertEquals(CompressionMode.EMERGENCY, artifact.mode)
                assertTrue(artifact.text.contains("COMPACTED CONTEXT"))
                workerJob.cancel()
                scope.cancel()
                return@runBlocking
            }
            delay(20)
        }

        workerJob.cancel()
        scope.cancel()
        error("Expected compaction artifact was not produced in time")
    }

    @Test
    fun `stale source version does not override newer artifact`() = runBlocking {
        val store = InMemoryContextCompactionStore()

        store.enqueue(
            ContextCompactionJob(
                id = "new",
                agentId = "agent-3",
                sourceVersion = 20,
                sourceFingerprint = "fp-20",
                mode = CompressionMode.AGGRESSIVE,
                riskZone = ContextRiskZone.CRITICAL,
                hints = ContextHints(),
                promptMessages = listOf("new"),
            ),
        )

        val claimedNew = store.claimNext("w")
        assertNotNull(claimedNew)
        store.complete(
            claimedNew.id,
            com.ead.koog.context.orchestrator.async.ContextCompactionResult(
                status = ContextCompactionJobStatus.SUCCEEDED,
                artifact = com.ead.koog.context.orchestrator.async.ContextCompactionArtifact(
                    agentId = "agent-3",
                    sourceVersion = 20,
                    sourceFingerprint = "fp-20",
                    resultVersion = 20,
                    mode = CompressionMode.AGGRESSIVE,
                    text = "new artifact",
                ),
            ),
        )

        val staleAccepted = store.enqueue(
            ContextCompactionJob(
                id = "stale",
                agentId = "agent-3",
                sourceVersion = 19,
                sourceFingerprint = "fp-19",
                mode = CompressionMode.LIGHT,
                riskZone = ContextRiskZone.WATCH,
                hints = ContextHints(),
                promptMessages = listOf("old"),
            ),
        )

        assertFalse(staleAccepted)
        val artifact = store.latestArtifact("agent-3")
        assertNotNull(artifact)
        assertEquals(20, artifact.resultVersion)
    }
}
