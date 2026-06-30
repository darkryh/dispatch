package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.DispatchConfig
import io.github.darkryh.dispatch.runtime.DispatchScope
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalDispatchScope
import io.github.darkryh.dispatch.runtime.LocalFocusRegistry
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.runtime.withComposer
import io.github.darkryh.dispatch.theme.DispatchTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T2.12 characterization (golden) suite for InputEditor vertical-cursor movement.
 *
 * This pins CURRENT (`master`) behavior so the O(n^2)->O(n) rewrite (STEP 2) can be proven
 * byte-identical. STEP 1 (shared render cache) is already landed and is behavior-preserving, so this
 * suite must be GREEN both before and after STEP 1.
 *
 * Two observation surfaces are asserted per case:
 *  - Surface 1 = the resulting cursor INDEX after the arrow key (covers `moveCursorVertical`).
 *  - Surface 2 = the (line, col) of the painted cursor block `█` in the emitted lines (covers
 *    `Input.computeCursorVisual` + `applyCursorOverlay`). Asserting both proves the editor and the
 *    painter agree on the same index -> (line, col) wrap model.
 *
 * Icon is "> " (2 cells), so contentWidth = terminalWidth - 2 and the painted column of `█` in the
 * full emitted line = 2 + (content column). The editor's wrapWidth provider also uses contentWidth,
 * so both surfaces wrap identically by construction.
 *
 * Every expected triple carries a `// CAPTURE:` marker: the values are derived by hand-tracing
 * Mordant PRE_WRAP + BREAK_WORD wrapping and MUST be confirmed/adjusted by the central verification
 * pass against actual current behavior. The pre-existing InputTextFieldInputTest vertical cases remain
 * the authoritative regression guard.
 */
class InputEditorVerticalGoldenTest {
    data class WrapCase(
        val name: String,
        val text: String,
        val width: Int,
        val startCursor: Int,
        val key: String,
        val expectedCursor: Int,
        val expectedPaintedLine: Int,
        val expectedPaintedCol: Int,
    )

