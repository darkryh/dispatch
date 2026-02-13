package com.ead.dispatch.sample.presentation.chat_mode.chat.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val ToolJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Dispatchable
fun ToolCallMessage(
    message: CliMessage,
    modifier: Modifier = Modifier,
) {
    val display = parseToolDisplay(message)
    if (display == null) {
        val toolLabel = message.toolName?.let { "Tool payload ($it)" } ?: "Tool payload"
        val preview = unparsedPayloadPreview(message.data)
        Column(modifier = modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(text = "⚙ ", style = rgb("#FFA500"))
                Text(text = "$toolLabel (unparsed)", style = rgb("#FFA500"))
                Spacer(modifier = Modifier.width(2))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(4))
                Text(text = preview, style = rgb("#C7CBD1"))
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(2))
            val headline = splitHeadline(display.summary, display.actionLabel)
            if (headline != null) {
                Text(text = headline.first, style = display.actionStyle)
                Text(text = headline.second, style = rgb("#FFFFFF"))
            } else {
                Text(text = display.summary, style = rgb("#FFFFFF"))
            }
        }
        display.properties.forEach { (key, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(4))
                val line = rgb("#FFFFFF")("- $key: ") + rgb("#C7CBD1")(value)
                Text(text = line)
            }
        }
        if (display.warnings.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(4))
                Text(text = "Warnings: ${display.warnings.joinToString(", ")}", style = rgb("#FFA500"))
            }
        }
    }
}

private data class ToolDisplay(
    val summary: String,
    val actionLabel: String,
    val actionStyle: TextStyle,
    val properties: List<Pair<String, String>>,
    val warnings: List<String>,
)

private data class ToolAction(
    val verb: String,
    val pastVerb: String,
    val style: TextStyle,
)

private fun parseToolDisplay(message: CliMessage): ToolDisplay? {
    val raw = extractJsonObject(message.data) ?: message.data
    val normalized = normalizeToolJson(raw)
    val root = runCatching { ToolJson.parseToJsonElement(normalized).jsonObject }.getOrNull() ?: return null

    val type = root["type"]?.jsonPrimitive?.asStringOrNull()
    return if (type == "success" || type == "failure") {
        parseToolResult(root, type == "failure", message.toolName)
    } else {
        parseToolRequest(root, message.toolName)
    }
}

private fun parseToolResult(root: JsonObject, isError: Boolean, toolName: String?): ToolDisplay {
    if (isError) {
        val message = root["message"]?.jsonPrimitive?.asStringOrNull() ?: "Tool failed."
        val error = root["error"]?.jsonObject
        val properties = buildList {
            error?.get("code")?.jsonPrimitive?.asStringOrNull()?.let { add("code" to it) }
            error?.get("details")?.jsonPrimitive?.asStringOrNull()?.let { add("details" to it) }
        }
        return ToolDisplay(
            summary = "Error: $message",
            actionLabel = "Error",
            actionStyle = rgb("#B00020"),
            properties = properties,
            warnings = emptyList(),
        )
    }

    val data = root["data"]?.jsonObject
    val action = data?.get("action")?.jsonPrimitive?.asStringOrNull()
    val entity = data?.get("entity")?.jsonPrimitive?.asStringOrNull()
    val summary = root["message"]?.jsonPrimitive?.asStringOrNull()
        ?: data?.get("summary")?.jsonPrimitive?.asStringOrNull()
        ?: "Tool completed."
    val actionLabel = action?.let { it.replaceFirstChar { ch -> ch.uppercase() } }
        ?: actionFromToolName(toolName)
    val actionInfo = toolAction(actionLabel)
    val warnings = root["warnings"]
        ?.takeIf { it is JsonArray }
        ?.let { it as JsonArray }
        ?.mapNotNull { (it as? JsonPrimitive)?.asStringOrNull() }
        ?: emptyList()

    val properties = buildList {
        val entityLabel = entityLabel(entity)
        if (entityLabel != null) {
            add("entity" to entityLabel)
        }
    }

    return ToolDisplay(
        summary = summary,
        actionLabel = actionInfo.pastVerb,
        actionStyle = actionInfo.style,
        properties = properties,
        warnings = warnings,
    )
}

private fun parseToolRequest(root: JsonObject, toolName: String?): ToolDisplay {
    val request = root["request"] as? JsonObject
    val entityId = root["entityId"]?.jsonPrimitive?.asStringOrNull()

    val actionInfo = toolAction(actionFromToolName(toolName))
    val entityLabel = entityLabelFromToolName(toolName)
    val displayName = request?.let { guessDisplayName(it) } ?: entityId

    val summary = buildString {
        append(actionInfo.pastVerb)
        if (entityLabel != null) {
            append(" ")
            append(entityLabel)
        }
        if (displayName != null) {
            append(": ")
            append(displayName)
        }
        append(".")
    }

    val properties = buildList {
        if (request != null) {
            flattenJson(request, null, this)
        } else if (entityId != null) {
            add("entityId" to entityId)
        }
    }

    return ToolDisplay(
        summary = summary,
        actionLabel = actionInfo.pastVerb,
        actionStyle = actionInfo.style,
        properties = properties,
        warnings = emptyList(),
    )
}

