package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.policy.CompressionMode

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
    val preserveMemory: Boolean = true,
    val lightFromLastNMessages: Int = 24,
    val structuredChunkSize: Int = 12,
    val continuityMaxItemsPerSection: Int = 6,
    val enableFactFocusedCompression: Boolean = true,
    val requireUnresolvedCommitmentsForWarningFactFocused: Boolean = true,
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
    val maxCompressionsPerTurn: Int = 1,
    val requireModelTokenUsage: Boolean = true,
) {
    init {
        require(maxContextTokens > 0) { "maxContextTokens must be > 0." }
        require(lightFromLastNMessages > 0) { "lightFromLastNMessages must be > 0." }
        require(structuredChunkSize > 0) { "structuredChunkSize must be > 0." }
        require(maxCompressionsPerTurn > 0) { "maxCompressionsPerTurn must be > 0." }
    }
}
