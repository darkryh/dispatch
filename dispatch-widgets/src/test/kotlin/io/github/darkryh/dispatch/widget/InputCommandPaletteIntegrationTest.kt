package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Column
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputCommandPaletteIntegrationTest {
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

    private class InputPaletteHarness {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 80,
                height = 20,
                interactive = false,
            )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry =
            io.github.darkryh.dispatch.runtime
                .FocusRegistry()
        private val dispatchScope = TestDispatchScope(terminal, DispatchTheme.Dark, keyboardInterceptor)
        private val composer = Composer()

        val paletteState = CommandPaletteState<String>()
        val options =
            listOf(
                CommandOption(label = "model", description = "desc", data = "model"),
                CommandOption(label = "help", description = "desc", data = "help"),
            )
        var inputValue: String = ""
            private set
        var selected: CommandOption<String>? = null
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
                    Column {
                        TextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            icon = "> ",
                            placeholder = "Type / for commands",
                        )
                        CommandPalette(
                            options = options,
                            inputValue = inputValue,
                            onOptionSelected = { selected = it },
                            onInputTransform = { inputValue = it },
                            textStyles =
                                CommandPaletteTextStyles(
                                    prefix = null,
                                    selectedPrefix = null,
                                    label = null,
                                    selectedLabel = null,
                                    description = null,
                                    selectedDescription = null,
                                    disabledLabel = null,
                                    noResultsText = null,
                                ),
                            state = paletteState,
                        )
                    }
                }

                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return emptyList()
            focusRegistry.sync(rootNode)
            return rootNode.measure(Constraints.fixedWidth(80)).lines
        }

        fun press(key: String) {
            dispatchScope.sendKey(KeyboardEvent(key))
            render()
        }
    }

    @Test
    fun `typing slash opens palette and arrow navigates`() {
        val harness = InputPaletteHarness()

        harness.render()
        harness.press("/")

        assertTrue(harness.paletteState.isVisible)
        assertEquals("/", harness.inputValue)

        harness.press("ArrowDown")

        assertEquals(1, harness.paletteState.selectedIndex)
        assertEquals("/", harness.inputValue)
    }

    @Test
    fun `typing slash after text does not open palette`() {
        val harness = InputPaletteHarness()

        harness.render()
        harness.press("h")
        harness.press("i")
        harness.press("/")

        assertFalse(harness.paletteState.isVisible)
        assertEquals("hi/", harness.inputValue)
    }

    @Test
    fun `enter selects command and clears input`() {
        val harness = InputPaletteHarness()

        harness.render()
        harness.press("/")
        harness.press("h")

        assertTrue(harness.paletteState.isVisible)

        harness.press("Enter")

        assertEquals("help", harness.selected?.data)
        assertEquals("", harness.inputValue)
        assertFalse(harness.paletteState.isVisible)
    }

    @Test
    fun `escape resets selection and preserves input`() {
        val harness = InputPaletteHarness()

        harness.render()
        harness.press("/")
        harness.press("ArrowDown")
        harness.press("Escape")

        assertEquals(0, harness.paletteState.selectedIndex)
        assertEquals("/", harness.inputValue)
    }

    @Test
    fun `tab completes current selection into input`() {
        val harness = InputPaletteHarness()

        harness.render()
        harness.press("/")
        harness.press("ArrowDown")
        harness.press("Tab")

        assertEquals("/help ", harness.inputValue)
        assertFalse(harness.paletteState.isVisible)
        assertEquals(0, harness.paletteState.selectedIndex)
    }
}
