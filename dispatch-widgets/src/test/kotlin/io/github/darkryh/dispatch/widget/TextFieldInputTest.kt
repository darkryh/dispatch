package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputTextFieldInputTest {
    private class FakeClock {
        var nowNanos: Long = 0L

        fun advanceMillis(millis: Long) {
            nowNanos += millis * 1_000_000
        }
    }

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

    private class InputHarness(
        initialValue: String = "",
        private val width: Int = 80,
        private val historyItems: List<String> = emptyList(),
        private val maskChar: Char? = null,
    ) {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = width,
                height = 20,
                interactive = false,
            )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry =
            io.github.darkryh.dispatch.runtime
                .FocusRegistry()
        private val dispatchScope = TestDispatchScope(terminal, DispatchTheme.Dark, keyboardInterceptor)
        private val composer = Composer()

        var inputValue: String = initialValue
            private set
        var cursorPosition: Int = initialValue.length
            private set
        var submitted: String? = null
            private set
        var delayValueUpdates: Boolean = false
        private val pendingValues = mutableListOf<String>()
        private val historyIndexState = InputHistoryIndexState()

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
                        onValueChange = {
                            pendingValues += it
                            if (!delayValueUpdates) inputValue = it
                        },
                        icon = "> ",
                        placeholder = "Type / for commands",
                        onSubmit = { submitted = it },
                        cursorPosition = cursorPosition,
                        onCursorPositionChange = { cursorPosition = it },
                        historyItems = historyItems,
                        historyIndexState = historyIndexState,
                        maskChar = maskChar,
                    )
                }

                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return emptyList()
            focusRegistry.sync(rootNode)
            return rootNode.measure(Constraints.fixedWidth(width)).lines
        }

        fun press(
            key: String,
            ctrl: Boolean = false,
            alt: Boolean = false,
            shift: Boolean = false,
        ) {
            dispatchScope.sendKey(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
            render()
        }

        fun pressWithoutRender(key: String) {
            dispatchScope.sendKey(KeyboardEvent(key))
        }

        fun applyPendingValue(index: Int) {
            inputValue = pendingValues[index]
        }

        fun latestPendingValue(): String = pendingValues.last()
    }

    @Test
    fun `typing travels through focus and keyboard pipeline and renders cursor`() {
        val harness = InputHarness()

        harness.render()
        harness.render()
        val lines =
            buildList {
                harness.press("h")
                harness.press("i")
                addAll(harness.render())
            }

        assertEquals("hi", harness.inputValue)
        assertEquals(2, harness.cursorPosition)
        assertEquals(true, lines.any { it.contains("> hi█") })
    }

    @Test
    fun `masked input edits raw value without rendering the secret`() {
        val harness = InputHarness(maskChar = '*')

        harness.render()
        harness.press("s")
        harness.press("e")
        val lines = harness.render()

        assertEquals("se", harness.inputValue)
        assertEquals(true, lines.any { it.contains("> **█") })
        assertEquals(false, lines.any { it.contains("se") })
    }

    @Test
    fun `stale acknowledged value does not move cursor behind newer local edits`() {
        val harness = InputHarness()

        harness.render()
        harness.delayValueUpdates = true
        harness.pressWithoutRender("v")
        harness.pressWithoutRender("l")

        harness.applyPendingValue(0)
        harness.render()
        harness.pressWithoutRender("a")

        assertEquals("vla", harness.latestPendingValue())
    }

    @Test
    fun `up at first visual line reaches start then previous history record`() {
        val harness =
            InputHarness(
                initialValue = "draft line",
                historyItems = listOf("older", "newer"),
            )

        harness.render()
        repeat(5) { harness.press("ArrowLeft") }
        harness.press("ArrowUp")
        assertEquals(0, harness.cursorPosition)
        assertEquals("draft line", harness.inputValue)

        harness.press("ArrowUp")
        assertEquals("newer", harness.inputValue)
        assertEquals(5, harness.cursorPosition)
    }

    @Test
    fun `down at last visual line reaches end then next history record`() {
        val harness =
            InputHarness(
                initialValue = "draft",
                historyItems = listOf("older", "newer"),
            )

        harness.render()
        repeat(5) { harness.press("ArrowLeft") }
        harness.press("ArrowUp")
        harness.press("ArrowUp")
        assertEquals("newer", harness.inputValue)

        repeat(3) { harness.press("ArrowLeft") }
        harness.press("ArrowDown")
        assertEquals(5, harness.cursorPosition)
        assertEquals("newer", harness.inputValue)

        harness.press("ArrowDown")
        assertEquals("draft", harness.inputValue)
        assertEquals(5, harness.cursorPosition)
    }

    @Test
    fun `vertical movement preserves visual column across wrapped lines`() {
        val harness =
            InputHarness(
                initialValue = "abcd\nefghij\nkl",
                width = 8,
            )

        harness.render()
        repeat(4) { harness.press("ArrowLeft") }
        val middlePosition = harness.cursorPosition
        harness.press("ArrowUp")
        val upperPosition = harness.cursorPosition
        harness.press("ArrowDown")

        assertEquals(middlePosition, harness.cursorPosition)
        assertEquals(true, upperPosition < middlePosition)
    }

    @Test
    fun `emoji key inserts text`() {
        val harness = InputHarness()

        harness.render()
        harness.press("\uD83D\uDE00")

        assertEquals("\uD83D\uDE00", harness.inputValue)
    }

    @Test
    fun `paste mode inserts newline without submit`() {
        val harness = InputHarness()

        harness.render()
        harness.press("PasteStart")
        harness.press("h")
        harness.press("i")
        harness.press("Enter")
        harness.press("t")
        harness.press("h")
        harness.press("e")
        harness.press("r")
        harness.press("e")
        harness.press("PasteEnd")

        assertEquals("hi\nthere", harness.inputValue)
        assertNull(harness.submitted)

        harness.press("Enter")

        assertEquals("hi\nthere", harness.submitted)
        assertEquals("", harness.inputValue)
    }

    @Test
    fun `pasted newline text inserts without submit`() {
        val harness = InputHarness()

        harness.render()
        harness.press("hello\r\nworld")

        assertEquals("hello\nworld", harness.inputValue)
        assertNull(harness.submitted)
    }

    @Test
    fun `paste inserts at cursor position`() {
        val harness = InputHarness()

        harness.render()
        harness.press("h")
        harness.press("i")
        harness.press("ArrowLeft")
        harness.press("PASTE")

        assertEquals("hPASTEi", harness.inputValue)
    }

    @Test
    fun `paste multiline inserts at cursor position`() {
        val harness = InputHarness()

        harness.render()
        harness.press("a")
        harness.press("b")
        harness.press("ArrowLeft")
        harness.press("1\n2")

        assertEquals("a1\n2b", harness.inputValue)
    }

    @Test
    fun `paste with tabs preserves tabs`() {
        val harness = InputHarness()

        harness.render()
        harness.press("a\tb")

        assertEquals("a\tb", harness.inputValue)
    }

    @Test
    fun `paste burst enter inserts newline without submit`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = InputHarness()

            harness.render()
            harness.press("h")
            clock.advanceMillis(1)
            harness.press("i")
            clock.advanceMillis(1)
            harness.press("Enter")
            clock.advanceMillis(1)
            harness.press("t")
            clock.advanceMillis(1)
            harness.press("h")
            clock.advanceMillis(1)
            harness.press("e")
            clock.advanceMillis(1)
            harness.press("r")
            clock.advanceMillis(1)
            harness.press("e")

            assertEquals("hi\nthere", harness.inputValue)
            assertNull(harness.submitted)

            clock.advanceMillis(650)
            harness.press("Enter")

            assertEquals("hi\nthere", harness.submitted)
            assertEquals("", harness.inputValue)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `multiline paste suppresses next enter briefly`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = InputHarness()

            harness.render()
            harness.press("hello\nworld")
            clock.advanceMillis(50)
            harness.press("Enter")

            assertEquals("hello\nworld\n", harness.inputValue)
            assertNull(harness.submitted)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `multiline paste enter submits after suppression window`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = InputHarness()

            harness.render()
            harness.press("hello\nworld")
            clock.advanceMillis(650)
            harness.press("Enter")

            assertEquals("hello\nworld", harness.submitted)
            assertEquals("", harness.inputValue)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `single character then enter submits`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = InputHarness()

            harness.render()
            harness.press("h")
            clock.advanceMillis(200)
            harness.press("Enter")

            assertEquals("h", harness.submitted)
            assertEquals("", harness.inputValue)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `tab key does not insert literal text`() {
        val harness = InputHarness()

        harness.render()
        harness.press("Tab")

        assertEquals("", harness.inputValue)
    }

    @Test
    fun `insert clamps cursor beyond length`() {
        val result = applyInsertion("hello", 10, "!")

        assertEquals("hello!", result.value)
        assertEquals(6, result.cursorPosition)
    }
}
