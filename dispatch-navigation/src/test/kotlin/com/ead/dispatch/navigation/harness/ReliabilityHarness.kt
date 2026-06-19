package com.ead.dispatch.navigation.harness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.DispatchConfig
import com.ead.dispatch.runtime.DispatchScope
import com.ead.dispatch.runtime.FocusRegistry
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

internal class ReliabilityHarness(
    width: Int = 120,
    height: Int = 40,
    theme: DispatchTheme = DispatchTheme.Dark,
) {
    private var activeTheme: DispatchTheme = theme
    private val terminal =
        Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = width,
            height = height,
            interactive = false,
        )
    private val keyboardInterceptor = KeyboardInterceptor()
    private val focusRegistry = FocusRegistry()
    private val dispatchScope = TestDispatchScope(terminal, { activeTheme }, keyboardInterceptor)
    private val composer = Composer()

    fun render(
        theme: DispatchTheme = activeTheme,
        boundedHeight: Boolean = false,
        content: @Composable () -> Unit,
    ): List<String> {
        activeTheme = theme
        withComposer(composer) {
            composer.startComposition()

            CompositionLocalProvider(
                LocalTerminal provides terminal,
                LocalTerminalWidth provides terminal.size.width,
                LocalTerminalHeight provides terminal.size.height,
                LocalKeyboardInterceptor provides keyboardInterceptor,
                LocalFocusRegistry provides focusRegistry,
                LocalDispatchScope provides dispatchScope,
                LocalTheme provides activeTheme,
            ) {
                content()
            }

            composer.endComposition()
        }

        val rootNode = composer.getRootNode() ?: return emptyList()
        focusRegistry.sync(rootNode)
        val constraints =
            if (boundedHeight) {
                Constraints.maxSize(terminal.size.width, terminal.size.height)
            } else {
                Constraints.fixedWidth(terminal.size.width)
            }
        return rootNode.measure(constraints).lines
    }

    fun press(
        key: String,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ) {
        dispatchScope.sendKey(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
    }
}

private class TestDispatchScope(
    override val terminal: Terminal,
    private val themeProvider: () -> DispatchTheme,
    private val keyboardInterceptor: KeyboardInterceptor,
    override val args: Array<String> = emptyArray(),
) : DispatchScope {
    private var keyHandler: ((KeyboardEvent) -> Unit)? = null

    override val theme: DispatchTheme get() = themeProvider()
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
