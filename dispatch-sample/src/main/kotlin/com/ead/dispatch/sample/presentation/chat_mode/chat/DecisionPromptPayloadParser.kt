package com.ead.dispatch.sample.presentation.chat_mode.chat

import com.ead.dispatch.widget.DecisionOption
import com.ead.dispatch.sample.domain.agents.tools.model.DEFAULT_DECISION_PLACEHOLDER
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class DecisionPromptPayload(
    val promptId: String?,
    val question: String,
    val options: List<DecisionOption>,
    val placeholder: String,
)

private val DecisionPromptJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun parseDecisionPromptPayload(
    toolName: String?,
    rawContent: String,
): DecisionPromptPayload? = runCatching {
    val extracted = extractJsonObject(rawContent) ?: rawContent
    val normalized = normalizeToolJson(extracted)
    val root = parseRootObject(normalized) ?: return null

    if (!looksLikeDecisionPayload(toolName, root)) return null

    val payload = extractPayloadObject(root)
    val question = payload.string("question")
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val options = parseDecisionOptions(payload["options"]).takeIf { it.isNotEmpty() } ?: return null
    val placeholder = payload.string("customPlaceholder")
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_DECISION_PLACEHOLDER

    DecisionPromptPayload(
        promptId = payload.string("promptId"),
        question = question,
        options = options,
        placeholder = placeholder,
    )
}.getOrNull()

private fun parseRootObject(raw: String): JsonObject? =
    runCatching { DecisionPromptJson.parseToJsonElement(raw) }
        .getOrNull() as? JsonObject

private fun looksLikeDecisionPayload(toolName: String?, root: JsonObject): Boolean {
    val normalizedName = toolName?.lowercase().orEmpty()
    if (normalizedName in DECISION_TOOL_NAMES) return true

    if (root.string("type") == "user_choice") return true

    val payload = extractPayloadObject(root)
    val hasQuestion = payload.string("question")?.isNotBlank() == true
    val hasOptions = payload["options"] is JsonArray
    return hasQuestion && hasOptions
}

private fun extractPayloadObject(root: JsonObject): JsonObject {
    val type = root.string("type")
    val base = if (type == "success") {
        root["data"] as? JsonObject ?: root
    } else {
        root
    }
    val fromRequest = base["request"] as? JsonObject
    if (fromRequest != null) return fromRequest

    val fromPayload = base["payload"] as? JsonObject
    if (fromPayload != null) return fromPayload

    return base
}

private fun parseDecisionOptions(element: JsonElement?): List<DecisionOption> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.asStringOrNull()?.trim()
            is JsonObject -> {
                item.string("label")
                    ?: item.string("text")
                    ?: item.string("title")
            }
            else -> null
        }?.takeIf { it.isNotBlank() }?.let { DecisionOption(it) }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.asStringOrNull()

private fun JsonPrimitive.asStringOrNull(): String? {
    val value = content
    return if (value == "null") null else value
}

private fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return null
    return raw.substring(start, end + 1)
}

private fun normalizeToolJson(raw: String): String {
    if (raw.isBlank()) return raw
    val result = StringBuilder(raw.length)
    var inString = false
    var escaped = false
    raw.forEach { ch ->
        if (escaped) {
            result.append(ch)
            escaped = false
            return@forEach
        }
        when (ch) {
            '\\' -> {
                result.append(ch)
                escaped = true
            }
            '"' -> {
                result.append(ch)
                inString = !inString
            }
            '\n' -> if (inString) result.append("\\n") else result.append(ch)
            '\r' -> if (inString) result.append("\\r") else result.append(ch)
            else -> result.append(ch)
        }
    }
    return result.toString()
}

private val DECISION_TOOL_NAMES = setOf(
    "requestuserchoice",
    "requestdecision",
    "askuserchoice",
)
