package com.ead.koog.context.orchestrator.api

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import com.ead.koog.context.orchestrator.compression.HeuristicTokenEstimator
import com.ead.koog.context.orchestrator.compression.TokenEstimator
import com.ead.koog.context.orchestrator.policy.AdaptiveContextBudgetManager
import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry

/**
 * KOOG-native context manager that applies adaptive compression and continuity rehydration.
 */
class KoogContextOrchestrator(
    private val config: ContextManagementConfig,
    private val tokenEstimator: TokenEstimator = HeuristicTokenEstimator(),
) {
    private val budgetManager = AdaptiveContextBudgetManager(config)

    private var turnCounter: Int = 0
    private var lastCompressionTurn: Int? = null
    private var compressionCount: Int = 0
    private var lastCompressionMode: CompressionMode? = null
    private var lastContinuityPacket: ContinuityPacket? = null

    private var lastTelemetry: ContextTelemetry = emptyTelemetry()

    suspend fun beforeLlmCall(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        turnCounter += 1
        val initialTelemetry = collectTelemetry(context)

        val mode = budgetManager.decideMode(
            messageCount = context.llm.readSession { prompt.messages.size },
            turnsSinceLastCompression = turnsSinceLastCompression(),
            zone = initialTelemetry.riskZone,
            hints = hints,
        )

        if (mode != CompressionMode.NONE) {
            compress(context, mode, hints)
        }

        val updatedTelemetry = collectTelemetry(context)
        val reason = when (mode) {
            CompressionMode.NONE -> "Context is within budget for this turn."
            CompressionMode.LIGHT -> "Applying light compression due to watch-zone growth."
            CompressionMode.STRUCTURED -> "Applying structured compression for warning zone."
            CompressionMode.AGGRESSIVE -> "Applying aggressive compression for critical zone."
            CompressionMode.FACT_FOCUSED -> "Applying fact-focused compression for continuity-sensitive context."
            CompressionMode.EMERGENCY -> "Applying emergency compression close to context limit."
        }

        return ContextDecision(
            mode = mode,
            reason = reason,
            telemetry = updatedTelemetry,
        )
    }

    suspend fun afterLlmCall(context: AIAgentGraphContextBase): ContextTelemetry = collectTelemetry(context)

    suspend fun beforeToolLoop(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): ContextDecision = beforeLlmCall(context, hints)

    suspend fun afterToolLoop(context: AIAgentGraphContextBase): ContextTelemetry = collectTelemetry(context)

    suspend fun forceCompaction(
        context: AIAgentGraphContextBase,
        reason: String,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        compress(context, CompressionMode.EMERGENCY, hints)
        val telemetry = collectTelemetry(context)
        return ContextDecision(
            mode = CompressionMode.EMERGENCY,
            reason = reason,
            telemetry = telemetry,
        )
    }

    fun snapshot(): ContextSnapshot = ContextSnapshot(
        telemetry = lastTelemetry,
        continuityPacket = lastContinuityPacket,
    )

    fun snapshotTelemetry(): ContextTelemetry = lastTelemetry

    private suspend fun compress(
        context: AIAgentGraphContextBase,
        mode: CompressionMode,
        hints: ContextHints,
    ) {
        val continuity = resolveContinuityPacket(hints)

        context.llm.writeSession {
            when (mode) {
                CompressionMode.LIGHT -> {
                    replaceHistoryWithTLDR(
                        strategy = HistoryCompressionStrategy.FromLastNMessages(config.lightFromLastNMessages),
                        preserveMemory = config.preserveMemory,
                    )
                }

                CompressionMode.STRUCTURED -> {
                    replaceHistoryWithTLDR(
                        strategy = HistoryCompressionStrategy.Chunked(config.structuredChunkSize),
                        preserveMemory = config.preserveMemory,
                    )
                }

                CompressionMode.AGGRESSIVE,
                CompressionMode.EMERGENCY -> {
                    replaceHistoryWithTLDR(
                        strategy = HistoryCompressionStrategy.WholeHistory,
                        preserveMemory = config.preserveMemory,
                    )
                }

                CompressionMode.FACT_FOCUSED -> {
                    val concepts = hints.factConcepts
                    if (concepts.isNotEmpty()) {
                        replaceHistoryWithTLDR(
                            strategy = RetrieveFactsFromHistory(concepts),
                            preserveMemory = config.preserveMemory,
                        )
                    } else {
                        replaceHistoryWithTLDR(
                            strategy = HistoryCompressionStrategy.WholeHistory,
                            preserveMemory = config.preserveMemory,
                        )
                    }
                }

                CompressionMode.NONE -> Unit
            }

            if (continuity?.isMeaningful() == true) {
                appendPrompt {
                    system(continuity.toSystemMessage(config))
                }
            }
        }

        compressionCount += 1
        lastCompressionTurn = turnCounter
        lastCompressionMode = mode
        lastContinuityPacket = continuity
    }

    private fun resolveContinuityPacket(hints: ContextHints): ContinuityPacket? {
        return hints.continuityPacket
            ?.takeIf { it.isMeaningful() }
    }

    private suspend fun collectTelemetry(context: AIAgentGraphContextBase): ContextTelemetry {
        val messages = context.llm.readSession { prompt.messages }
        val estimatedByHistory = tokenEstimator.estimate(messages)
        val latestModelUsage = context.llm.readSession { prompt.latestTokenUsage }

        val estimatedPromptTokens = maxOf(estimatedByHistory, latestModelUsage)
        budgetManager.observe(estimatedPromptTokens)

        val telemetry = budgetManager.telemetry(
            estimatedPromptTokens = estimatedPromptTokens,
            compressionCount = compressionCount,
            turnsSinceLastCompression = turnsSinceLastCompression(),
            lastCompressionMode = lastCompressionMode,
            continuityIntegrityScore = lastContinuityPacket?.integrityScore() ?: 0,
        )

        lastTelemetry = telemetry
        return telemetry
    }

    private fun turnsSinceLastCompression(): Int? {
        val lastTurn = lastCompressionTurn ?: return null
        return (turnCounter - lastTurn).coerceAtLeast(0)
    }

    private fun emptyTelemetry(): ContextTelemetry = budgetManager.telemetry(
        estimatedPromptTokens = 0,
        compressionCount = 0,
        turnsSinceLastCompression = null,
        lastCompressionMode = null,
        continuityIntegrityScore = 0,
    )
}
