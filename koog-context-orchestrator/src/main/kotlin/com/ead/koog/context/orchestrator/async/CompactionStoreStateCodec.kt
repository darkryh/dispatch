package com.ead.koog.context.orchestrator.async

import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.TaskPhase
import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.policy.ContextRiskZone
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class CompactionStoreState(
    val queue: ArrayDeque<ContextCompactionJob> = ArrayDeque(),
    val running: LinkedHashMap<String, ContextCompactionJob> = linkedMapOf(),
    val latestArtifactByAgent: LinkedHashMap<String, ContextCompactionArtifact> = linkedMapOf(),
    val latestVersionByAgent: LinkedHashMap<String, Long> = linkedMapOf(),
    val latestFingerprintByAgent: LinkedHashMap<String, String> = linkedMapOf(),
)

internal object CompactionStoreStateCodec {
    fun encodeState(state: CompactionStoreState): JsonObject = buildJsonObject {
        put("queue", buildJsonArray { state.queue.forEach { add(encodeJob(it)) } })
        put("running", buildJsonArray { state.running.values.forEach { add(encodeJob(it)) } })
        put("latestArtifacts", buildJsonArray { state.latestArtifactByAgent.values.forEach { add(encodeArtifact(it)) } })
        put("latestVersions", buildJsonObject {
            state.latestVersionByAgent.forEach { (agentId, version) -> put(agentId, JsonPrimitive(version)) }
        })
        put("latestFingerprints", buildJsonObject {
            state.latestFingerprintByAgent.forEach { (agentId, fingerprint) -> put(agentId, JsonPrimitive(fingerprint)) }
        })
    }

    fun decodeState(root: JsonObject): CompactionStoreState {
        val queue = ArrayDeque(root.arrayOrEmpty("queue").mapNotNull { decodeJob(it) })
        val running = linkedMapOf<String, ContextCompactionJob>().apply {
            root.arrayOrEmpty("running")
                .mapNotNull { decodeJob(it) }
                .forEach { put(it.id, it) }
        }
        val latestArtifactByAgent = linkedMapOf<String, ContextCompactionArtifact>().apply {
            root.arrayOrEmpty("latestArtifacts")
                .mapNotNull { decodeArtifact(it) }
                .forEach { put(it.agentId, it) }
        }
        val latestVersionByAgent = linkedMapOf<String, Long>().apply {
            root.objectOrEmpty("latestVersions").forEach { (agentId, value) ->
                value.jsonPrimitive.longOrNull?.let { put(agentId, it) }
            }
        }
        val latestFingerprintByAgent = linkedMapOf<String, String>().apply {
            root.objectOrEmpty("latestFingerprints").forEach { (agentId, value) ->
                value.jsonPrimitive.contentOrNull?.let { put(agentId, it) }
            }
        }
        return CompactionStoreState(
            queue = queue,
            running = running,
            latestArtifactByAgent = latestArtifactByAgent,
            latestVersionByAgent = latestVersionByAgent,
            latestFingerprintByAgent = latestFingerprintByAgent,
        )
    }

