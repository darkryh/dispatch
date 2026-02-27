package com.ead.koog.context.orchestrator.async

import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.policy.ContextRiskZone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class ContextCompactionJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    STALE_SKIPPED,
    FAILED,
}

data class ContextCompactionJob(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val sourceVersion: Long,
    val sourceFingerprint: String,
    val mode: CompressionMode,
    val riskZone: ContextRiskZone,
    val hints: ContextHints,
    val promptMessages: List<String>,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class ContextCompactionArtifact(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val sourceVersion: Long,
    val sourceFingerprint: String,
    val resultVersion: Long,
    val mode: CompressionMode,
    val text: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class ContextCompactionResult(
    val status: ContextCompactionJobStatus,
    val artifact: ContextCompactionArtifact? = null,
    val error: String? = null,
)

interface ContextCompactionStore {
    suspend fun enqueue(job: ContextCompactionJob): Boolean

    suspend fun claimNext(workerId: String): ContextCompactionJob?

    suspend fun complete(jobId: String, result: ContextCompactionResult)

    suspend fun latestArtifact(agentId: String): ContextCompactionArtifact?

    suspend fun latestVersion(agentId: String): Long?

    suspend fun latestFingerprint(agentId: String): String?
}

class InMemoryContextCompactionStore : ContextCompactionStore {
    private val mutex = Mutex()
    private val queue = ArrayDeque<ContextCompactionJob>()
    private val running = linkedMapOf<String, ContextCompactionJob>()
    private val latestArtifactByAgent = linkedMapOf<String, ContextCompactionArtifact>()
    private val latestVersionByAgent = linkedMapOf<String, Long>()
    private val latestFingerprintByAgent = linkedMapOf<String, String>()

    override suspend fun enqueue(job: ContextCompactionJob): Boolean = mutex.withLock {
        val latest = latestVersionByAgent[job.agentId] ?: 0L
        if (job.sourceVersion < latest) return false
        val duplicate = queue.any {
            it.agentId == job.agentId &&
                it.sourceVersion == job.sourceVersion &&
                it.sourceFingerprint == job.sourceFingerprint &&
                it.mode == job.mode
        }
        val duplicateRunning = running.values.any {
            it.agentId == job.agentId &&
                it.sourceVersion == job.sourceVersion &&
                it.sourceFingerprint == job.sourceFingerprint &&
                it.mode == job.mode
        }
        if (duplicate || duplicateRunning) return false
        queue.addLast(job)
        true
    }

    override suspend fun claimNext(workerId: String): ContextCompactionJob? = mutex.withLock {
        val job = queue.removeFirstOrNull() ?: return null
        running[job.id] = job
        job
    }

    override suspend fun complete(jobId: String, result: ContextCompactionResult): Unit = mutex.withLock {
        val job = running.remove(jobId) ?: return
        val latest = latestVersionByAgent[job.agentId] ?: 0L
        when (result.status) {
            ContextCompactionJobStatus.SUCCEEDED -> {
                val artifact = result.artifact ?: return
                if (artifact.resultVersion > latest ||
                    (artifact.resultVersion == latest && latestFingerprintByAgent[job.agentId] != artifact.sourceFingerprint)
                ) {
                    latestVersionByAgent[job.agentId] = artifact.resultVersion
                    latestFingerprintByAgent[job.agentId] = artifact.sourceFingerprint
                    latestArtifactByAgent[job.agentId] = artifact
                }
            }

            ContextCompactionJobStatus.STALE_SKIPPED -> Unit
            ContextCompactionJobStatus.FAILED -> Unit
            ContextCompactionJobStatus.PENDING,
            ContextCompactionJobStatus.RUNNING,
            -> Unit
        }
    }

    override suspend fun latestArtifact(agentId: String): ContextCompactionArtifact? = mutex.withLock {
        latestArtifactByAgent[agentId]
    }

    override suspend fun latestVersion(agentId: String): Long? = mutex.withLock {
        latestVersionByAgent[agentId]
    }

    override suspend fun latestFingerprint(agentId: String): String? = mutex.withLock {
        latestFingerprintByAgent[agentId]
    }
}
