package com.ead.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.DispatchComposition
import com.ead.dispatch.runtime.FocusRegistry
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal

internal fun renderLines(
    width: Int = 80,
    height: Int = 20,
    content: @Composable () -> Unit,
): List<String> {
    val terminal =
        Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = width,
            height = height,
            interactive = false,
        )
    val focusRegistry = FocusRegistry()
    return DispatchComposition().use { composition ->
        composition.setContent {
            CompositionLocalProvider(
                LocalTerminal provides terminal,
                LocalTerminalWidth provides terminal.size.width,
                LocalTerminalHeight provides terminal.size.height,
                LocalKeyboardInterceptor provides KeyboardInterceptor(),
                LocalFocusRegistry provides focusRegistry,
            ) {
                content()
            }
        }
        focusRegistry.sync(composition.root)
        composition.root.children.flatMap { node ->
            node.measure(Constraints.fixedWidth(width)).lines
        }
    }
}
