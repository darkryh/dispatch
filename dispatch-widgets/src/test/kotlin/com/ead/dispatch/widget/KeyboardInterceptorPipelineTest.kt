package com.ead.dispatch.widget

import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.FocusRegistry
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.withComposer
import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardInterceptorPipelineTest {
    private class InputHarness(
        initialValue: String = "hello",
    ) {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 80,
                height = 20,
                interactive = false,
            )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry = FocusRegistry()
        private val composer = Composer()

        val state = TextFieldState(initialValue)

        fun render() {
            withComposer(composer) {
                composer.startComposition()

                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides terminal.size.width,
                    LocalTerminalHeight provides terminal.size.height,
                    LocalKeyboardInterceptor provides keyboardInterceptor,
                    LocalFocusRegistry provides focusRegistry,
                    LocalTheme provides DispatchTheme.Dark,
                ) {
                    InputTextField(
                        state = state,
                        icon = "> ",
                        placeholder = "Type / for commands",
                    )
                }

                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return
            focusRegistry.sync(rootNode)
            rootNode.measure(Constraints.fixedWidth(80))
        }

        fun press(
            key: String,
            ctrl: Boolean = false,
            alt: Boolean = false,
            shift: Boolean = false,
        ) {
            keyboardInterceptor.tryIntercept(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
            render()
        }

        fun registerInterceptor(
            priority: Int = 0,
            handler: (KeyboardEvent) -> Boolean,
        ): () -> Unit = keyboardInterceptor.register(priority, handler)
    }

    @Test
    fun `interceptor consumes key before input field`() {
        val harness = InputHarness("hello")
        var consumedCount = 0

        harness.render()
        assertEquals(5, harness.state.cursorPosition)

        val dispose =
            harness.registerInterceptor(priority = 1) { event ->
                if (event.key == "ArrowLeft") {
                    consumedCount += 1
                    true
                } else {
                    false
                }
            }

        harness.press("ArrowLeft")
        assertEquals(1, consumedCount)
        assertEquals(5, harness.state.cursorPosition)

        harness.press("x")
        assertEquals("hellox", harness.state.value)
        assertEquals(6, harness.state.cursorPosition)

        dispose()
    }

    @Test
    fun `shortcut interceptor does not block text input`() {
        val harness = InputHarness("")
        var shortcutCount = 0

        harness.render()
        assertEquals(0, harness.state.cursorPosition)

        val dispose =
            harness.registerInterceptor(priority = 1) { event ->
                if (event.key == "Tab" && event.shift) {
                    shortcutCount += 1
                    true
                } else {
                    false
                }
            }

        harness.press("a")
        assertEquals("a", harness.state.value)
        assertEquals(1, harness.state.cursorPosition)

        harness.press("Tab", shift = true)
        assertEquals(1, shortcutCount)
        assertEquals("a", harness.state.value)
        assertEquals(1, harness.state.cursorPosition)

        harness.press("b")
        assertEquals("ab", harness.state.value)
        assertEquals(2, harness.state.cursorPosition)

        dispose()
    }
}
