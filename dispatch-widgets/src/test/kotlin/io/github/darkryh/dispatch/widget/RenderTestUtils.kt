package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.DispatchComposition
import io.github.darkryh.dispatch.runtime.FocusRegistry
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalFocusRegistry
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth

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