private fun actionFromToolName(toolName: String?): String {
    val name = toolName?.lowercase().orEmpty()
    return when {
        name.startsWith("create") -> "Create"
        name.startsWith("update") -> "Update"
        name.startsWith("delete") -> "Delete"
        name.startsWith("list") -> "List"
        name.startsWith("search") -> "Search"
        name.startsWith("get") -> "Get"
        else -> "Action"
    }
}

private fun toolAction(label: String): ToolAction = when (label.lowercase()) {
    "create", "created" -> ToolAction("Create", "Created", rgb("#66D17A"))
    "update", "updated" -> ToolAction("Update", "Updated", rgb("#6BE3FF"))
    "delete", "deleted" -> ToolAction("Delete", "Deleted", rgb("#FF6B6B"))
    "list", "listed" -> ToolAction("List", "Listed", rgb("#6BE3FF"))
    "search", "searched", "found" -> ToolAction("Search", "Found", rgb("#6BE3FF"))
    "get", "read", "fetched" -> ToolAction("Get", "Fetched", rgb("#6BE3FF"))
    "error" -> ToolAction("Error", "Error", rgb("#B00020"))
    else -> ToolAction(label, label, rgb("#FFA500"))
}

private fun entityLabel(raw: String?): String? = when (raw?.uppercase()) {
    "STORY" -> "story"
    "CHARACTER" -> "character"
    "LOCATION" -> "location"
    "ARC" -> "arc"
    "WORLD_RULE" -> "world rule"
    "CULTURE" -> "culture"
    "EVENT" -> "event"
    "ORGANIZATION" -> "organization"
    "RELATIONSHIP" -> "relationship"
    "LOCATION_FEATURE" -> "location feature"
    "ARTIFACT" -> "artifact"
    "TIMELINE_ENTRY" -> "timeline entry"
    else -> null
}

private fun entityLabelFromToolName(toolName: String?): String? {
    if (toolName.isNullOrBlank()) return null
    val raw = toolName
        .removePrefix("create")
        .removePrefix("update")
        .removePrefix("delete")
        .removePrefix("list")
        .removePrefix("get")
        .removePrefix("search")
        .trim()
        .ifBlank { return null }
    val spaced = raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
    return spaced.lowercase()
}

private fun splitHeadline(summary: String, actionLabel: String): Pair<String, String>? {
    val trimmed = summary.trimStart()
    val expected = actionLabel.trim()
    if (!trimmed.startsWith(expected, ignoreCase = true)) return null
    val rest = trimmed.drop(expected.length)
    return expected to rest
}

private fun guessDisplayName(request: JsonObject): String? {
    val keys = listOf("name", "title")
    for (key in keys) {
        val value = request[key]?.jsonPrimitive?.asStringOrNull()?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun flattenJson(
    element: JsonElement,
    prefix: String?,
    output: MutableList<Pair<String, String>>,
) {
    when (element) {
        is JsonObject -> {
            element.forEach { (key, value) ->
                val nextPrefix = if (prefix == null) key else "$prefix.$key"
                flattenJson(value, nextPrefix, output)
            }
        }
        is JsonArray -> {
            val values = element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.asStringOrNull()
                    is JsonObject -> item.toString()
                    else -> null
                }
            }.map { it.trim() }.filter { it.isNotBlank() }
            if (values.isNotEmpty() && prefix != null) {
                output.add(prefix to values.joinToString(", "))
            }
        }
        is JsonPrimitive -> {
            val content = element.asStringOrNull()?.trim().orEmpty()
            if (content.isNotBlank() && prefix != null) {
                output.add(prefix to content)
            }
        }
    }
}

private fun JsonPrimitive.asStringOrNull(): String? {
    val value = content
    if (value == "null") return null
    return value
}

private fun unparsedPayloadPreview(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "(empty payload)"
    if (trimmed.length <= MAX_UNPARSED_PREVIEW_LENGTH) return trimmed
    val remaining = trimmed.length - MAX_UNPARSED_PREVIEW_LENGTH
    return buildString {
        append(trimmed.take(MAX_UNPARSED_PREVIEW_LENGTH))
        append("\n… [truncated ")
        append(remaining)
        append(" chars]")
    }
}

private const val MAX_UNPARSED_PREVIEW_LENGTH = 4000
