package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.FocusRegistry
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalFocusRegistry
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.withComposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollableListTest {
    @Test
    fun `renders all items in unbounded mode`() {
        val lines =
            renderLines(width = 40) {
                ScrollableList(
                    items = listOf("Alpha", "Beta", "Gamma"),
                ) { item ->
                    Text(item)
                }
            }

        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("Alpha"))
        assertTrue(lines[1].contains("Beta"))
        assertTrue(lines[2].contains("Gamma"))
    }

    @Test
    fun `clamps offset when content shrinks across recomposition`() {
        val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 40,
                height = 5,
                interactive = false,
            )
        val composer = Composer()
        val focusRegistry = FocusRegistry()
        val scrollState = ScrollState(initialOffset = 3)
        val showFooter = androidx.compose.runtime.mutableStateOf(true)

        fun render(content: @Composable () -> Unit): List<String> {
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
            return rootNode
                .measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = terminal.size.width,
                        minHeight = 0,
                        maxHeight = terminal.size.height,
                    ),
                ).lines
        }

        val content: @Composable () -> Unit = {
            LazyColumn(state = scrollState) {
                items((0 until 6).toList()) { item ->
                    Text("Item-$item")
                }
                if (showFooter.value) {
                    item { Text("Loading...") }
                    item { Text("Please wait") }
                }
            }
        }

        render(content)
        showFooter.value = false
        val linesAfterShrink = render(content)

        assertEquals(1, scrollState.offset)
        assertTrue(linesAfterShrink.none { it.isBlank() })
    }

    @Test
    fun `bounded list with wrapped first item still renders following items`() {
        val longLine = "This is a long line that wraps across several visual rows in narrow width."
        val lines =
            renderLines(width = 38, height = 20) {
                ScrollableList(
                    items = listOf(longLine, "Second row", "Third row"),
                    modifier = Modifier.height(6),
                ) { item ->
                    Text(item)
                }
            }

        assertEquals(6, lines.size)
        assertTrue(lines.any { it.contains("Second row") })
        assertTrue(lines.any { it.contains("Third row") })
    }

    @Test
    fun `bounded list updates content height using intrinsic item heights`() {
        val scrollState = ScrollState()
        val longLine = "One very long line that wraps and should contribute full intrinsic height."
        renderLines(width = 34, height = 20) {
            ScrollableList(
                items = listOf(longLine, longLine, "tail"),
                modifier = Modifier.height(5),
                scrollState = scrollState,
            ) { item ->
                Text(item)
            }
        }

        assertEquals(5, scrollState.viewportHeight)
        assertTrue(scrollState.contentHeight > scrollState.viewportHeight)
        assertTrue(scrollState.maxOffset > 0)
    }

    @Test
    fun `bounded list pads viewport when content is shorter than viewport`() {
        val lines =
            renderLines(width = 30, height = 20) {
                ScrollableList(
                    items = listOf("One"),
                    modifier = Modifier.height(4),
                ) { item ->
                    Text(item)
                }
            }

        assertEquals(4, lines.size)
        assertTrue(lines.first().contains("One"))
    }
}
