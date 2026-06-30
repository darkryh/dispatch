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
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data class TestRoute(
    val id: Int,
) : NavKey

class NavDisplayNavigatorTest {
    @Test
    fun `NavDisplay provides LocalNavigator backed by backStack`() {
        val backStack = NavBackStack(TestRoute(1))
        var navigatorRef: Navigator? = null

        render {
            NavDisplay(
                backStack = backStack,
                entryProvider =
                    entryProvider<TestRoute> {
                        entry<TestRoute> {
                            navigatorRef = LocalNavigator.current
                        }
                    },
            )
        }

        val navigator = navigatorRef
        assertNotNull(navigator)

        navigator.navigate(TestRoute(2))
        assertEquals(2, backStack.size)
        assertEquals(TestRoute(2), backStack.last())

        assertTrue(navigator.popBackStack())
        assertEquals(1, backStack.size)
        assertFalse(navigator.popBackStack())
    }
}

private fun render(content: @Composable () -> Unit) {
    val terminal =
        Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 80,
            height = 20,
            interactive = false,
        )
    val composer = Composer()

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

    composer.getRootNode()?.measure(Constraints.fixedWidth(80))
}
