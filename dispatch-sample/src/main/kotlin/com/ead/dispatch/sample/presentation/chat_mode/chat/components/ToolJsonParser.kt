package com.ead.dispatch.sample.presentation.chat_mode.chat.components

internal fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return null
    return raw.substring(start, end + 1)
}

internal fun normalizeToolJson(raw: String): String {
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
            '\n' -> {
                if (inString) result.append("\\n") else result.append(ch)
            }
            '\r' -> {
                if (inString) result.append("\\r") else result.append(ch)
            }
            else -> result.append(ch)
        }
    }
    return result.toString()
}
