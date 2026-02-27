package com.ead.koog.context.orchestrator.async

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.message.Message
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Persistence-provider-backed compaction store.
 *
 * State is serialized into checkpoints for [stateAgentId], so backend durability comes from
 * the configured [PersistenceStorageProvider] (file, DB, etc.).
 */
class PersistenceContextCompactionStore(
    private val persistenceStorageProvider: PersistenceStorageProvider<*>,
    private val stateAgentId: String = "__koog_context_compaction_store__",
    private val checkpointNodePath: String = "koog-context-compaction-store",
) : ContextCompactionStore {
    private val mutex = Mutex()
    private var stateLoaded = false
    private var state = CompactionStoreState()

    override suspend fun enqueue(job: ContextCompactionJob): Boolean = mutex.withLock {
        ensureLoaded()
        val latest = state.latestVersionByAgent[job.agentId] ?: 0L
        if (job.sourceVersion < latest) return false

        val duplicateQueued = state.queue.any {
            it.agentId == job.agentId &&
                it.sourceVersion == job.sourceVersion &&
                it.sourceFingerprint == job.sourceFingerprint &&
                it.mode == job.mode
        }
        val duplicateRunning = state.running.values.any {
            it.agentId == job.agentId &&
                it.sourceVersion == job.sourceVersion &&
                it.sourceFingerprint == job.sourceFingerprint &&
                it.mode == job.mode
        }
        if (duplicateQueued || duplicateRunning) return false

        state.queue.addLast(job)
        persistState()
        true
    }

    override suspend fun claimNext(workerId: String): ContextCompactionJob? = mutex.withLock {
        ensureLoaded()
        val job = state.queue.removeFirstOrNull() ?: return null
        state.running[job.id] = job
        persistState()
        job
    }

    override suspend fun complete(jobId: String, result: ContextCompactionResult): Unit = mutex.withLock {
        ensureLoaded()
        val job = state.running.remove(jobId) ?: return
        val latest = state.latestVersionByAgent[job.agentId] ?: 0L

        when (result.status) {
            ContextCompactionJobStatus.SUCCEEDED -> {
                val artifact = result.artifact ?: return
                if (artifact.resultVersion > latest ||
                    (artifact.resultVersion == latest && state.latestFingerprintByAgent[job.agentId] != artifact.sourceFingerprint)
                ) {
                    state.latestVersionByAgent[job.agentId] = artifact.resultVersion
                    state.latestFingerprintByAgent[job.agentId] = artifact.sourceFingerprint
                    state.latestArtifactByAgent[job.agentId] = artifact
                }
            }

            ContextCompactionJobStatus.STALE_SKIPPED,
            ContextCompactionJobStatus.FAILED,
            ContextCompactionJobStatus.PENDING,
            ContextCompactionJobStatus.RUNNING,
            -> Unit
        }

        persistState()
    }

    override suspend fun latestArtifact(agentId: String): ContextCompactionArtifact? = mutex.withLock {
        ensureLoaded()
        state.latestArtifactByAgent[agentId]
    }

    override suspend fun latestVersion(agentId: String): Long? = mutex.withLock {
        ensureLoaded()
        state.latestVersionByAgent[agentId]
    }

    override suspend fun latestFingerprint(agentId: String): String? = mutex.withLock {
        ensureLoaded()
        state.latestFingerprintByAgent[agentId]
    }

    private suspend fun ensureLoaded() {
        if (stateLoaded) return
        val checkpoint = persistenceStorageProvider.getLatestCheckpoint(stateAgentId)
        val root = checkpoint?.properties?.get(STATE_PROPERTY_KEY)?.jsonObject
        state = root?.let(CompactionStoreStateCodec::decodeState) ?: CompactionStoreState()
        stateLoaded = true
    }

    private suspend fun persistState() {
        val latest = persistenceStorageProvider.getLatestCheckpoint(stateAgentId)
        val nextVersion = (latest?.version ?: -1L) + 1L
        val checkpoint = AgentCheckpointData(
            checkpointId = UUID.randomUUID().toString(),
            createdAt = Clock.System.now(),
            nodePath = checkpointNodePath,
            lastInput = JsonNull,
            messageHistory = emptyList<Message>(),
            version = nextVersion,
            properties = mapOf(STATE_PROPERTY_KEY to CompactionStoreStateCodec.encodeState(state)),
        )
        persistenceStorageProvider.saveCheckpoint(stateAgentId, checkpoint)
    }

    private companion object {
        const val STATE_PROPERTY_KEY = "koog.context.compaction.store_state.v1"
    }
}
