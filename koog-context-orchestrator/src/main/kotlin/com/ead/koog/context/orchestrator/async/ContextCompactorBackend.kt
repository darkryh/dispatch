package com.ead.koog.context.orchestrator.async

import com.ead.koog.context.orchestrator.policy.CompressionMode

interface ContextCompactorBackend {
    suspend fun compact(job: ContextCompactionJob): ContextCompactionResult
}

object NoOpContextCompactorBackend : ContextCompactorBackend {
    override suspend fun compact(job: ContextCompactionJob): ContextCompactionResult {
        return ContextCompactionResult(
            status = ContextCompactionJobStatus.STALE_SKIPPED,
            error = "No compactor backend configured.",
        )
    }
}

class DeterministicContextCompactorBackend(
    private val maxMessages: Int = 24,
    private val maxCharsPerMessage: Int = 320,
) : ContextCompactorBackend {
    override suspend fun compact(job: ContextCompactionJob): ContextCompactionResult {
        val compacted = when (job.mode) {
            CompressionMode.LIGHT -> compactLast(job, count = maxMessages)
            CompressionMode.STRUCTURED -> compactLast(job, count = (maxMessages / 2).coerceAtLeast(1))
            CompressionMode.AGGRESSIVE,
            CompressionMode.EMERGENCY,
            -> compactLast(job, count = (maxMessages / 3).coerceAtLeast(1))
            CompressionMode.FACT_FOCUSED -> compactLast(job, count = (maxMessages / 2).coerceAtLeast(1))
            CompressionMode.NONE -> ""
        }

        if (compacted.isBlank()) {
            return ContextCompactionResult(
                status = ContextCompactionJobStatus.STALE_SKIPPED,
                error = "Compaction produced empty output.",
            )
        }

        return ContextCompactionResult(
            status = ContextCompactionJobStatus.SUCCEEDED,
            artifact = ContextCompactionArtifact(
                agentId = job.agentId,
                sourceVersion = job.sourceVersion,
                sourceFingerprint = job.sourceFingerprint,
                resultVersion = job.sourceVersion,
                mode = job.mode,
                text = compacted,
            ),
        )
    }

    private fun compactLast(job: ContextCompactionJob, count: Int): String {
        val selected = job.promptMessages.takeLast(count)
        if (selected.isEmpty()) return ""
        val objective = job.hints.continuityPacket?.objective
        val constraints = job.hints.continuityPacket?.constraints.orEmpty().take(6)
        return buildString {
            appendLine("[COMPACTED CONTEXT]")
            appendLine("mode=${job.mode.name}")
            appendLine("risk_zone=${job.riskZone.name}")
            if (!objective.isNullOrBlank()) {
                appendLine("objective=$objective")
            }
            if (constraints.isNotEmpty()) {
                appendLine("constraints:")
                constraints.forEach { appendLine("- $it") }
            }
            appendLine("recent_messages:")
            selected.forEach { message ->
                val normalized = message.replace("\n", " ").trim()
                if (normalized.isNotBlank()) {
                    appendLine("- ${normalized.take(maxCharsPerMessage)}")
                }
            }
        }.trim()
    }
}
