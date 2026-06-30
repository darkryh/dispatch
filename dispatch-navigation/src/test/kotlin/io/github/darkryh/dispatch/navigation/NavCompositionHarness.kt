package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.withComposer

/**
 * Retained-composition harness for navigation tests.
 *
 * Backed by the real Compose runtime via [Composer]/[withComposer], so re-rendering
 * mutated state recomposes the SAME composition, and [close] disposes it (firing
 * RememberObserver.onForgotten / DisposableEffect.onDispose). This is what makes
 * the memory-leak regressions observable.
 */
internal class NavCompositionHarness(
    width: Int = 80,
    height: Int = 20,
) : AutoCloseable {
    private val terminal =
        Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = width,
            height = height,
            interactive = false,
        )
    private val composer = Composer()

    fun render(content: @Composable () -> Unit) {
        withComposer(composer) {
            composer.startComposition()
            CompositionLocalProvider(
                LocalTerminal provides terminal,
                LocalTerminalWidth provides terminal.size.width,
                LocalTerminalHeight provides terminal.size.height,
            ) {
                content()
            }
            composer.endComposition()
        }
        composer.getRootNode()?.measure(Constraints.fixedWidth(terminal.size.width))
    }

    override fun close() {
        composer.close()
    }
}
