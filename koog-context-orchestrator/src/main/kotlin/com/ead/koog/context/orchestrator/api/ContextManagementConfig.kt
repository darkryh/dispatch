package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.async.ContextCompactionStore
import com.ead.koog.context.orchestrator.async.ContextCompactorBackend
import com.ead.koog.context.orchestrator.async.DeterministicContextCompactorBackend
import com.ead.koog.context.orchestrator.async.InMemoryContextCompactionStore

/**
 * Generic configuration for adaptive context management.
 */
data class ContextManagementConfig(
    val maxContextTokens: Int,
    val watchAtUsedPercent: Double = 75.0,
    val warningAtUsedPercent: Double = 79.0,
    val criticalAtUsedPercent: Double = 80.0,
    val emergencyAtUsedPercent: Double = 85.0,
    val minMessagesForCompression: Int = 16,
    val compressionCooldownTurns: Int = 2,
    val continuityMaxItemsPerSection: Int = 6,
    val watchPolicy: ZoneCompressionPolicy = ZoneCompressionPolicy(
        mode = CompressionMode.LIGHT,
        timing = CompressionTiming.END_OF_TURN,
        minRecentToolCalls = 2,
    ),
    val warningPolicy: ZoneCompressionPolicy = ZoneCompressionPolicy(
        mode = CompressionMode.STRUCTURED,
        timing = CompressionTiming.END_OF_TURN,
    ),
    val criticalPolicy: ZoneCompressionPolicy = ZoneCompressionPolicy(
        mode = CompressionMode.AGGRESSIVE,
        timing = CompressionTiming.BEFORE_NEXT_LLM,
    ),
    val emergencyPolicy: ZoneCompressionPolicy = ZoneCompressionPolicy(
        mode = CompressionMode.EMERGENCY,
        timing = CompressionTiming.BEFORE_NEXT_LLM,
    ),
    val requireModelTokenUsage: Boolean = true,
    val compactionStore: ContextCompactionStore = InMemoryContextCompactionStore(),
    val compactorBackend: ContextCompactorBackend = DeterministicContextCompactorBackend(),
    val workerRegistryKey: String? = null,
) {
    init {
        require(maxContextTokens > 0) { "maxContextTokens must be > 0." }
    }
}
