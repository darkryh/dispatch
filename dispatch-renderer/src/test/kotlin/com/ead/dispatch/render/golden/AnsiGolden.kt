package com.ead.dispatch.render

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder

/**
 * Shared helpers for the P0.1 golden ANSI byte-stream regression suite.
 *
 * Every expected string in the golden tests is built from [AnsiCodes] constants (never a hardcoded
 * escape literal) so the goldens stay correct *by construction* if a code is ever re-spelled.
 *
 * Determinism note: the only source of non-determinism in [TerminalRenderer] is the
 * `DISPATCH_DIAGNOSTICS_FILE` env var, which `RenderDiagnostics` reads at class-init. When it is
 * unset (the production default) `flushBuffer` short-circuits the diagnostics map and emits the
 * *identical* bytes either way — diagnostics write to a file sidecar, never to the terminal — but
 * the golden tests still assert `!RenderDiagnostics.isEnabled` in `@BeforeTest` to document and
 * pin the assumption. We deliberately do NOT set the env var here.
 */
object AnsiGolden {
    /** Carriage return emitted before every cleared/redrawn active-area or viewport line. */
    const val CR: String = "\r"

    /** Line feed used by both scrollback commits and active-area row separators. */
    const val LF: String = "\n"

    /** Convenience alias for the clear-line code (the single most repeated token). */
    val CL: String get() = AnsiCodes.CLEAR_LINE

    /**
     * The same recorder wiring used by `TerminalRendererTest` /
     * `ViewportTransitionCleanupReliabilityTest`: an 80x24 TRUECOLOR [TerminalRecorder] behind a
     * Mordant [Terminal]. `recorder.output()` is cumulative, so per-call deltas come from [delta].
     */
    fun recordingRenderer(
        width: Int = 80,
        height: Int = 24,
    ): Pair<TerminalRenderer, TerminalRecorder> {
        val recorder =
            TerminalRecorder(
                ansiLevel = AnsiLevel.TRUECOLOR,
                width = width,
                height = height,
                supportsAnsiCursor = true,
            )
        val terminal = Terminal(terminalInterface = recorder)
        return TerminalRenderer(terminal) to recorder
    }

    /**
     * The per-call delta: the established cumulative-output pattern
     * (`recorder.output().removePrefix(before)`), encoded once so no test gets it wrong.
     */
    fun delta(
        recorder: TerminalRecorder,
        before: String,
    ): String = recorder.output().removePrefix(before)

    /**
     * The block emitted by `clearViewportRowsAfter`: `moveTo(row,1) + CLEAR_LINE` for each row in
     * `fromRow..toRow`. `AnsiCodes.appendMoveTo` produces the byte-identical sequence to
     * `AnsiCodes.moveTo`, so building the expectation from `moveTo` matches the real output exactly.
     */
    fun trailingRowClears(
        fromRow: Int,
        toRow: Int,
    ): String =
        buildString {
            for (row in fromRow..toRow) {
                append(AnsiCodes.moveTo(row, 1))
                append(AnsiCodes.CLEAR_LINE)
            }
        }

    /**
     * Map an ANSI byte stream to readable tokens for human-legible assertion messages.
     *
     * Tokens: `<ESC>`, `<CR>`, `<LF>`, `<CLEAR_LINE>`, `<CLEAR_SCROLLBACK>`, `<CLEAR_SCREEN>`,
     * `<CURSOR_HOME>`, `<MOVE r=.. c=..>`, `<UP n>`.
     */
    fun visualizeEscapes(text: String): String {
        var out = text
        // Specific multi-char CSI codes first so the generic <ESC> fallback never eats them.
        out = out.replace(AnsiCodes.CLEAR_SCROLLBACK, "<CLEAR_SCROLLBACK>")
        out = out.replace(AnsiCodes.CLEAR_SCREEN, "<CLEAR_SCREEN>")
        out = out.replace(AnsiCodes.CLEAR_LINE, "<CLEAR_LINE>")
        out = out.replace(AnsiCodes.CURSOR_HOME, "<CURSOR_HOME>")
        out =
            Regex("\\[(\\d+);(\\d+)H").replace(out) { m ->
                "<MOVE r=${m.groupValues[1]} c=${m.groupValues[2]}>"
            }
        out = Regex("\\[(\\d+)A").replace(out) { m -> "<UP ${m.groupValues[1]}>" }
        out = out.replace("", "<ESC>")
        out = out.replace("\r", "<CR>")
        out = out.replace("\n", "<LF>")
        return out
    }
}
