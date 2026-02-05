package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.FocusRegistry
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.withComposer
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal

internal fun renderLines(
    width: Int = 80,
    height: Int = 20,
    content: @Dispatchable () -> Unit,
): List<String> {
    val terminal = Terminal(
        ansiLevel = AnsiLevel.NONE,
        width = width,
        height = height,
        interactive = false,
    )
    val composer = Composer()
    val focusRegistry = FocusRegistry()

    withComposer(composer) {
        composer.startComposition()

        CompositionLocalProvider(
            LocalTerminal provides terminal,
            LocalTerminalWidth provides terminal.size.width,
            LocalTerminalHeight provides terminal.size.height,
            LocalKeyboardInterceptor provides KeyboardInterceptor(),
            LocalFocusRegistry provides focusRegistry,
        ) {
            content()
        }

        composer.endComposition()
    }

    val rootNode = composer.getRootNode() ?: return emptyList()
    focusRegistry.sync(rootNode)
    return rootNode.measure(Constraints.fixedWidth(width)).lines
}