    fun encodeJob(job: ContextCompactionJob): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(job.id))
        put("agentId", JsonPrimitive(job.agentId))
        put("sourceVersion", JsonPrimitive(job.sourceVersion))
        put("sourceFingerprint", JsonPrimitive(job.sourceFingerprint))
        put("mode", JsonPrimitive(job.mode.name))
        put("riskZone", JsonPrimitive(job.riskZone.name))
        put("hints", encodeHints(job.hints))
        put("promptMessages", buildJsonArray { job.promptMessages.forEach { add(JsonPrimitive(it)) } })
        put("createdAtEpochMillis", JsonPrimitive(job.createdAtEpochMillis))
    }

    fun decodeJob(element: JsonElement): ContextCompactionJob? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val agentId = obj["agentId"]?.jsonPrimitive?.contentOrNull ?: return null
        val sourceVersion = obj["sourceVersion"]?.jsonPrimitive?.longOrNull ?: return null
        val sourceFingerprint = obj["sourceFingerprint"]?.jsonPrimitive?.contentOrNull ?: return null
        val mode = obj["mode"]?.jsonPrimitive?.contentOrNull?.let { runCatching { CompressionMode.valueOf(it) }.getOrNull() } ?: return null
        val riskZone = obj["riskZone"]?.jsonPrimitive?.contentOrNull?.let { runCatching { ContextRiskZone.valueOf(it) }.getOrNull() } ?: return null
        val hints = obj["hints"]?.let { decodeHints(it) } ?: ContextHints()
        val promptMessages = obj.arrayOrEmpty("promptMessages").mapNotNull { it.jsonPrimitive.contentOrNull }
        val createdAtEpochMillis = obj["createdAtEpochMillis"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        return ContextCompactionJob(
            id = id,
            agentId = agentId,
            sourceVersion = sourceVersion,
            sourceFingerprint = sourceFingerprint,
            mode = mode,
            riskZone = riskZone,
            hints = hints,
            promptMessages = promptMessages,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    fun encodeArtifact(artifact: ContextCompactionArtifact): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(artifact.id))
        put("agentId", JsonPrimitive(artifact.agentId))
        put("sourceVersion", JsonPrimitive(artifact.sourceVersion))
        put("sourceFingerprint", JsonPrimitive(artifact.sourceFingerprint))
        put("resultVersion", JsonPrimitive(artifact.resultVersion))
        put("mode", JsonPrimitive(artifact.mode.name))
        put("text", JsonPrimitive(artifact.text))
        put("createdAtEpochMillis", JsonPrimitive(artifact.createdAtEpochMillis))
    }

    fun decodeArtifact(element: JsonElement): ContextCompactionArtifact? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val agentId = obj["agentId"]?.jsonPrimitive?.contentOrNull ?: return null
        val sourceVersion = obj["sourceVersion"]?.jsonPrimitive?.longOrNull ?: return null
        val sourceFingerprint = obj["sourceFingerprint"]?.jsonPrimitive?.contentOrNull ?: return null
        val resultVersion = obj["resultVersion"]?.jsonPrimitive?.longOrNull ?: return null
        val mode = obj["mode"]?.jsonPrimitive?.contentOrNull?.let { runCatching { CompressionMode.valueOf(it) }.getOrNull() } ?: return null
        val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return null
        val createdAtEpochMillis = obj["createdAtEpochMillis"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        return ContextCompactionArtifact(
            id = id,
            agentId = agentId,
            sourceVersion = sourceVersion,
            sourceFingerprint = sourceFingerprint,
            resultVersion = resultVersion,
            mode = mode,
            text = text,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    private fun encodeHints(hints: ContextHints): JsonObject = buildJsonObject {
        put("phase", JsonPrimitive(hints.phase.name))
        put("unresolvedCommitments", JsonPrimitive(hints.unresolvedCommitments))
        put("recentToolCalls", JsonPrimitive(hints.recentToolCalls))
        hints.continuityPacket?.let { continuity ->
            put("continuity", buildJsonObject {
                continuity.objective?.let { put("objective", JsonPrimitive(it)) }
                putStringArray("constraints", continuity.constraints)
                putStringArray("acceptedDecisions", continuity.acceptedDecisions)
                putStringArray("pendingActions", continuity.pendingActions)
                putStringArray("criticalReferences", continuity.criticalReferences)
                putStringArray("latestToolOutcomes", continuity.latestToolOutcomes)
                putStringArray("openQuestions", continuity.openQuestions)
            })
        }
    }

    private fun decodeHints(element: JsonElement): ContextHints {
        val obj = element as? JsonObject ?: return ContextHints()
        val phase = obj["phase"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { TaskPhase.valueOf(it) }.getOrNull() }
            ?: TaskPhase.EXECUTION
        val unresolvedCommitments = obj["unresolvedCommitments"]?.jsonPrimitive?.intOrNull ?: 0
        val recentToolCalls = obj["recentToolCalls"]?.jsonPrimitive?.intOrNull ?: 0
        val continuity = obj["continuity"]?.jsonObject?.let { continuityObj ->
            ContinuityPacket(
                objective = continuityObj["objective"]?.jsonPrimitive?.contentOrNull,
                constraints = continuityObj.readStringArray("constraints"),
                acceptedDecisions = continuityObj.readStringArray("acceptedDecisions"),
                pendingActions = continuityObj.readStringArray("pendingActions"),
                criticalReferences = continuityObj.readStringArray("criticalReferences"),
                latestToolOutcomes = continuityObj.readStringArray("latestToolOutcomes"),
                openQuestions = continuityObj.readStringArray("openQuestions"),
            )
        }

        return ContextHints(
            phase = phase,
            unresolvedCommitments = unresolvedCommitments,
            recentToolCalls = recentToolCalls,
            factConcepts = emptyList(),
            continuityPacket = continuity,
        )
    }
}

private fun JsonObject.arrayOrEmpty(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

private fun JsonObject.objectOrEmpty(key: String): JsonObject = this[key]?.jsonObject ?: JsonObject(emptyMap())

private fun JsonObject.readStringArray(key: String): List<String> =
    this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

private fun kotlinx.serialization.json.JsonObjectBuilder.putStringArray(key: String, values: List<String>) {
    put(key, buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    })
}
