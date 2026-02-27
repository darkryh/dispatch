package com.ead.koog.context.orchestrator.api

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import com.ead.koog.context.orchestrator.async.ContextCompactionJob
import com.ead.koog.context.orchestrator.async.ContextCompactionWorkerRegistry
import com.ead.koog.context.orchestrator.policy.AdaptiveContextBudgetManager
import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry

/**
 * KOOG-native context manager with explicit planning and async compaction scheduling.
 */
class KoogContextOrchestrator(
    private val config: ContextManagementConfig,
) {
    private val budgetManager = AdaptiveContextBudgetManager(config)

    private var turnCounter: Int = 0
    private var activeTurnId: Int? = null
    private var plannedCompressionForTurn: CompressionPlan? = null
    private var scheduledCompressionForTurn: Boolean = false
    private var pendingEndTurnPlan: CompressionPlan? = null

    private var lastCompactionTurn: Int? = null
    private var compactionCount: Int = 0
    private var lastCompactionMode: CompressionMode? = null
    private var lastContinuityPacket: ContinuityPacket? = null

    private var lastAppliedArtifactId: String? = null
    private var lastAppliedArtifactVersion: Long? = null
    private var lastTelemetry: ContextTelemetry = emptyTelemetry()

    init {
        ContextCompactionWorkerRegistry.ensureStarted(
            store = config.compactionStore,
            backend = config.compactorBackend,
            key = config.workerRegistryKey ?: buildString {
                append(config.compactionStore::class.qualifiedName)
                append("@")
                append(System.identityHashCode(config.compactionStore))
                append(":")
                append(config.compactorBackend::class.qualifiedName)
                append("@")
                append(System.identityHashCode(config.compactorBackend))
            },
            workerId = "context-compactor-default",
        )
    }

    suspend fun beginTurn(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): CompressionPlan {
        if (activeTurnId == null) {
            activeTurnId = ++turnCounter
            plannedCompressionForTurn = null
            pendingEndTurnPlan = null
            scheduledCompressionForTurn = false
        }

        val existingPlan = plannedCompressionForTurn
        if (existingPlan != null) return existingPlan

        val telemetry = collectTelemetry(context)
        val plan = budgetManager.decidePlan(
            messageCount = context.llm.readSession { prompt.messages.size },
            turnsSinceLastCompression = turnsSinceLastCompaction(),
            zone = telemetry.riskZone,
            hints = hints,
        )

        plannedCompressionForTurn = plan
        if (plan.shouldCompress && plan.timing == CompressionTiming.END_OF_TURN) {
            pendingEndTurnPlan = plan
        }
        return plan
    }

    suspend fun applyLatestCompactedContext(context: AIAgentGraphContextBase): Boolean {
        val artifact = config.compactionStore.latestArtifact(context.agentId) ?: return false
        if (artifact.id == lastAppliedArtifactId) return false

        context.llm.writeSession {
            appendPrompt {
                system(
                    buildString {
                        appendLine("[COMPACTED MEMORY ARTIFACT]")
                        appendLine("mode=${artifact.mode.name}")
                        appendLine("source_version=${artifact.sourceVersion}")
                        appendLine(artifact.text)
                    }.trim(),
                )
            }
        }

        lastAppliedArtifactId = artifact.id
        lastAppliedArtifactVersion = artifact.resultVersion
        return true
    }

    suspend fun beforeLlmCall(
        context: AIAgentGraphContextBase,
        hints: ContextHints = ContextHints(),
    ): ContextDecision {
        val plan = beginTurn(context, hints)
        if (plan.shouldCompress && plan.timing == CompressionTiming.BEFORE_NEXT_LLM) {
            scheduleCompactionIfNeeded(context, plan, hints)
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
            scheduleCompactionIfNeeded(context, plan, hints)
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
        scheduledCompressionForTurn = false
        return decision
    }

    fun snapshot(): ContextSnapshot = ContextSnapshot(
        telemetry = lastTelemetry,
        continuityPacket = lastContinuityPacket,
        latestAppliedArtifactId = lastAppliedArtifactId,
        latestAppliedArtifactVersion = lastAppliedArtifactVersion,
    )

    fun snapshotTelemetry(): ContextTelemetry = lastTelemetry

    private suspend fun scheduleCompactionIfNeeded(
        context: AIAgentGraphContextBase,
        plan: CompressionPlan,
        hints: ContextHints,
    ) {
        if (scheduledCompressionForTurn) return
        if (plan.mode == CompressionMode.NONE) return

        val promptMessages = context.llm.readSession {
            prompt.messages.map { message -> message.toString() }
        }
        val sourceVersion = context.llm.readSession { prompt.messages.size.toLong() }
        val sourceFingerprint = promptMessages.joinToString(separator = "\n").hashCode().toString()
        val enqueued = config.compactionStore.enqueue(
            ContextCompactionJob(
                agentId = context.agentId,
                sourceVersion = sourceVersion,
                sourceFingerprint = sourceFingerprint,
                mode = plan.mode,
                riskZone = plan.riskZone,
                hints = hints,
                promptMessages = promptMessages,
            ),
        )
        if (enqueued) {
            scheduledCompressionForTurn = true
            compactionCount += 1
            lastCompactionTurn = turnCounter
            lastCompactionMode = plan.mode
            lastContinuityPacket = hints.continuityPacket?.takeIf { it.isMeaningful() }
        }
    }

    private suspend fun collectTelemetry(context: AIAgentGraphContextBase): ContextTelemetry {
        val latestModelUsage = context.llm.readSession { prompt.latestTokenUsage }
        val usageKnown = latestModelUsage > 0
        val estimatedPromptTokens = latestModelUsage.coerceAtLeast(0)

        if (config.requireModelTokenUsage && !usageKnown) {
            val telemetry = budgetManager.telemetry(
                estimatedPromptTokens = 0,
                tokenUsageKnown = false,
                compactionCount = compactionCount,
                turnsSinceLastCompaction = turnsSinceLastCompaction(),
                lastCompactionMode = lastCompactionMode,
                continuityIntegrityScore = lastContinuityPacket?.integrityScore() ?: 0,
            )
            lastTelemetry = telemetry
            return telemetry
        }

        budgetManager.observe(estimatedPromptTokens)

        val telemetry = budgetManager.telemetry(
            estimatedPromptTokens = estimatedPromptTokens,
            tokenUsageKnown = usageKnown,
            compactionCount = compactionCount,
            turnsSinceLastCompaction = turnsSinceLastCompaction(),
            lastCompactionMode = lastCompactionMode,
            continuityIntegrityScore = lastContinuityPacket?.integrityScore() ?: 0,
        )

        lastTelemetry = telemetry
        return telemetry
    }

    private fun turnsSinceLastCompaction(): Int? {
        val lastTurn = lastCompactionTurn ?: return null
        return (turnCounter - lastTurn).coerceAtLeast(0)
    }

    private fun emptyTelemetry(): ContextTelemetry = budgetManager.telemetry(
        estimatedPromptTokens = 0,
        tokenUsageKnown = false,
        compactionCount = 0,
        turnsSinceLastCompaction = null,
        lastCompactionMode = null,
        continuityIntegrityScore = 0,
    )
}
