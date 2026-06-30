package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.FocusRegistry
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalFocusRegistry
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.runtime.withComposer
import io.github.darkryh.dispatch.theme.DispatchTheme
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
                    TextField(
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
