package io.github.darkryh.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T2.12 render-budget gate.
 *
 * Counts the number of REAL `terminal.render` calls the vertical-cursor measurer performs for a
 * single ArrowUp on long wrapped text. Mordant's `Terminal` (and `Terminal.render`) are `final`, so
 * the count is captured through the behavior-neutral [inputVerticalRenderObserver] seam in
 * InputEditor.kt (mirrors the existing [inputNowNanos] seam), which fires once per actual render
 * (cache miss) inside the editor's measurement path only.
 *
 * - [render call count scales linearly with text length] is the characterization gate: it is GREEN
 *   on `master` (the per-position scan renders ~n distinct positions, each cached, so calls are O(n)
 *   — NOT O(n^2) calls — but they are clearly NOT constant). It proves both that the counting seam
 *   works and that the current cost grows with n.
 * - [render call count is constant after STEP 2] is the STEP-2 acceptance gate: it asserts a constant
 *   render budget (the hybrid does exactly 3 renders regardless of n). It is RED on the pre-STEP-2
 *   code (~n renders) and GREEN only after STEP 2 lands. It is @Ignore'd for now so the suite stays
 *   green while STEP 2 is deferred; un-ignore it together with the STEP 2 implementation.
 */
class InputEditorRenderBudgetTest {
    private val previousObserver = inputVerticalRenderObserver

    @AfterTest
    fun tearDown() {
        inputVerticalRenderObserver = previousObserver
    }

    private class CountingHarness(
        text: String,
        cursor: Int,
        private val contentWidth: Int,
    ) {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = contentWidth + 2,
                height = 40,
                interactive = false,
            )
        var value: String = text
        var cursorPos: Int = cursor

        private val editor =
            InputEditor(
                getValue = { value },
                setValue = { value = it },
                getCursor = { cursorPos },
                setCursor = { cursorPos = it },
                historyIndexState = InputHistoryIndexState(),
                pasteTracker = PasteTracker(),
                pasteHeuristic = PasteHeuristic(inputNowNanos),
            )

        init {
            editor.updateDependencies(
                onValueChange = { value = it },
                onSubmit = {},
                onCursorPositionChange = { cursorPos = it },
                historyItems = { emptyList() },
                terminal = terminal,
                contentWidth = { contentWidth },
            )
        }

        fun press(key: String) {
            editor.handleKeyEvent(KeyboardEvent(key))
        }
    }

    private fun countRendersForArrowUp(textLength: Int): Int {
        val contentWidth = 40
        // A single long word wrapped by BREAK_WORD into several visual lines; cursor parked on an
        // interior visual line so moveCursorVertical actually runs its candidate scan.
        val harness = CountingHarness("a".repeat(textLength), cursor = textLength / 2, contentWidth = contentWidth)
        var renders = 0
        inputVerticalRenderObserver = { renders += 1 }
        harness.press("ArrowUp")
        return renders
    }

    @Test
    fun `render call count scales linearly with text length`() {
        val n = 200
        val renders = countRendersForArrowUp(n)

        // Far above any constant budget: proves the seam counts and the cost is NOT constant today.
        assertTrue(renders > 50, "expected the per-position scan to render many times, got $renders")
        // Calls are linear (cached per position), NOT quadratic in calls.
        assertTrue(renders <= 3 * (n + 1), "render calls should be O(n), got $renders for n=$n")
    }

    @Ignore("T2.12 STEP 2 deferred: RED until the O(n)->O(1)-renders hybrid lands in moveCursorVertical")
    @Test
    fun `render call count is constant after STEP 2`() {
        val renders = countRendersForArrowUp(200)
        // STEP 2 target: current(line,col) + maxLine + one no-marker full render + one re-validation,
        // i.e. a small constant independent of n.
        assertTrue(renders <= 8, "post-STEP-2 budget exceeded: $renders renders for one ArrowUp")
    }
}