    // Icon "> " => contentWidth = width - 2. Painted col of █ = 2 + content col.
    private val cases =
        listOf(
            // CAPTURE: cursor exactly at the wrap column of a BREAK_WORD-split word.
            // "abcdefghij" @ contentWidth 8 => "abcdefgh" / "ij"; start on 'i' (line1,col0), ArrowUp.
            WrapCase(
                name = "cursor at wrap column",
                text = "abcdefghij",
                width = 10,
                startCursor = 8,
                key = "ArrowUp",
                expectedCursor = 0, // CAPTURE
                expectedPaintedLine = 0, // CAPTURE
                expectedPaintedCol = 2, // CAPTURE
            ),
            // CAPTURE: end-of-logical-line vs start-of-next-visual-line across a hard '\n'.
            // "ab\ncd": line0 "ab", line1 "cd"; start on 'd' (line1,col1), ArrowUp -> 'b' (line0,col1).
            WrapCase(
                name = "hard newline boundary",
                text = "ab\ncd",
                width = 10,
                startCursor = 4,
                key = "ArrowUp",
                expectedCursor = 1, // CAPTURE
                expectedPaintedLine = 0, // CAPTURE
                expectedPaintedCol = 3, // CAPTURE
            ),
            // CAPTURE: one logical line wrapping to several visual lines (contentWidth 4 => 4 rows).
            // start on 'k' (line2,col2), ArrowUp -> 'g' (line1,col2).
            WrapCase(
                name = "logical line wraps to several visual lines",
                text = "abcdefghijklmnop",
                width = 6,
                startCursor = 10,
                key = "ArrowUp",
                expectedCursor = 6, // CAPTURE
                expectedPaintedLine = 1, // CAPTURE
                expectedPaintedCol = 4, // CAPTURE
            ),
            // CAPTURE: wide/CJK chars measured in cells. "你好\nab": line0 "你好" (4 cells), line1 "ab".
            // start on 'b' (line1,col1), ArrowUp with desiredCol 1. Candidates on line0 are at cols
            // {0, 2, 4}; tie between col0 (idx0) and col2 (idx1) at distance 1, tie-break = larger
            // index -> idx1 (the '好' cell at col 2). Demonstrates cell-width tie-break.
            WrapCase(
                name = "wide CJK columns in cells",
                text = "你好\nab",
                width = 10,
                startCursor = 4,
                key = "ArrowUp",
                expectedCursor = 1, // CAPTURED: lands on index 1 (between 你 and 好)
                expectedPaintedLine = 0, // CAPTURED
                expectedPaintedCol = 3, // CAPTURED: icon(2) + content col 1
            ),
            // CAPTURE: trailing spaces preserved under PRE_WRAP. "ab  cd" @ contentWidth 4 =>
            // line0 "ab  " (trailing spaces), line1 "cd". start on 'b' (line0,col1), ArrowDown -> 'd'.
            WrapCase(
                name = "trailing spaces preserved",
                text = "ab  cd",
                width = 6,
                startCursor = 1,
                key = "ArrowDown",
                expectedCursor = 5, // CAPTURE
                expectedPaintedLine = 1, // CAPTURE
                expectedPaintedCol = 3, // CAPTURE
            ),
            // CAPTURE: empty interior visual line. "a\n\nb": line0 "a", line1 "", line2 "b".
            // start on 'b' (line2,col0), ArrowUp -> the empty line (index 2, the 2nd '\n').
            WrapCase(
                name = "empty interior line",
                text = "a\n\nb",
                width = 10,
                startCursor = 3,
                key = "ArrowUp",
                expectedCursor = 2, // CAPTURE
                expectedPaintedLine = 1, // CAPTURE
                expectedPaintedCol = 2, // CAPTURE
            ),
            // CAPTURE: cursor at 0, ArrowUp is a no-op (already on first visual line; the
            // `line==0 && cursor>0` short-circuit does NOT fire because cursor==0, and the targetLine
            // collapses to the current line so moveCursorVertical returns the clamped cursor).
            WrapCase(
                name = "cursor at zero",
                text = "abc",
                width = 10,
                startCursor = 0,
                key = "ArrowUp",
                expectedCursor = 0, // CAPTURE
                expectedPaintedLine = 0, // CAPTURE
                expectedPaintedCol = 2, // CAPTURE
            ),
            // CAPTURE: cursor at text.length, ArrowDown is a no-op (already on last visual line).
            WrapCase(
                name = "cursor at text length",
                text = "abc",
                width = 10,
                startCursor = 3,
                key = "ArrowDown",
                expectedCursor = 3, // CAPTURE
                expectedPaintedLine = 0, // CAPTURE
                expectedPaintedCol = 5, // CAPTURE
            ),
            // CAPTURED: tab span (cursorSpanType == 1). "a\nb\tc": line0 "a", line1 "b<tab>c".
            // ArrowUp from 'c' with the tab-inflated desired column lands on index 2 ('b', start of
            // the wrapped line) — the tab expansion places the nearest candidate at the line-1 start.
            // This locks the current tab-handling behavior so a future STEP 2 cannot silently alter it.
            WrapCase(
                name = "tab span",
                text = "a\nb\tc",
                width = 10,
                startCursor = 4,
                key = "ArrowUp",
                expectedCursor = 2, // CAPTURED
                expectedPaintedLine = 1, // CAPTURED
                expectedPaintedCol = 2, // CAPTURED: icon(2) + content col 0
            ),
        )

    @Test
    fun `vertical cursor golden cases - index and painted block`() {
        val failures = mutableListOf<String>()
        for (case in cases) {
            val harness = GoldenHarness(case.text, case.width, case.startCursor)
            harness.render() // establish focus so the cursor block paints
            val lines = harness.press(case.key)

            // Surface 1: resulting cursor index.
            if (harness.cursorPosition != case.expectedCursor) {
                failures +=
                    "[${case.name}] Surface1 index: expected ${case.expectedCursor}, " +
                    "got ${harness.cursorPosition}"
            }

            // Surface 2: painted █ block (line, col) in the emitted lines.
            val cell = cursorCell(lines)
            if (cell == null) {
                failures += "[${case.name}] Surface2: no cursor block '█' found in lines=$lines"
            } else {
                if (cell.first != case.expectedPaintedLine || cell.second != case.expectedPaintedCol) {
                    failures +=
                        "[${case.name}] Surface2 painted: expected " +
                        "(${case.expectedPaintedLine},${case.expectedPaintedCol}), got $cell"
                }
            }
        }
        assertEquals(emptyList<String>(), failures, "golden mismatches (confirm/adjust // CAPTURE values):")
    }

