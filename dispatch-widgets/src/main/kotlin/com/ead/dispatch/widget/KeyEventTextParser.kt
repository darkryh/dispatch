package com.ead.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent

internal data class KeyEventTextParseConfig(
    val mapSpaceKeyToSpace: Boolean = false,
    val replaceNewlineWithSpace: Boolean = false,
    val replaceTabWithSpace: Boolean = false,
    val extraNonTextKeys: Set<String> = emptySet(),
)

internal fun parseTextFromKeyEvent(
    event: KeyboardEvent,
    config: KeyEventTextParseConfig = KeyEventTextParseConfig(),
): String? {
    if (event.ctrl || event.alt) return null

    val key = event.key
    if (key.isEmpty()) return null
    if (config.mapSpaceKeyToSpace && key == "Space") return " "

    if (key in (DEFAULT_NON_TEXT_KEYS + config.extraNonTextKeys)) return null
    if (isFunctionKey(key)) return null

    var normalized = key.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.any { it.isISOControl() && it != '\n' && it != '\t' }) return null

    if (config.replaceNewlineWithSpace) {
        normalized = normalized.replace('\n', ' ')
    }
    if (config.replaceTabWithSpace) {
        normalized = normalized.replace('\t', ' ')
    }

    return normalized
}

private fun isFunctionKey(key: String): Boolean {
    if (key.length < 2 || key[0] != 'F') return false
    return key.drop(1).all { it.isDigit() }
}

private val DEFAULT_NON_TEXT_KEYS =
    setOf(
        "ArrowDown",
        "ArrowLeft",
        "ArrowRight",
        "ArrowUp",
        "Alt",
        "Backspace",
        "CapsLock",
        "Clear",
        "Compose",
        "Control",
        "Dead",
        "Delete",
        "End",
        "Enter",
        "Escape",
        "Home",
        "Insert",
        "Meta",
        "NumLock",
        "PageDown",
        "PageUp",
        "PasteEnd",
        "PasteStart",
        "Pause",
        "PrintScreen",
        "Process",
        "ScrollLock",
        "Shift",
        "Tab",
        "Unidentified",
    )
