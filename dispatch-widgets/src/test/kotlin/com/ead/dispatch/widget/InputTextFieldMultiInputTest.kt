package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Column
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

class InputTextFieldMultiInputTest {
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

    private class MultiInputHarness {
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

        var firstValue: String = ""
            private set
        var secondValue: String = ""
            private set

        var firstEnabled: Boolean = true
        var secondEnabled: Boolean = false

        fun render() {
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
                    Column {
                        InputTextField(
                            value = firstValue,
                            onValueChange = { firstValue = it },
                            icon = "> ",
                            placeholder = "First",
                            enabled = firstEnabled,
                        )
                        InputTextField(
                            value = secondValue,
                            onValueChange = { secondValue = it },
                            icon = "> ",
                            placeholder = "Second",
                            enabled = secondEnabled,
                        )
                    }
                }

                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return
            focusRegistry.sync(rootNode)
            rootNode.measure(Constraints.fixedWidth(80))
        }

        fun press(key: String, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) {
            dispatchScope.sendKey(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
            render()
        }
    }

    @Test
    fun `only enabled field receives input`() {
        val harness = MultiInputHarness()

        harness.render()
        harness.press("h")
        harness.press("i")

        assertEquals("hi", harness.firstValue)
        assertEquals("", harness.secondValue)
    }

    @Test
    fun `input follows enabled field when focus changes`() {
        val harness = MultiInputHarness()

        harness.render()
        harness.press("a")

        harness.firstEnabled = false
        harness.secondEnabled = true
        harness.render()

        harness.press("b")

        assertEquals("a", harness.firstValue)
        assertEquals("b", harness.secondValue)
    }
}
