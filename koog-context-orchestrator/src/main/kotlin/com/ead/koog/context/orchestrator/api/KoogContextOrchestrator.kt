package com.ead.koog.context.orchestrator.api

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import com.ead.koog.context.orchestrator.policy.AdaptiveContextBudgetManager
import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry

/**
 * KOOG-native context manager with explicit planning and execution phases.
 *
 * Planning happens once per turn. Compression can be executed immediately or deferred to end of turn.
 */
class KoogContextOrchestrator(
    private val config: ContextManagementConfig,
) {
    private val budgetManager = AdaptiveContextBudgetManager(config)

    private var turnCounter: Int = 0
    private var activeTurnId: Int? = null
    private var plannedCompressionForTurn: CompressionPlan? = null
    private var appliedCompressionsForTurn: Int = 0
    private var pendingEndTurnPlan: CompressionPlan? = null

    private var lastCompressionTurn: Int? = null
    private var compressionCount: Int = 0
    private var lastCompressionMode: CompressionMode? = null
    private var lastContinuityPacket: ContinuityPacket? = null

    private var lastTelemetry: ContextTelemetry = emptyTelemetry()

    suspend fun beginTurn(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): CompressionPlan {
        if (activeTurnId == null) {
            activeTurnId = ++turnCounter
            appliedCompressionsForTurn = 0
            plannedCompressionForTurn = null
            pendingEndTurnPlan = null
        }

        val existingPlan = plannedCompressionForTurn
        if (existingPlan != null) return existingPlan

        val telemetry = collectTelemetry(context)
        val plan = budgetManager.decidePlan(
            messageCount = context.llm.readSession { prompt.messages.size },
            turnsSinceLastCompression = turnsSinceLastCompression(),
            zone = telemetry.riskZone,
            hints = hints,
        )
        plannedCompressionForTurn = plan
        if (plan.shouldCompress && plan.timing == CompressionTiming.END_OF_TURN) {
            pendingEndTurnPlan = plan
        }
        return plan
    }

    suspend fun beforeLlmCall(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        val plan = beginTurn(context, hints)
        if (plan.shouldCompress && plan.timing == CompressionTiming.BEFORE_NEXT_LLM) {
            applyCompressionIfNeeded(
                context = context,
                plan = plan,
                hints = hints,
                stage = ContextLifecycleStage.BEFORE_LLM,
            )
        }

        val updatedTelemetry = collectTelemetry(context)
        return ContextDecision(
            mode = plan.mode,
            timing = plan.timing,
            reason = plan.reason,
            telemetry = updatedTelemetry,
            stage = ContextLifecycleStage.BEFORE_LLM,
        )
    }

    suspend fun afterLlmCall(context: AIAgentGraphContextBase): ContextTelemetry = collectTelemetry(context)

    suspend fun beforeToolLoop(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        val plan = beginTurn(context, hints)
        if (plan.shouldCompress && plan.timing == CompressionTiming.BEFORE_NEXT_LLM) {
            applyCompressionIfNeeded(
                context = context,
                plan = plan,
                hints = hints,
                stage = ContextLifecycleStage.BEFORE_TOOL_LOOP,
            )
        }
        val updatedTelemetry = collectTelemetry(context)
        return ContextDecision(
            mode = plan.mode,
            timing = plan.timing,
            reason = plan.reason,
            telemetry = updatedTelemetry,
            stage = ContextLifecycleStage.BEFORE_TOOL_LOOP,
        )
    }

    suspend fun afterToolLoop(context: AIAgentGraphContextBase): ContextTelemetry = collectTelemetry(context)

    suspend fun endTurn(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        val plan = pendingEndTurnPlan
        if (plan != null && plan.shouldCompress) {
            applyCompressionIfNeeded(
                context = context,
                plan = plan,
                hints = hints,
                stage = ContextLifecycleStage.END_TURN,
            )
        }
        val telemetry = collectTelemetry(context)
        val decision = ContextDecision(
            mode = plan?.mode ?: CompressionMode.NONE,
            timing = plan?.timing ?: CompressionTiming.MANUAL,
            reason = plan?.reason ?: "No deferred compression for this turn.",
            telemetry = telemetry,
            stage = ContextLifecycleStage.END_TURN,
        )
        activeTurnId = null
        plannedCompressionForTurn = null
        pendingEndTurnPlan = null
        appliedCompressionsForTurn = 0
        return decision
    }

    suspend fun forceCompaction(
        context: AIAgentGraphContextBase,
        reason: String,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        val plan = CompressionPlan(
            mode = CompressionMode.EMERGENCY,
            timing = CompressionTiming.BEFORE_NEXT_LLM,
            reason = reason,
            riskZone = lastTelemetry.riskZone,
            requiresLlmRoundtrip = true,
        )
        applyCompressionIfNeeded(
            context = context,
            plan = plan,
            hints = hints,
            stage = ContextLifecycleStage.BEFORE_LLM,
            allowBypassPerTurnLimit = true,
        )
        val telemetry = collectTelemetry(context)
        return ContextDecision(
            mode = CompressionMode.EMERGENCY,
            timing = CompressionTiming.BEFORE_NEXT_LLM,
            reason = reason,
            telemetry = telemetry,
            stage = ContextLifecycleStage.BEFORE_LLM,
        )
    }

    fun snapshot(): ContextSnapshot = ContextSnapshot(
        telemetry = lastTelemetry,
        continuityPacket = lastContinuityPacket,
    )

    fun snapshotTelemetry(): ContextTelemetry = lastTelemetry

    private suspend fun applyCompressionIfNeeded(
        context: AIAgentGraphContextBase,
        plan: CompressionPlan,
        hints: ContextHints,
        stage: ContextLifecycleStage,
        allowBypassPerTurnLimit: Boolean = false,
    ) {
        if (plan.mode == CompressionMode.NONE) return
        if (!allowBypassPerTurnLimit && appliedCompressionsForTurn >= config.maxCompressionsPerTurn) return
        if (stage == ContextLifecycleStage.BEFORE_TOOL_LOOP && plan.timing == CompressionTiming.END_OF_TURN) return

        compress(context, plan.mode, hints)
        appliedCompressionsForTurn += 1
        if (plan.timing == CompressionTiming.END_OF_TURN) {
            pendingEndTurnPlan = null
        }
    }

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
        val latestModelUsage = context.llm.readSession { prompt.latestTokenUsage }
        val usageKnown = latestModelUsage > 0
        val estimatedPromptTokens = latestModelUsage.coerceAtLeast(0)

        if (config.requireModelTokenUsage && !usageKnown) {
            val telemetry = budgetManager.telemetry(
                estimatedPromptTokens = 0,
                tokenUsageKnown = false,
                compressionCount = compressionCount,
                turnsSinceLastCompression = turnsSinceLastCompression(),
                lastCompressionMode = lastCompressionMode,
                continuityIntegrityScore = lastContinuityPacket?.integrityScore() ?: 0,
            )
            lastTelemetry = telemetry
            return telemetry
        }

        budgetManager.observe(estimatedPromptTokens)

        val telemetry = budgetManager.telemetry(
            estimatedPromptTokens = estimatedPromptTokens,
            tokenUsageKnown = usageKnown,
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
        tokenUsageKnown = false,
        compressionCount = 0,
        turnsSinceLastCompression = null,
        lastCompressionMode = null,
        continuityIntegrityScore = 0,
    )
}
