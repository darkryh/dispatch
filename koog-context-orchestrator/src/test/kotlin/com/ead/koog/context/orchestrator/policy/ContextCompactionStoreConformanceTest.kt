package com.ead.koog.context.orchestrator.policy

import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.async.ContextCompactionArtifact
import com.ead.koog.context.orchestrator.async.ContextCompactionJob
import com.ead.koog.context.orchestrator.async.ContextCompactionJobStatus
import com.ead.koog.context.orchestrator.async.ContextCompactionResult
import com.ead.koog.context.orchestrator.async.ContextCompactionStore
import com.ead.koog.context.orchestrator.async.FileContextCompactionStore
import com.ead.koog.context.orchestrator.async.InMemoryContextCompactionStore
import com.ead.koog.context.orchestrator.async.PersistenceContextCompactionStore
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContextCompactionStoreConformanceTest {

    @Test
    fun `in memory store satisfies conformance contract`() = runBlocking {
        runConformanceScenario { InMemoryContextCompactionStore() }
    }

    @Test
    fun `file store satisfies conformance contract`() = runBlocking {
        val tempDir = createTempDirectory(prefix = "ctx-store-")
        val file = tempDir.resolve("compaction-store.json")
        runConformanceScenario { FileContextCompactionStore(file) }
    }

    @Test
    fun `persistence provider store satisfies conformance contract`() = runBlocking {
        val provider = InMemoryPersistenceStorageProvider()
        runConformanceScenario {
            PersistenceContextCompactionStore(
                persistenceStorageProvider = provider,
                stateAgentId = "ctx-compaction-state-test",
            )
        }
    }

    @Test
    fun `file store restores pending and latest artifact across restart`() = runBlocking {
        val tempDir = createTempDirectory(prefix = "ctx-store-restart-")
        val file = tempDir.resolve("compaction-store.json")

        val initial = FileContextCompactionStore(file)
        val pendingJob = job(agentId = "agent-r", sourceVersion = 7, sourceFingerprint = "fp-7", id = "pending")
        assertTrue(initial.enqueue(pendingJob))

        val reloaded1 = FileContextCompactionStore(file)
        val claimed = reloaded1.claimNext("worker-r")
        assertNotNull(claimed)
        assertEquals("pending", claimed.id)

        reloaded1.complete(
            claimed.id,
            ContextCompactionResult(
                status = ContextCompactionJobStatus.SUCCEEDED,
                artifact = ContextCompactionArtifact(
                    agentId = "agent-r",
                    sourceVersion = 7,
                    sourceFingerprint = "fp-7",
                    resultVersion = 7,
                    mode = CompressionMode.AGGRESSIVE,
                    text = "restored",
                ),
            ),
        )

        val reloaded2 = FileContextCompactionStore(file)
        val artifact = reloaded2.latestArtifact("agent-r")
        assertNotNull(artifact)
        assertEquals("restored", artifact.text)
        assertEquals("fp-7", reloaded2.latestFingerprint("agent-r"))
    }

    @Test
    fun `persistence provider store restores pending and latest artifact across restart`() = runBlocking {
        val root = createTempDirectory(prefix = "ctx-checkpoint-store-restart-")
        val provider = JVMFilePersistenceStorageProvider(root)
        val stateAgentId = "ctx-compaction-state-restart"

        val initial = PersistenceContextCompactionStore(
            persistenceStorageProvider = provider,
            stateAgentId = stateAgentId,
        )
        val pendingJob = job(agentId = "agent-r", sourceVersion = 9, sourceFingerprint = "fp-9", id = "pending")
        assertTrue(initial.enqueue(pendingJob))

        val reloaded1 = PersistenceContextCompactionStore(
            persistenceStorageProvider = provider,
            stateAgentId = stateAgentId,
        )
        val claimed = reloaded1.claimNext("worker-r")
        assertNotNull(claimed)
        assertEquals("pending", claimed.id)

        reloaded1.complete(
            claimed.id,
            ContextCompactionResult(
                status = ContextCompactionJobStatus.SUCCEEDED,
                artifact = ContextCompactionArtifact(
                    agentId = "agent-r",
                    sourceVersion = 9,
                    sourceFingerprint = "fp-9",
                    resultVersion = 9,
                    mode = CompressionMode.AGGRESSIVE,
                    text = "restored-persistent",
                ),
            ),
        )

        val reloaded2 = PersistenceContextCompactionStore(
            persistenceStorageProvider = provider,
            stateAgentId = stateAgentId,
        )
        val artifact = reloaded2.latestArtifact("agent-r")
        assertNotNull(artifact)
        assertEquals("restored-persistent", artifact.text)
        assertEquals("fp-9", reloaded2.latestFingerprint("agent-r"))
    }

    private suspend fun runConformanceScenario(factory: () -> ContextCompactionStore) {
        val store = factory()

        val j1 = job(agentId = "agent-c", sourceVersion = 10, sourceFingerprint = "fp-10", id = "j1")
        val j1Dup = j1.copy(id = "j1-dup")
        val j2 = job(agentId = "agent-c", sourceVersion = 11, sourceFingerprint = "fp-11", id = "j2")

        assertTrue(store.enqueue(j1))
        assertFalse(store.enqueue(j1Dup))

        val claimed = store.claimNext("worker-c")
        assertNotNull(claimed)
        assertEquals("j1", claimed.id)

        assertFalse(store.enqueue(j1.copy(id = "j1-running-dup")))

        store.complete(
            claimed.id,
            ContextCompactionResult(
                status = ContextCompactionJobStatus.SUCCEEDED,
                artifact = ContextCompactionArtifact(
                    agentId = "agent-c",
                    sourceVersion = 10,
                    sourceFingerprint = "fp-10",
                    resultVersion = 10,
                    mode = CompressionMode.STRUCTURED,
                    text = "artifact-10",
                ),
            ),
        )

        val latest = store.latestArtifact("agent-c")
        assertNotNull(latest)
        assertEquals("artifact-10", latest.text)
        assertEquals(10L, store.latestVersion("agent-c"))
        assertEquals("fp-10", store.latestFingerprint("agent-c"))

        assertTrue(store.enqueue(j2))

        val claimed2 = store.claimNext("worker-c")
        assertNotNull(claimed2)
        assertEquals("j2", claimed2.id)
        store.complete(
            claimed2.id,
            ContextCompactionResult(
                status = ContextCompactionJobStatus.SUCCEEDED,
                artifact = ContextCompactionArtifact(
                    agentId = "agent-c",
                    sourceVersion = 11,
                    sourceFingerprint = "fp-11",
                    resultVersion = 11,
                    mode = CompressionMode.AGGRESSIVE,
                    text = "artifact-11",
                ),
            ),
        )

        assertEquals(11L, store.latestVersion("agent-c"))
        assertEquals("fp-11", store.latestFingerprint("agent-c"))
    }

    private fun job(
        agentId: String,
        sourceVersion: Long,
        sourceFingerprint: String,
        id: String,
    ): ContextCompactionJob = ContextCompactionJob(
        id = id,
        agentId = agentId,
        sourceVersion = sourceVersion,
        sourceFingerprint = sourceFingerprint,
        mode = CompressionMode.STRUCTURED,
        riskZone = ContextRiskZone.WARNING,
        hints = ContextHints(),
        promptMessages = listOf("u:hello", "a:world"),
    )
}
