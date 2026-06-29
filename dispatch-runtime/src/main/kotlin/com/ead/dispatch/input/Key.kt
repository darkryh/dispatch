package com.ead.dispatch.input

import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * A single logical key, normalized from Mordant's W3C-format [KeyboardEvent.key] string.
 *
 * Dispatch never matches keys by raw string. Mordant already normalizes raw terminal bytes into
 * the canonical [W3C key names](https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_key_values)
 * identically across Linux, macOS, and Windows (the per-OS decoding lives in Mordant's
 * `PosixEventParser` and `TerminalInterface.windows`). This type is the single, type-safe view of
 * that canonical key: matching against [Key] values is typo-proof and `when`-exhaustive, and the
 * one place that touches the underlying strings is [KeyMapping].
 *
 * Two shapes:
 * - [Named] — the fixed set of non-printable / special keys a terminal can report.
 * - [Char] — a printable character key (letters, digits, punctuation, unicode, and space).
 */
sealed interface Key {
    /** The fixed set of non-printable / named keys a terminal can report. */
    enum class Named : Key {
        Enter,
        Escape,
        Tab,
        Backspace,
        Delete,
        Insert,
        ArrowUp,
        ArrowDown,
        ArrowLeft,
        ArrowRight,
        Home,
        End,
        PageUp,
        PageDown,

        /** Bracketed-paste begin signal (Mordant pseudo-key). */
        PasteStart,

        /** Bracketed-paste end signal (Mordant pseudo-key). */
        PasteEnd,
        F1,
        F2,
        F3,
        F4,
        F5,
        F6,
        F7,
        F8,
        F9,
        F10,
        F11,
        F12,

        /** Reported when the key cannot be identified, or for multi-character paste payloads. */
        Unknown,
    }

    /** A printable character key (the character itself, e.g. `'a'`, `'1'`, `'€'`, or `' '`). */
    @JvmInline
    value class Char(
        val char: kotlin.Char,
    ) : Key

    @Suppress("ktlint:standard:property-naming")
    companion object {
        // Ergonomic aliases so call sites read `Key.Enter`, not `Key.Named.Enter`.
        val Enter: Key = Named.Enter
        val Escape: Key = Named.Escape
        val Tab: Key = Named.Tab
        val Backspace: Key = Named.Backspace
        val Delete: Key = Named.Delete
        val Insert: Key = Named.Insert
        val ArrowUp: Key = Named.ArrowUp
        val ArrowDown: Key = Named.ArrowDown
        val ArrowLeft: Key = Named.ArrowLeft
        val ArrowRight: Key = Named.ArrowRight
        val Home: Key = Named.Home
        val End: Key = Named.End
        val PageUp: Key = Named.PageUp
        val PageDown: Key = Named.PageDown
        val PasteStart: Key = Named.PasteStart
        val PasteEnd: Key = Named.PasteEnd

        /**
         * The space bar. Mordant reports space as the printable character `" "`, so [Space] is a
         * [Char] alias: editors still receive it as insertable text, while command widgets can
         * match it ergonomically as `event.key == Key.Space`.
         */
        val Space: Key = Char(' ')

        /** Match a literal printable character, e.g. `Key.char('q')`. */
        fun char(c: kotlin.Char): Key = Char(c)
    }
}

/**
 * A typed view over a Mordant [KeyboardEvent]. Wrap a raw event with [asKeyEvent] (or [keyEvent] in
 * tests), then match against [Key] values instead of strings.
 */
@JvmInline
value class KeyEvent internal constructor(
    val raw: KeyboardEvent,
) {
    /** The normalized [Key] for this event. */
    val key: Key get() = KeyMapping.toKey(raw)

    /** The printable character for this event, or `null` if it is a named key or multi-char paste. */
    val char: Char? get() = (key as? Key.Char)?.char

    val ctrl: Boolean get() = raw.ctrl
    val alt: Boolean get() = raw.alt
    val shift: Boolean get() = raw.shift

    /**
     * `true` when this event is a single printable character with no Ctrl/Alt held — i.e. ordinary
     * text input. Note multi-character paste payloads are handled separately by the input editor.
     */
    val isText: Boolean get() = char != null && !ctrl && !alt

    /** `true` if this event is the printable character [c] (case-insensitive by default). */
    fun isChar(
        c: Char,
        ignoreCase: Boolean = true,
    ): Boolean = char?.equals(c, ignoreCase) == true

    /** `true` if this event matches [stroke] (key + required modifiers). */
    fun matches(stroke: KeyStroke): Boolean = stroke.matches(this)
}

/** Wrap a raw Mordant [KeyboardEvent] in the typed [KeyEvent] view. */
fun KeyboardEvent.asKeyEvent(): KeyEvent = KeyEvent(this)

