package com.ead.koog.context.orchestrator.api

/**
 * Generic configuration for adaptive context management.
 */
data class ContextManagementConfig(
    val maxContextTokens: Int,
    val watchAtUsedPercent: Double = 40.0,
    val warningAtUsedPercent: Double = 55.0,
    val criticalAtUsedPercent: Double = 70.0,
    val emergencyAtUsedPercent: Double = 85.0,
    val minMessagesForCompression: Int = 16,
    val compressionCooldownTurns: Int = 1,
    val highGrowthTokensPerTurn: Int = 1_200,
    val preserveMemory: Boolean = true,
    val lightFromLastNMessages: Int = 24,
    val structuredChunkSize: Int = 12,
    val continuityMaxItemsPerSection: Int = 6,
    val enableFactFocusedCompression: Boolean = true,
) {
    init {
        require(maxContextTokens > 0) { "maxContextTokens must be > 0." }
        require(lightFromLastNMessages > 0) { "lightFromLastNMessages must be > 0." }
        require(structuredChunkSize > 0) { "structuredChunkSize must be > 0." }
    }
}
