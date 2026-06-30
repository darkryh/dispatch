package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.CompositionLocalProvider
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.withComposer
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionPromptTest {
    private data class PromptHarness(
        val terminal: Terminal,
        val keyboard: KeyboardInterceptor,
        val state: DecisionPromptState,
        val submissions: MutableList<DecisionSelection>,
        var options: List<DecisionOption>,
        val composer: Composer,
    )

    private val plainStyles =
        DecisionPromptTextStyles(
            question = null,
            option = null,
            selectedOption = null,
            prefix = null,
            selectedPrefix = null,
            placeholder = null,
            customText = null,
        )

    private fun createHarness(
        options: List<DecisionOption> = defaultOptions(),
        state: DecisionPromptState = DecisionPromptState(),
    ): PromptHarness =
        PromptHarness(
            terminal =
                Terminal(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 20,
                    interactive = false,
                ),
            keyboard = KeyboardInterceptor(),
            state = state,
            submissions = mutableListOf(),
            options = options,
            composer = Composer(),
        )

    private fun render(harness: PromptHarness): List<String> {
        withComposer(harness.composer) {
            harness.composer.startComposition()
            CompositionLocalProvider(
                LocalTerminal provides harness.terminal,
                LocalTerminalWidth provides harness.terminal.size.width,
                LocalTerminalHeight provides harness.terminal.size.height,
                LocalKeyboardInterceptor provides harness.keyboard,
            ) {
                DecisionPrompt(
                    question = "Which relationship outcome do you want?",
                    options = harness.options,
                    state = harness.state,
                    placeholder = "tell the assistant how it should proceed...",
                    textStyles = plainStyles,
                    onSubmit = { harness.submissions += it },
                )
            }
            harness.composer.endComposition()
        }
        return harness.composer
            .getRootNode()
            ?.measure(Constraints.fixedWidth(80))
            ?.lines
            ?: emptyList()
    }

    private fun press(
        harness: PromptHarness,
        key: String,
    ) {
        harness.keyboard.tryIntercept(KeyboardEvent(key))
    }

    @Test
    fun `enter on option emits selected option`() {
        val harness = createHarness()
        render(harness)

        press(harness, "Enter")

        assertEquals(1, harness.submissions.size)
        assertEquals(
            DecisionSelection.Option(DecisionOption("Keep existing")),
            harness.submissions.first(),
        )
    }

    @Test
    fun `typing only works on custom row and enter submits custom`() {
        val harness = createHarness()
        render(harness)

        press(harness, "x")
        assertEquals("", harness.state.customText)

        press(harness, "ArrowDown")
        press(harness, "ArrowDown")
        press(harness, "ArrowDown")
        assertEquals(3, harness.state.selectedIndex)

        press(harness, "H")
        press(harness, "i")
        assertEquals("Hi", harness.state.customText)

        press(harness, "Enter")

        assertEquals(1, harness.submissions.size)
        assertEquals(
            DecisionSelection.Custom("Hi"),
            harness.submissions.first(),
        )
        assertEquals("", harness.state.customText)
    }

    @Test
    fun `placeholder is rendered for empty custom row`() {
        val harness = createHarness()
        val lines = render(harness)

        assertTrue(lines.any { it.contains("tell the assistant how it should proceed...") })
    }

    @Test
    fun `selection is clamped when options shrink`() {
        val state = DecisionPromptState(initialSelectedIndex = 3)
        val harness = createHarness(state = state)
        render(harness)

        harness.options =
            listOf(
                DecisionOption("Keep existing"),
                DecisionOption("Replace with new"),
            )
        render(harness)

        // Two options => custom row index is 2.
        assertEquals(2, harness.state.selectedIndex)
    }

    @Test
    fun `tab jumps directly to custom row`() {
        val harness = createHarness()
        render(harness)

        press(harness, "Tab")

        assertEquals(3, harness.state.selectedIndex)
    }

    @Test
    fun `marker uses letters first then numbers`() {
        assertEquals("a", decisionOptionMarker(0))
        assertEquals("c", decisionOptionMarker(2))
        assertEquals("z", decisionOptionMarker(25))
        assertEquals("27", decisionOptionMarker(26))
    }

    @Test
    fun `clamp helper keeps index within option plus custom row`() {
        assertEquals(0, clampDecisionSelectionIndex(-1, 3))
        assertEquals(3, clampDecisionSelectionIndex(99, 3))
        assertEquals(2, clampDecisionSelectionIndex(2, 3))
    }

    private fun defaultOptions(): List<DecisionOption> =
        listOf(
            DecisionOption("Keep existing"),
            DecisionOption("Replace with new"),
            DecisionOption("Merge both"),
        )
}