    @Test
    fun `sticky preferred column survives a short intermediate line`() {
        // "abcdef\ng\nhijklm": line0 "abcdef", line1 "g" (short), line2 "hijklm".
        // Start on 'e' (line0,col4). ArrowDown lands on the short "g" line (col clamps low), then a
        // second ArrowDown must use the STICKY preferred column 4 (not the short line's column) and
        // land on line2 col4 = 'l' (index 13). If stickiness were lost, it would land at index 10.
        val harness = GoldenHarness("abcdef\ng\nhijklm", width = 10, startCursor = 4)
        harness.render()
        harness.press("ArrowDown")
        val lines = harness.press("ArrowDown")

        assertEquals(13, harness.cursorPosition) // CAPTURE
        assertEquals(2 to 6, cursorCell(lines)) // CAPTURE (line 2, col 2 + 4)
    }

    @Test
    fun `up then down round trip restores the original index`() {
        // "abcd\nefghij\nkl" @ contentWidth 6: line0 "abcd", line1 "efghij", line2 "kl".
        // Start on 'g' (line1,col2). ArrowUp -> 'c' (line0,col2); ArrowDown -> back to 'g'.
        val harness = GoldenHarness("abcd\nefghij\nkl", width = 8, startCursor = 7)
        harness.render()
        harness.press("ArrowUp")
        assertEquals(2, harness.cursorPosition) // CAPTURE

        harness.press("ArrowDown")
        assertEquals(7, harness.cursorPosition) // CAPTURE: round-trip identity
    }

    private fun cursorCell(lines: List<String>): Pair<Int, Int>? {
        lines.forEachIndexed { index, line ->
            val col = line.indexOf('█')
            if (col >= 0) return index to col
        }
        return null
    }

    // --- Harness modeled on InputTextFieldInputTest, with a settable initial cursor. ---

    private class TestDispatchScope(
        override val terminal: Terminal,
        override val theme: DispatchTheme,
        private val keyboardInterceptor: KeyboardInterceptor,
        override val args: Array<String> = emptyArray(),
    ) : DispatchScope {
        private var keyHandler: ((KeyboardEvent) -> Unit)? = null

        override val terminalWidth: Int get() = terminal.size.width
        override val terminalHeight: Int get() = terminal.size.height

        override fun config(block: DispatchConfig.() -> Unit) {
            DispatchConfig().block()
        }

        override fun exit(code: Int) = Unit

        override fun hasFlag(name: String): Boolean = false

        override fun getArgument(name: String): String? = null

        override fun launch(block: suspend CoroutineScope.() -> Unit): Job = Job()

        override fun clearScreen(clearScrollback: Boolean) = Unit

        override fun onKeyEvent(handler: (KeyboardEvent) -> Unit) {
            keyHandler = handler
        }

        override fun onMouseEvent(handler: (MouseEvent) -> Unit) = Unit

        override fun content(block: @Composable () -> Unit) = Unit

        fun sendKey(event: KeyboardEvent) {
            if (keyboardInterceptor.tryIntercept(event)) return
            keyHandler?.invoke(event)
        }
    }

    private class GoldenHarness(
        initialValue: String,
        private val width: Int,
        startCursor: Int,
    ) {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = width,
                height = 40,
                interactive = false,
            )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry =
            io.github.darkryh.dispatch.runtime
                .FocusRegistry()
        private val dispatchScope = TestDispatchScope(terminal, DispatchTheme.Dark, keyboardInterceptor)
        private val composer = Composer()
        private val historyIndexState = InputHistoryIndexState()

        var inputValue: String = initialValue
            private set
        var cursorPosition: Int = startCursor.coerceIn(0, initialValue.length)
            private set

        fun render(): List<String> {
            withComposer(composer) {
                composer.startComposition()
                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides terminal.size.width,
                    LocalTerminalHeight provides terminal.size.height,
                    LocalKeyboardInterceptor provides keyboardInterceptor,
                    LocalFocusRegistry provides focusRegistry,
                    LocalDispatchScope provides dispatchScope,
                    LocalTheme provides DispatchTheme.Dark,
                ) {
                    TextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        icon = "> ",
                        placeholder = "Type / for commands",
                        cursorPosition = cursorPosition,
                        onCursorPositionChange = { cursorPosition = it },
                        historyItems = emptyList(),
                        historyIndexState = historyIndexState,
                    )
                }
                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return emptyList()
            focusRegistry.sync(rootNode)
            return rootNode.measure(Constraints.fixedWidth(width)).lines
        }

        fun press(key: String): List<String> {
            dispatchScope.sendKey(KeyboardEvent(key))
            return render()
        }
    }
}