/** Construct a [KeyEvent] directly from a key name and modifiers (convenient for tests). */
fun keyEvent(
    key: String,
    ctrl: Boolean = false,
    alt: Boolean = false,
    shift: Boolean = false,
): KeyEvent = KeyEvent(KeyboardEvent(key = key, ctrl = ctrl, alt = alt, shift = shift))

/**
 * A key plus required modifiers — the unit of a declarative shortcut (e.g. `Ctrl+P`, `Shift+Tab`).
 *
 * Matching semantics:
 * - For a [Key.Char] target, the character is matched case-insensitively (capitalization already
 *   encodes Shift), Ctrl/Alt must match exactly, and Shift is required only when [shift] is set.
 * - For a [Key.Named] target, all three modifiers must match exactly.
 */
data class KeyStroke(
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    fun matches(event: KeyEvent): Boolean =
        when (val k = key) {
            is Key.Char ->
                event.isChar(k.char) &&
                    event.ctrl == ctrl &&
                    event.alt == alt &&
                    (!shift || event.shift)
            else ->
                event.key == k &&
                    event.ctrl == ctrl &&
                    event.alt == alt &&
                    event.shift == shift
        }

    /** Human-readable label such as `"Ctrl+Enter"` or `"Shift+q"`, useful for key-hint bars. */
    fun label(): String {
        val parts = mutableListOf<String>()
        if (ctrl) parts += "Ctrl"
        if (alt) parts += "Alt"
        if (shift) parts += "Shift"
        parts +=
            when (val k = key) {
                is Key.Char -> k.char.toString()
                is Key.Named -> k.name
            }
        return parts.joinToString("+")
    }
}

/** Require Ctrl with this key: `Key.Enter.ctrl`. */
val Key.ctrl: KeyStroke get() = KeyStroke(this, ctrl = true)

/** Require Alt with this key: `Key.Enter.alt`. */
val Key.alt: KeyStroke get() = KeyStroke(this, alt = true)

/** Require Shift with this key: `Key.Tab.shift`. */
val Key.shift: KeyStroke get() = KeyStroke(this, shift = true)

/** A plain (modifier-free) stroke for this key. */
fun Key.stroke(): KeyStroke = KeyStroke(this)

/** `Ctrl+<char>`, e.g. `ctrl('p')`. */
fun ctrl(c: Char): KeyStroke = KeyStroke(Key.Char(c), ctrl = true)

/** `Alt+<char>`, e.g. `alt('x')`. */
fun alt(c: Char): KeyStroke = KeyStroke(Key.Char(c), alt = true)

/** `Shift+<char>`, e.g. `shift('q')`. */
fun shift(c: Char): KeyStroke = KeyStroke(Key.Char(c), shift = true)

/**
 * The single source of truth mapping Mordant's canonical W3C key string to a [Key].
 *
 * This is the only place in Dispatch that compares against raw key strings. If Mordant ever renames
 * a key, exactly one branch here changes (and [com.ead.dispatch.input.KeyMappingTest] catches it).
 */
internal object KeyMapping {
    fun toKey(event: KeyboardEvent): Key =
        when (val k = event.key) {
            "Enter" -> Key.Named.Enter
            "Escape" -> Key.Named.Escape
            "Tab" -> Key.Named.Tab
            "Backspace" -> Key.Named.Backspace
            "Delete" -> Key.Named.Delete
            "Insert" -> Key.Named.Insert
            "ArrowUp" -> Key.Named.ArrowUp
            "ArrowDown" -> Key.Named.ArrowDown
            "ArrowLeft" -> Key.Named.ArrowLeft
            "ArrowRight" -> Key.Named.ArrowRight
            "Home" -> Key.Named.Home
            "End" -> Key.Named.End
            "PageUp" -> Key.Named.PageUp
            "PageDown" -> Key.Named.PageDown
            "PasteStart" -> Key.Named.PasteStart
            "PasteEnd" -> Key.Named.PasteEnd
            else ->
                functionKey(k)
                    ?: if (k.length == 1) Key.Char(k[0]) else Key.Named.Unknown
        }

    private fun functionKey(key: String): Key? {
        if (key.length < 2 || key[0] != 'F') return null
        val digits = key.drop(1)
        if (!digits.all { it.isDigit() }) return null
        return when (digits.toInt()) {
            1 -> Key.Named.F1
            2 -> Key.Named.F2
            3 -> Key.Named.F3
            4 -> Key.Named.F4
            5 -> Key.Named.F5
            6 -> Key.Named.F6
            7 -> Key.Named.F7
            8 -> Key.Named.F8
            9 -> Key.Named.F9
            10 -> Key.Named.F10
            11 -> Key.Named.F11
            12 -> Key.Named.F12
            else -> Key.Named.Unknown
        }
    }
}
