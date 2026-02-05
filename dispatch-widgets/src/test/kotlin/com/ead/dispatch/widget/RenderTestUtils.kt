package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
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
import java.util.concurrent.atomic.AtomicReference

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
    val root = AtomicReference<Measurable?>(null)

    withComposer(composer) {
        composer.startComposition()
        composer.setMeasurableCollector { measurable -> root.set(measurable) }

        CompositionLocalProvider(
            LocalTerminal provides terminal,
            LocalTerminalWidth provides terminal.size.width,
            LocalTerminalHeight provides terminal.size.height,
            LocalKeyboardInterceptor provides KeyboardInterceptor(),
            LocalFocusRegistry provides FocusRegistry(),
        ) {
            content()
        }

        composer.setMeasurableCollector(null)
        composer.endComposition()
    }

    val measurable = root.get() ?: return emptyList()
    return measurable.measure(Constraints.fixedWidth(width)).lines
}
