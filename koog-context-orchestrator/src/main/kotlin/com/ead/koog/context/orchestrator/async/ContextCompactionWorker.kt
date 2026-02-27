package com.ead.koog.context.orchestrator.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ContextCompactionWorker(
    private val store: ContextCompactionStore,
    private val backend: ContextCompactorBackend,
    private val workerId: String,
    private val pollingDelayMillis: Long = 50L,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val job = store.claimNext(workerId)
            if (job == null) {
                delay(pollingDelayMillis)
                continue
            }

            val latest = store.latestVersion(job.agentId) ?: 0L
            val latestFingerprint = store.latestFingerprint(job.agentId)
            if (job.sourceVersion < latest) {
                store.complete(
                    jobId = job.id,
                    result = ContextCompactionResult(
                        status = ContextCompactionJobStatus.STALE_SKIPPED,
                        error = "Stale source version.",
                    ),
                )
                continue
            }
            if (job.sourceVersion == latest && latestFingerprint != null && latestFingerprint == job.sourceFingerprint) {
                store.complete(
                    jobId = job.id,
                    result = ContextCompactionResult(
                        status = ContextCompactionJobStatus.STALE_SKIPPED,
                        error = "Already compacted for same source fingerprint.",
                    ),
                )
                continue
            }

            val result = try {
                backend.compact(job)
            } catch (throwable: Throwable) {
                ContextCompactionResult(
                    status = ContextCompactionJobStatus.FAILED,
                    error = throwable.message ?: throwable::class.simpleName,
                )
            }

            store.complete(job.id, result)
        }
    }
}

object ContextCompactionWorkerRegistry {
    private val lock = Any()
    private val startedKeys = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun ensureStarted(
        store: ContextCompactionStore,
        backend: ContextCompactorBackend,
        key: String,
        workerId: String,
    ) {
        synchronized(lock) {
            if (startedKeys.contains(key)) return
            ContextCompactionWorker(store, backend, workerId).start(scope)
            startedKeys.add(key)
        }
    }
}
