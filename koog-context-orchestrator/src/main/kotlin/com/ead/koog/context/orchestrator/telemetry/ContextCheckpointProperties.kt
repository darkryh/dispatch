package com.ead.koog.context.orchestrator.telemetry

import com.ead.koog.context.orchestrator.state.ContinuityPacket
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object ContextCheckpointProperties {
    const val USED_TOKENS_ESTIMATE = "koog.context.used_tokens_estimate"
    const val MAX_TOKENS = "koog.context.max_tokens"
    const val USED_PERCENT = "koog.context.used_percent"
    const val REMAINING_TOKENS = "koog.context.remaining_tokens"
    const val REMAINING_PERCENT = "koog.context.remaining_percent"
    const val RISK_ZONE = "koog.context.risk_zone"
    const val COMPRESSION_COUNT = "koog.context.compression_count"
    const val TURNS_SINCE_LAST_COMPRESSION = "koog.context.turns_since_last_compression"
    const val LAST_COMPRESSION_MODE = "koog.context.last_compression_mode"
    const val GROWTH_TOKENS_PER_TURN = "koog.context.growth_tokens_per_turn"
    const val CONTINUITY_INTEGRITY_SCORE = "koog.context.continuity_integrity_score"
    const val CONTINUITY_PACKET = "koog.context.continuity_packet"

    fun merge(
        existing: Map<String, JsonElement>?,
        snapshot: ContextSnapshot?,
    ): Map<String, JsonElement> {
        if (snapshot == null) return existing ?: emptyMap()

        val merged = (existing ?: emptyMap()).toMutableMap()
        val telemetry = snapshot.telemetry

        merged[USED_TOKENS_ESTIMATE] = JsonPrimitive(telemetry.estimatedPromptTokens)
        merged[MAX_TOKENS] = JsonPrimitive(telemetry.maxContextTokens)
        merged[USED_PERCENT] = JsonPrimitive(telemetry.usedPercent)
        merged[REMAINING_TOKENS] = JsonPrimitive(telemetry.remainingTokens)
        merged[REMAINING_PERCENT] = JsonPrimitive(telemetry.remainingPercent)
        merged[RISK_ZONE] = JsonPrimitive(telemetry.riskZone.name)
        merged[COMPRESSION_COUNT] = JsonPrimitive(telemetry.compressionCount)
        telemetry.turnsSinceLastCompression?.let { merged[TURNS_SINCE_LAST_COMPRESSION] = JsonPrimitive(it) }
        telemetry.lastCompressionMode?.let { merged[LAST_COMPRESSION_MODE] = JsonPrimitive(it.name) }
        merged[GROWTH_TOKENS_PER_TURN] = JsonPrimitive(telemetry.growthTokensPerTurn)
        merged[CONTINUITY_INTEGRITY_SCORE] = JsonPrimitive(telemetry.continuityIntegrityScore)

        val continuityPacket = snapshot.continuityPacket
        if (continuityPacket != null && continuityPacket.isMeaningful()) {
            merged[CONTINUITY_PACKET] = continuityPacket.toJsonObject()
        }

        return merged
    }

    fun readRemainingPercent(properties: Map<String, JsonElement>?): Int? {
        val value = properties?.get(REMAINING_PERCENT) ?: return null
        return value.jsonPrimitive.doubleOrNull?.toInt()
            ?: value.jsonPrimitive.intOrNull
    }

    fun readRiskZone(properties: Map<String, JsonElement>?): String? {
        return properties?.get(RISK_ZONE)?.jsonPrimitive?.contentOrNull
    }

    private fun ContinuityPacket.toJsonObject(): JsonObject = buildJsonObject {
        objective?.takeIf { it.isNotBlank() }?.let { put("objective", JsonPrimitive(it)) }
        if (constraints.isNotEmpty()) put("constraints", constraints.toJsonArray())
        if (acceptedDecisions.isNotEmpty()) put("acceptedDecisions", acceptedDecisions.toJsonArray())
        if (pendingActions.isNotEmpty()) put("pendingActions", pendingActions.toJsonArray())
        if (criticalReferences.isNotEmpty()) put("criticalReferences", criticalReferences.toJsonArray())
        if (latestToolOutcomes.isNotEmpty()) put("latestToolOutcomes", latestToolOutcomes.toJsonArray())
        if (openQuestions.isNotEmpty()) put("openQuestions", openQuestions.toJsonArray())
    }

    private fun List<String>.toJsonArray() = buildJsonArray {
        forEach { add(JsonPrimitive(it)) }
    }
}
