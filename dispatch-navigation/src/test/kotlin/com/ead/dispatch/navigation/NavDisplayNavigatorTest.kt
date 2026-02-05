package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.withComposer
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data class TestRoute(val id: Int) : NavKey

class NavDisplayNavigatorTest {

    @Test
    fun `NavDisplay provides LocalNavigator backed by backStack`() {
        val backStack = NavBackStack(TestRoute(1))
        val navigatorRef = AtomicReference<Navigator?>()

        render {
            NavDisplay(
                backStack = backStack,
                entryProvider = entryProvider<TestRoute> {
                    entry<TestRoute> {
                        navigatorRef.set(LocalNavigator.current)
                    }
                }
            )
        }

        val navigator = navigatorRef.get()
        assertNotNull(navigator)

        navigator.navigate(TestRoute(2))
        assertEquals(2, backStack.size)
        assertTrue(backStack.last() is TestRoute)

        assertTrue(navigator.popBackStack())
        assertEquals(1, backStack.size)
        assertFalse(navigator.popBackStack())
    }
}

private fun render(content: @Dispatchable () -> Unit) {
    val terminal = Terminal(
        ansiLevel = AnsiLevel.NONE,
        width = 80,
        height = 20,
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
        ) {
            content()
        }

        composer.setMeasurableCollector(null)
        composer.endComposition()
    }

    root.get()?.measure(Constraints.fixedWidth(80))
}
