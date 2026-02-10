package com.ead.koog.context.orchestrator.policy

import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.ContextManagementConfig
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry
import kotlin.math.roundToInt

class AdaptiveContextBudgetManager(
    private val config: ContextManagementConfig,
) {
    private val observedPromptTokenTotals = ArrayDeque<Int>()

    fun observe(totalPromptTokens: Int) {
        observedPromptTokenTotals.addLast(totalPromptTokens)
        if (observedPromptTokenTotals.size > 8) {
            observedPromptTokenTotals.removeFirst()
        }
    }

    fun growthTokensPerTurn(): Int {
        if (observedPromptTokenTotals.size < 2) return 0
        val first = observedPromptTokenTotals.first()
        val last = observedPromptTokenTotals.last()
        val steps = (observedPromptTokenTotals.size - 1).coerceAtLeast(1)
        return (last - first) / steps
    }

    fun riskZone(usedPercent: Double): ContextRiskZone = when {
        usedPercent >= config.emergencyAtUsedPercent -> ContextRiskZone.EMERGENCY
        usedPercent >= config.criticalAtUsedPercent -> ContextRiskZone.CRITICAL
        usedPercent >= config.warningAtUsedPercent -> ContextRiskZone.WARNING
        usedPercent >= config.watchAtUsedPercent -> ContextRiskZone.WATCH
        else -> ContextRiskZone.HEALTHY
    }

    fun decideMode(
        messageCount: Int,
        turnsSinceLastCompression: Int?,
        zone: ContextRiskZone,
        hints: ContextHints,
    ): CompressionMode {
        if (messageCount < config.minMessagesForCompression) return CompressionMode.NONE

        val withinCooldown =
            turnsSinceLastCompression != null &&
                turnsSinceLastCompression <= config.compressionCooldownTurns

        val growth = growthTokensPerTurn()

        if (zone == ContextRiskZone.EMERGENCY) return CompressionMode.EMERGENCY

        if (withinCooldown && zone <= ContextRiskZone.WARNING) {
            return CompressionMode.NONE
        }

        return when (zone) {
            ContextRiskZone.CRITICAL -> {
                if (config.enableFactFocusedCompression && hints.factConcepts.isNotEmpty()) {
                    CompressionMode.FACT_FOCUSED
                } else {
                    CompressionMode.AGGRESSIVE
                }
            }

            ContextRiskZone.WARNING -> {
                if (config.enableFactFocusedCompression && hints.factConcepts.isNotEmpty() && hints.unresolvedCommitments > 0) {
                    CompressionMode.FACT_FOCUSED
                } else {
                    CompressionMode.STRUCTURED
                }
            }

            ContextRiskZone.WATCH -> {
                if (growth >= config.highGrowthTokensPerTurn || hints.recentToolCalls > 0) {
                    CompressionMode.LIGHT
                } else {
                    CompressionMode.NONE
                }
            }

            else -> CompressionMode.NONE
        }
    }

    fun telemetry(
        estimatedPromptTokens: Int,
        compressionCount: Int,
        turnsSinceLastCompression: Int?,
        lastCompressionMode: CompressionMode?,
        continuityIntegrityScore: Int,
    ): ContextTelemetry {
        val safeEstimated = estimatedPromptTokens.coerceAtLeast(0)
        val usedPercent = ((safeEstimated.toDouble() / config.maxContextTokens) * 100.0)
            .coerceIn(0.0, 100.0)
        val remainingTokens = (config.maxContextTokens - safeEstimated).coerceAtLeast(0)
        val remainingPercent = (100.0 - usedPercent).coerceIn(0.0, 100.0)

        return ContextTelemetry(
            estimatedPromptTokens = safeEstimated,
            maxContextTokens = config.maxContextTokens,
            usedPercent = (usedPercent * 100.0).roundToInt() / 100.0,
            remainingTokens = remainingTokens,
            remainingPercent = (remainingPercent * 100.0).roundToInt() / 100.0,
            riskZone = riskZone(usedPercent),
            compressionCount = compressionCount,
            turnsSinceLastCompression = turnsSinceLastCompression,
            lastCompressionMode = lastCompressionMode,
            growthTokensPerTurn = growthTokensPerTurn(),
            continuityIntegrityScore = continuityIntegrityScore.coerceIn(0, 100),
        )
    }
}
