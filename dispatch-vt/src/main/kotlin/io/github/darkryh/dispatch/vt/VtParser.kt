package io.github.darkryh.dispatch.vt

/**
 * A notable control sequence, recorded with the stream offset at which it was applied.
 *
 * Only sequences the render invariants reason about are recorded; ordinary text and cursor motion
 * are applied to the screen but not logged, since a 3 MB capture would otherwise produce millions
 * of entries.
 */
data class VtOp(
    val kind: Kind,
    val offset: Int,
    val detail: String = "",
) {
    enum class Kind {
        ERASE_DISPLAY,
        ERASE_LINE,
        ERASE_SCROLLBACK,
        ALT_SCREEN_ENTER,
        ALT_SCREEN_EXIT,
        SCROLL_REGION_SET,
        SCROLL_REGION_RESET,
        SYNC_BEGIN,
        SYNC_END,
        CURSOR_HIDE,
        CURSOR_SHOW,
        OSC,
        UNKNOWN,
    }
}

/**
 * An incremental ANSI/VT parser that drives a [VtScreen].
 *
 * The parser is deliberately incremental and stateful: [feed] may be called with an arbitrary slice
 * of the stream, including one that splits an escape sequence in half. That is not a convenience —
 * it is the whole point. Dispatch's frames are split across multiple `write(2)` calls at arbitrary
 * byte offsets, and reproducing what the terminal saw requires parsing exactly the same way.
 *
 * Unrecognised sequences are recorded rather than thrown, so a capture can be analysed and reported
 * on; [strict] turns them into failures for tests that assert the renderer's vocabulary is closed.
 */
