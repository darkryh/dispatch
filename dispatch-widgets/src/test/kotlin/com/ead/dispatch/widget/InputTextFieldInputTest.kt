package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.DispatchConfig
import com.ead.dispatch.runtime.DispatchScope
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalDispatchScope
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.withComposer
import com.ead.dispatch.theme.DispatchTheme
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
        override fun content(block: @Dispatchable () -> Unit) = Unit

        fun sendKey(event: KeyboardEvent) {
            if (keyboardInterceptor.tryIntercept(event)) return
            keyHandler?.invoke(event)
        }
    }

    private class InputHarness {
        private val terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 80,
            height = 20,
            interactive = false,
        )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry = com.ead.dispatch.runtime.FocusRegistry()
        private val dispatchScope = TestDispatchScope(terminal, DispatchTheme.Dark, keyboardInterceptor)
        private val composer = Composer()

        var inputValue: String = ""
            private set
        var submitted: String? = null
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
                    InputTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        icon = "> ",
                        placeholder = "Type / for commands",
                        onSubmit = { submitted = it },
                    )
                }

                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return emptyList()
            focusRegistry.sync(rootNode)
            return rootNode.measure(Constraints.fixedWidth(80)).lines
        }

        fun press(key: String, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) {
            dispatchScope.sendKey(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
            render()
        }
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
