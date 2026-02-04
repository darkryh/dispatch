package com.ead.dispatch.runtime

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.isCtrlC

data class ExitKeyBinding(
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    fun matches(event: KeyboardEvent): Boolean {
        if (ctrl && key.equals("C", ignoreCase = true) && event.isCtrlC) {
            return true
        }

        val keyMatches = event.key.equals(key, ignoreCase = true)
        if (!keyMatches) return false

        return event.ctrl == ctrl && event.alt == alt && event.shift == shift
    }

    fun label(): String {
        val parts = mutableListOf<String>()
        if (ctrl) parts += "Ctrl"
        if (alt) parts += "Alt"
        if (shift) parts += "Shift"
        parts += key
        return parts.joinToString("+")
    }

    companion object {
        fun ctrl(key: String): ExitKeyBinding = ExitKeyBinding(key = key, ctrl = true)
    }
}
