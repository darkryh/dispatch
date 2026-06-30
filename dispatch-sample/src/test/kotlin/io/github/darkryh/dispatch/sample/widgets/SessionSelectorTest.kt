package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.DispatchConfig
import io.github.darkryh.dispatch.runtime.DispatchScope
import io.github.darkryh.dispatch.runtime.LocalDispatchScope
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.runtime.withComposer
import io.github.darkryh.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionSelectorTest {
    private class TestDispatchScope(
        override val terminal: Terminal,
        override val theme: DispatchTheme,
        private val keyboardInterceptor: io.github.darkryh.dispatch.runtime.KeyboardInterceptor,
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

    private class SelectorHarness(
        options: List<SessionOption<String>>,
        private val visibleCount: Int = 10,
    ) {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 80,
                height = 20,
                interactive = false,
            )
        private val keyboardInterceptor =
            io.github.darkryh.dispatch.runtime
                .KeyboardInterceptor()
        private val dispatchScope = TestDispatchScope(terminal, DispatchTheme.Dark, keyboardInterceptor)
        private val composer = Composer()

        var options: List<SessionOption<String>> = options
        var state = SessionSelectorState<String>()
        var selected: SessionOption<String>? = null
            private set
        var exitCount = 0
            private set

        private val plainStyles =
            SessionSelectorTextStyles(
                prefix = null,
                selectedPrefix = null,
                updatedTime = null,
                selectedUpdatedTime = null,
                conversationId = null,
                selectedConversationId = null,
                title = null,
                selectedTitle = null,
                messageCount = null,
                selectedMessageCount = null,
                noResultsText = null,
                filterPrompt = null,
                header = null,
            )

        fun render(): List<String> {
            withComposer(composer) {
                composer.startComposition()

                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides terminal.size.width,
                    LocalTerminalHeight provides terminal.size.height,
                    io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor provides keyboardInterceptor,
                    LocalDispatchScope provides dispatchScope,
                    LocalTheme provides DispatchTheme.Dark,
                ) {
                    SessionSelector(
                        options = options,
                        onOptionSelected = { selected = it },
                        onExit = { exitCount += 1 },
                        textStyles = plainStyles,
                        showHeaders = false,
                        visibleCount = visibleCount,
                        state = state,
                    )
                }

                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return emptyList()
            return rootNode.measure(Constraints.fixedWidth(80)).lines
        }

        fun press(key: String) {
            dispatchScope.sendKey(KeyboardEvent(key))
            render()
        }
    }

    private fun sampleOptions(): List<SessionOption<String>> =
        listOf(
            SessionOption(
                id = "s1",
                title = "Alpha",
                updatedTime = "1d",
                conversationId = "conv-1",
                messageCount = 1,
                data = "alpha",
            ),
            SessionOption(
                id = "s2",
                title = "Beta",
                updatedTime = "2d",
                conversationId = "conv-2",
                messageCount = 2,
                data = "beta",
            ),
        )

    @Test
    fun `keyboard navigation updates selection index`() {
        val harness = SelectorHarness(sampleOptions())

        harness.render()
        assertEquals(0, harness.state.selectedIndex)

        harness.press("ArrowDown")
        assertEquals(1, harness.state.selectedIndex)

        harness.press("ArrowDown")
        assertEquals(1, harness.state.selectedIndex)

        harness.press("ArrowUp")
        assertEquals(0, harness.state.selectedIndex)
    }

    @Test
    fun `enter selects option and escape exits`() {
        val harness = SelectorHarness(sampleOptions())

        harness.render()
        harness.press("ArrowDown")
        harness.press("Enter")

        assertNotNull(harness.selected)
        assertEquals("beta", harness.selected?.data)

        harness.press("Escape")
        assertEquals(1, harness.exitCount)
    }

    @Test
    fun `filter input narrows options and backspace clears`() {
        val harness = SelectorHarness(sampleOptions())

        harness.render()
        harness.press("b")

        assertEquals("b", harness.state.filterText)
        assertEquals(1, harness.state.filteredOptions.size)
        assertEquals(
            "Beta",
            harness.state.filteredOptions
                .first()
                .title,
        )

        harness.press("Backspace")

        assertEquals("", harness.state.filterText)
        assertEquals(2, harness.state.filteredOptions.size)
    }

    @Test
    fun `selector uses latest state instance`() {
        val harness = SelectorHarness(sampleOptions())

        harness.render()

        val previousState = harness.state
        val newState = SessionSelectorState<String>()
        harness.state = newState

        harness.render()
        harness.press("ArrowDown")

        assertEquals(0, previousState.selectedIndex)
        assertEquals(1, newState.selectedIndex)
    }

    @Test
    fun `selection clamps when options shrink`() {
        val harness = SelectorHarness(sampleOptions())

        harness.render()
        harness.press("ArrowDown")

        assertEquals(1, harness.state.selectedIndex)

        harness.options = listOf(sampleOptions().first())
        harness.render()

        assertEquals(0, harness.state.selectedIndex)
    }

    @Test
    fun `visibleCount zero does not crash`() {
        val harness = SelectorHarness(sampleOptions(), visibleCount = 0)

        val lines = harness.render()

        assertEquals(1, lines.size)
        assertNotNull(lines.firstOrNull { it.contains("Alpha") })
    }

    @Test
    fun `visibleCount negative does not crash`() {
        val harness = SelectorHarness(sampleOptions(), visibleCount = -2)

        val lines = harness.render()

        assertEquals(1, lines.size)
        assertNotNull(lines.firstOrNull { it.contains("Alpha") })
    }
}
