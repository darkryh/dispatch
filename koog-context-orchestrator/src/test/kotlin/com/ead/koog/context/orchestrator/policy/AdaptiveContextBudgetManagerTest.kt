package com.ead.koog.context.orchestrator.policy

import com.ead.koog.context.orchestrator.api.CompressionTiming
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.ContextManagementConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveContextBudgetManagerTest {

    @Test
    fun `default thresholds trigger compression only at configured used percentages`() {
        val config = ContextManagementConfig(maxContextTokens = 1000)
        val manager = AdaptiveContextBudgetManager(config)

        val belowWatch = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(74.0),
            hints = ContextHints(recentToolCalls = 10),
        )
        assertEquals(CompressionMode.NONE, belowWatch.mode)

        val watch = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(75.0),
            hints = ContextHints(recentToolCalls = 2),
        )
        assertEquals(CompressionMode.LIGHT, watch.mode)
        assertEquals(CompressionTiming.END_OF_TURN, watch.timing)

        val warning = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(79.0),
            hints = ContextHints(recentToolCalls = 0),
        )
        assertEquals(CompressionMode.STRUCTURED, warning.mode)
        assertEquals(CompressionTiming.END_OF_TURN, warning.timing)

        val critical = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(80.0),
            hints = ContextHints(recentToolCalls = 0),
        )
        assertEquals(CompressionMode.AGGRESSIVE, critical.mode)
        assertEquals(CompressionTiming.BEFORE_NEXT_LLM, critical.timing)

        val emergency = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(85.0),
            hints = ContextHints(recentToolCalls = 0),
        )
        assertEquals(CompressionMode.EMERGENCY, emergency.mode)
        assertEquals(CompressionTiming.BEFORE_NEXT_LLM, emergency.timing)
    }

    @Test
    fun `compression does not run when message count is below minimum`() {
        val config = ContextManagementConfig(maxContextTokens = 1000)
        val manager = AdaptiveContextBudgetManager(config)

        val plan = manager.decidePlan(
            messageCount = config.minMessagesForCompression - 1,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(95.0),
            hints = ContextHints(recentToolCalls = 100),
        )

        assertEquals(CompressionMode.NONE, plan.mode)
        assertEquals(CompressionTiming.MANUAL, plan.timing)
    }

    @Test
    fun `cooldown blocks watch and warning zones but not critical`() {
        val config = ContextManagementConfig(maxContextTokens = 1000, compressionCooldownTurns = 2)
        val manager = AdaptiveContextBudgetManager(config)

        val watchDuringCooldown = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = 1,
            zone = manager.riskZone(75.0),
            hints = ContextHints(recentToolCalls = 2),
        )
        assertEquals(CompressionMode.NONE, watchDuringCooldown.mode)

        val warningDuringCooldown = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = 1,
            zone = manager.riskZone(79.0),
            hints = ContextHints(recentToolCalls = 5),
        )
        assertEquals(CompressionMode.NONE, warningDuringCooldown.mode)

        val criticalDuringCooldown = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = 1,
            zone = manager.riskZone(80.0),
            hints = ContextHints(recentToolCalls = 0),
        )
        assertEquals(CompressionMode.AGGRESSIVE, criticalDuringCooldown.mode)
        assertEquals(CompressionTiming.BEFORE_NEXT_LLM, criticalDuringCooldown.timing)
    }

    @Test
    fun `threshold and mode are fully configurable`() {
        val config = ContextManagementConfig(
            maxContextTokens = 1000,
            watchAtUsedPercent = 60.0,
            warningAtUsedPercent = 70.0,
            criticalAtUsedPercent = 75.0,
            emergencyAtUsedPercent = 90.0,
        )
        val manager = AdaptiveContextBudgetManager(config)

        val watch = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(60.0),
            hints = ContextHints(recentToolCalls = 2),
        )
        assertEquals(CompressionMode.LIGHT, watch.mode)

        val critical = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(75.0),
            hints = ContextHints(recentToolCalls = 0),
        )
        assertEquals(CompressionMode.AGGRESSIVE, critical.mode)

        val emergency = manager.decidePlan(
            messageCount = config.minMessagesForCompression,
            turnsSinceLastCompression = null,
            zone = manager.riskZone(90.0),
            hints = ContextHints(recentToolCalls = 0),
        )
        assertEquals(CompressionMode.EMERGENCY, emergency.mode)
    }
}
