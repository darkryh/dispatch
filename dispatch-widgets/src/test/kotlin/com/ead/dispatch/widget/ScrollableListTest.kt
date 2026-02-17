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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollableListTest {

    @Test
    fun `renders all items in unbounded mode`() {
        val lines = renderLines(width = 40) {
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
        val terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 40,
            height = 5,
            interactive = false,
        )
        val composer = Composer()
        val focusRegistry = FocusRegistry()
        val scrollState = ScrollState(initialOffset = 3)
        var showFooter = true

        fun render(content: @Dispatchable () -> Unit): List<String> {
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
            return rootNode.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = terminal.size.width,
                    minHeight = 0,
                    maxHeight = terminal.size.height,
                )
            ).lines
        }

        val content: @Dispatchable () -> Unit = {
            LazyColumn(state = scrollState) {
                items((0 until 6).toList()) { item ->
                    Text("Item-$item")
                }
                if (showFooter) {
                    item { Text("Loading...") }
                    item { Text("Please wait") }
                }
            }
        }

        render(content)
        showFooter = false
        val linesAfterShrink = render(content)

        assertEquals(1, scrollState.offset)
        assertTrue(linesAfterShrink.none { it.isBlank() })
    }
}
