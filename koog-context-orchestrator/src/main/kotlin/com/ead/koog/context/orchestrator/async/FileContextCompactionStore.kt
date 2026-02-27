package com.ead.koog.context.orchestrator.async

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * File-backed compaction store.
 *
 * This store persists queue/running/latest state so compaction jobs and artifacts can survive process restarts.
 */
class FileContextCompactionStore(
    private val filePath: Path,
    private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true },
) : ContextCompactionStore {
    private val mutex = Mutex()
    private var state: CompactionStoreState = CompactionStoreState()

    init {
        loadFromDisk()
    }

    override suspend fun enqueue(job: ContextCompactionJob): Boolean = mutex.withLock {
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
        persistToDisk()
        true
    }

    override suspend fun claimNext(workerId: String): ContextCompactionJob? = mutex.withLock {
        val job = state.queue.removeFirstOrNull() ?: return null
        state.running[job.id] = job
        persistToDisk()
        job
    }

    override suspend fun complete(jobId: String, result: ContextCompactionResult): Unit = mutex.withLock {
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

        persistToDisk()
    }

    override suspend fun latestArtifact(agentId: String): ContextCompactionArtifact? = mutex.withLock {
        state.latestArtifactByAgent[agentId]
    }

    override suspend fun latestVersion(agentId: String): Long? = mutex.withLock {
        state.latestVersionByAgent[agentId]
    }

    override suspend fun latestFingerprint(agentId: String): String? = mutex.withLock {
        state.latestFingerprintByAgent[agentId]
    }

    private fun loadFromDisk() {
        if (!filePath.exists()) return
        val raw = runCatching { filePath.readText() }.getOrNull() ?: return
        if (raw.isBlank()) return
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        state = CompactionStoreStateCodec.decodeState(root)
    }

    private fun persistToDisk() {
        runCatching {
            filePath.parent?.createDirectories()
            if (!Files.exists(filePath)) {
                Files.createFile(filePath)
            }

            val root = CompactionStoreStateCodec.encodeState(state)
            filePath.writeText(json.encodeToString(JsonObject.serializer(), root))
        }
    }
}