class VtParser(
    val screen: VtScreen,
    private val strict: Boolean = false,
) {
    private enum class State { GROUND, ESCAPE, CSI, OSC, OSC_ESC, CHARSET }

    private var state: State = State.GROUND
    private val params: StringBuilder = StringBuilder()
    private val oscBuffer: StringBuilder = StringBuilder()
    private var pendingHighSurrogate: Char? = null
    private var offset: Int = 0

    private val recordedOps: MutableList<VtOp> = mutableListOf()
    private val unknown: MutableList<String> = mutableListOf()

    /** Notable control sequences applied so far, in stream order. */
    val operations: List<VtOp> get() = recordedOps

    /** Escape sequences the parser did not recognise. */
    val unknownSequences: List<String> get() = unknown

    /** Total printable code points written. */
    var printedCells: Int = 0
        private set

    /** Count of erase-in-line operations, the dominant erase in Dispatch's output. */
    var eraseLineCount: Int = 0
        private set

    fun feed(text: CharSequence) {
        for (ch in text) {
            consume(ch)
            offset += 1
        }
    }

    private fun consume(ch: Char) {
        when (state) {
            State.GROUND -> ground(ch)
            State.ESCAPE -> escape(ch)
            State.CSI -> csi(ch)
            State.OSC -> osc(ch)
            State.OSC_ESC -> oscEscape(ch)
            State.CHARSET -> state = State.GROUND
        }
    }

    private fun ground(ch: Char) {
        when (ch) {
            ESC -> {
                state = State.ESCAPE
                params.setLength(0)
            }
            '\n' -> screen.lineFeed()
            '\r' -> screen.carriageReturn()
            '\b' -> screen.backspace()
            '\t' -> screen.tab()
            BEL -> Unit
            else -> {
                if (ch.code < 0x20 || ch.code == 0x7F) return
                printable(ch)
            }
        }
    }

    private fun printable(ch: Char) {
        val high = pendingHighSurrogate
        if (high != null) {
            pendingHighSurrogate = null
            if (Character.isLowSurrogate(ch)) {
                screen.print(Character.toCodePoint(high, ch))
                printedCells += 1
                return
            }
        }
        if (Character.isHighSurrogate(ch)) {
            pendingHighSurrogate = ch
            return
        }
        screen.print(ch.code)
        printedCells += 1
    }

    private fun escape(ch: Char) {
        when (ch) {
            '[' -> {
                state = State.CSI
                params.setLength(0)
            }
            ']' -> {
                state = State.OSC
                oscBuffer.setLength(0)
            }
            '(', ')', '*', '+' -> state = State.CHARSET
            '7', '8' -> state = State.GROUND // DECSC / DECRC — cursor save/restore, unused here
            'M' -> {
                screen.scrollDown(1)
                state = State.GROUND
            }
            'c' -> {
                screen.eraseInDisplay(2)
                screen.setPosition(0, 0)
                state = State.GROUND
            }
            else -> {
                record(VtOp.Kind.UNKNOWN, "ESC $ch")
                state = State.GROUND
            }
        }
    }

    private fun csi(ch: Char) {
        if (ch in ' '..'?') {
            params.append(ch)
            return
        }
        val raw = params.toString()
        params.setLength(0)
        state = State.GROUND
        dispatchCsi(raw, ch)
    }

    private fun dispatchCsi(raw: String, final: Char) {
        if (raw.startsWith("?")) {
            dispatchPrivate(raw.removePrefix("?"), final)
            return
        }
        val args = parseArgs(raw)
        when (final) {
            'H', 'f' -> screen.setPosition(arg(args, 0, 1) - 1, arg(args, 1, 1) - 1)
            'A' -> screen.moveUp(arg(args, 0, 1))
            'B' -> screen.moveDown(arg(args, 0, 1))
            'C' -> screen.moveRight(arg(args, 0, 1))
            'D' -> screen.moveLeft(arg(args, 0, 1))
            'E' -> { screen.moveDown(arg(args, 0, 1)); screen.setColumn(0) }
            'F' -> { screen.moveUp(arg(args, 0, 1)); screen.setColumn(0) }
            'G', '`' -> screen.setColumn(arg(args, 0, 1) - 1)
            'd' -> screen.setRow(arg(args, 0, 1) - 1)
            'J' -> {
                val mode = arg(args, 0, 0)
                screen.eraseInDisplay(mode)
                if (mode == 3) {
                    record(VtOp.Kind.ERASE_SCROLLBACK, "ESC[3J")
                } else {
                    record(VtOp.Kind.ERASE_DISPLAY, "ESC[${mode}J")
                }
            }
            'K' -> {
                val mode = arg(args, 0, 0)
                screen.eraseInLine(mode)
                eraseLineCount += 1
                record(VtOp.Kind.ERASE_LINE, "ESC[${mode}K")
            }
            'S' -> screen.scrollUp(arg(args, 0, 1))
            'T' -> screen.scrollDown(arg(args, 0, 1))
            'L' -> screen.insertLines(arg(args, 0, 1))
            'M' -> screen.deleteLines(arg(args, 0, 1))
            'm' -> screen.style = applySgr(screen.style, args)
            'r' -> {
                if (args.isEmpty()) {
                    screen.scrollRegion = null
                    record(VtOp.Kind.SCROLL_REGION_RESET, "ESC[r")
                } else {
                    val top = arg(args, 0, 1) - 1
                    val bottom = arg(args, 1, screen.height) - 1
                    screen.scrollRegion = top..bottom
                    record(VtOp.Kind.SCROLL_REGION_SET, "ESC[${top + 1};${bottom + 1}r")
                }
            }
            's', 'u', 'n', 'c', 'h', 'l', 't' -> Unit // save/restore, reports, mode sets: inert here
            else -> record(VtOp.Kind.UNKNOWN, "ESC[$raw$final")
        }
    }

    private fun dispatchPrivate(raw: String, final: Char) {
        val set = final == 'h'
        if (final != 'h' && final != 'l') {
            record(VtOp.Kind.UNKNOWN, "ESC[?$raw$final")
            return
        }
        for (mode in parseArgs(raw)) {
            when (mode) {
                7 -> screen.autoWrap = set
                25 -> {
                    screen.cursorVisible = set
                    record(if (set) VtOp.Kind.CURSOR_SHOW else VtOp.Kind.CURSOR_HIDE, "ESC[?25$final")
                }
                1049, 47, 1047 -> {
                    screen.alternateScreen = set
                    record(
                        if (set) VtOp.Kind.ALT_SCREEN_ENTER else VtOp.Kind.ALT_SCREEN_EXIT,
                        "ESC[?$mode$final",
                    )
                }
                2026 -> {
                    screen.synchronizedUpdate = set
                    record(if (set) VtOp.Kind.SYNC_BEGIN else VtOp.Kind.SYNC_END, "ESC[?2026$final")
                }
                else -> Unit
            }
        }
    }

    private fun osc(ch: Char) {
        when (ch) {
            BEL -> {
                record(VtOp.Kind.OSC, oscBuffer.toString())
                state = State.GROUND
            }
            ESC -> state = State.OSC_ESC
            else -> oscBuffer.append(ch)
        }
    }

    private fun oscEscape(ch: Char) {
        if (ch == '\\') {
            record(VtOp.Kind.OSC, oscBuffer.toString())
            state = State.GROUND
        } else {
            oscBuffer.append(ESC).append(ch)
            state = State.OSC
        }
    }

    private fun record(kind: VtOp.Kind, detail: String) {
        recordedOps += VtOp(kind, offset, detail)
        if (kind == VtOp.Kind.UNKNOWN) {
            unknown += detail
            check(!strict) { "Unrecognised escape sequence at offset $offset: ${detail.escaped()}" }
        }
    }

    private companion object {
        const val ESC = '\u001B'
        const val BEL = '\u0007'

        fun parseArgs(raw: String): List<Int> =
            if (raw.isEmpty()) {
                emptyList()
            } else {
                raw.split(';').map { it.toIntOrNull() ?: 0 }
            }

        fun arg(args: List<Int>, index: Int, fallback: Int): Int {
            val value = args.getOrNull(index) ?: return fallback
            return if (value == 0 && fallback != 0) fallback else value
        }

        fun String.escaped(): String = replace("\u001B", "ESC")

        /** Fold an SGR parameter list into [current]. */
        fun applySgr(current: CellStyle, args: List<Int>): CellStyle {
            if (args.isEmpty()) return CellStyle.DEFAULT
            var style = current
            var i = 0
            while (i < args.size) {
                when (val code = args[i]) {
                    0 -> style = CellStyle.DEFAULT
                    1 -> style = style.copy(bold = true)
                    2 -> style = style.copy(dim = true)
                    3 -> style = style.copy(italic = true)
                    4 -> style = style.copy(underline = true)
                    7 -> style = style.copy(inverse = true)
                    9 -> style = style.copy(strikethrough = true)
                    22 -> style = style.copy(bold = false, dim = false)
                    23 -> style = style.copy(italic = false)
                    24 -> style = style.copy(underline = false)
                    27 -> style = style.copy(inverse = false)
                    29 -> style = style.copy(strikethrough = false)
                    39 -> style = style.copy(foreground = null)
                    49 -> style = style.copy(background = null)
                    38, 48 -> {
                        val (value, consumed) = readExtendedColour(args, i)
                        style = if (code == 38) style.copy(foreground = value) else style.copy(background = value)
                        i += consumed
                    }
                    in 30..37, in 90..97 -> style = style.copy(foreground = code.toString())
                    in 40..47, in 100..107 -> style = style.copy(background = code.toString())
                    else -> Unit
                }
                i += 1
            }
            return style
        }

        /** Reads a `38;2;r;g;b` or `38;5;n` colour, returning the encoded value and extra args used. */
        fun readExtendedColour(args: List<Int>, index: Int): Pair<String?, Int> =
            when (args.getOrNull(index + 1)) {
                2 -> {
                    val r = args.getOrNull(index + 2) ?: 0
                    val g = args.getOrNull(index + 3) ?: 0
                    val b = args.getOrNull(index + 4) ?: 0
                    "rgb($r,$g,$b)" to 4
                }
                5 -> "idx(${args.getOrNull(index + 2) ?: 0})" to 2
                else -> null to 0
            }
    }
}
