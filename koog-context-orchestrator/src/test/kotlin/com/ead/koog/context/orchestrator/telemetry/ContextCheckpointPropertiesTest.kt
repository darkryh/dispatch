package com.ead.koog.context.orchestrator.telemetry

import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.policy.ContextRiskZone
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextCheckpointPropertiesTest {

    @Test
    fun `merge writes compaction telemetry keys`() {
        val snapshot = ContextSnapshot(
            telemetry = ContextTelemetry(
                estimatedPromptTokens = 950,
                tokenUsageKnown = true,
                maxContextTokens = 4096,
                usedPercent = 23.1,
                remainingTokens = 3146,
                remainingPercent = 76.9,
                riskZone = ContextRiskZone.WATCH,
                compactionCount = 4,
                turnsSinceLastCompaction = 2,
                lastCompactionMode = CompressionMode.AGGRESSIVE,
                growthTokensPerTurn = 118,
                continuityIntegrityScore = 88,
            ),
            continuityPacket = null,
        )

        val merged = ContextCheckpointProperties.merge(existing = null, snapshot = snapshot)

        assertEquals(JsonPrimitive(4), merged[ContextCheckpointProperties.COMPACTION_COUNT])
        assertEquals(JsonPrimitive(2), merged[ContextCheckpointProperties.TURNS_SINCE_LAST_COMPACTION])
        assertEquals(JsonPrimitive(CompressionMode.AGGRESSIVE.name), merged[ContextCheckpointProperties.LAST_COMPACTION_MODE])
    }

    @Test
    fun `merge preserves existing properties without legacy aliases`() {
        val existing = mapOf("custom" to JsonPrimitive("keep-me"))
        val snapshot = ContextSnapshot(
            telemetry = ContextTelemetry(
                estimatedPromptTokens = 1200,
                tokenUsageKnown = true,
                maxContextTokens = 4096,
                usedPercent = 29.3,
                remainingTokens = 2896,
                remainingPercent = 70.7,
                riskZone = ContextRiskZone.WARNING,
                compactionCount = 1,
                turnsSinceLastCompaction = null,
                lastCompactionMode = null,
                growthTokensPerTurn = 180,
                continuityIntegrityScore = 76,
            ),
            continuityPacket = null,
        )

        val merged = ContextCheckpointProperties.merge(existing = existing, snapshot = snapshot)

        assertEquals(JsonPrimitive("keep-me"), merged["custom"])
        assertEquals(JsonPrimitive(1), merged[ContextCheckpointProperties.COMPACTION_COUNT])
        assertTrue(ContextCheckpointProperties.TURNS_SINCE_LAST_COMPACTION !in merged)
        assertTrue(ContextCheckpointProperties.LAST_COMPACTION_MODE !in merged)
    }
}
