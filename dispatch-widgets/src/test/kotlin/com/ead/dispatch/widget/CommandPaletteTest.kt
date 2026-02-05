package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.withComposer
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandPaletteTest {
    private data class PaletteRender(
        val lines: List<String>,
        val state: CommandPaletteState<String>,
        val keyboard: KeyboardInterceptor,
    )

    private val plainStyles = CommandPaletteTextStyles(
        prefix = null,
        selectedPrefix = null,
        label = null,
        selectedLabel = null,
        description = null,
        selectedDescription = null,
        disabledLabel = null,
        noResultsText = null,
    )

    private fun renderPalette(
        options: List<CommandOption<String>>,
        inputValue: String = "/",
        showIcons: Boolean = false,
        commandPrefix: String? = "/",
        selectionIndicator: String? = null,
        showDescriptions: Boolean = true,
        visibleCount: Int = 6,
        state: CommandPaletteState<String> = CommandPaletteState(),
        keyboardInterceptor: KeyboardInterceptor = KeyboardInterceptor(),
    ): PaletteRender {
        val terminal = Terminal(
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
                LocalKeyboardInterceptor provides keyboardInterceptor,
            ) {
                CommandPalette(
                    options = options,
                    inputValue = inputValue,
                    onOptionSelected = {},
                    onInputTransform = {},
                    showIcons = showIcons,
                    commandPrefix = commandPrefix,
                    selectionIndicator = selectionIndicator,
                    textStyles = plainStyles,
                    showDescriptions = showDescriptions,
                    visibleCount = visibleCount,
                    state = state,
                )
            }

            composer.endComposition()
        }

        val rootNode = composer.getRootNode()
        val lines = rootNode?.measure(Constraints.fixedWidth(80))?.lines ?: emptyList()

        return PaletteRender(lines, state, keyboardInterceptor)
    }

    private fun renderPaletteLines(
        options: List<CommandOption<String>>,
        inputValue: String = "/",
        showIcons: Boolean = false,
        commandPrefix: String? = "/",
        selectionIndicator: String? = null,
        showDescriptions: Boolean = true,
        visibleCount: Int = 6,
    ): List<String> {
        return renderPalette(
            options = options,
            inputValue = inputValue,
            showIcons = showIcons,
            commandPrefix = commandPrefix,
            selectionIndicator = selectionIndicator,
            showDescriptions = showDescriptions,
            visibleCount = visibleCount,
        ).lines
    }

    @Test
    fun `descriptions align and omit dash`() {
        val lines = renderPaletteLines(
            options = listOf(
                CommandOption(label = "short", description = "first", data = "short"),
                CommandOption(label = "longer", description = "second", data = "longer"),
            ),
        )

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("/short"))
        assertTrue(lines[1].startsWith("/longer"))
        assertFalse(lines.any { it.contains(" - ") })

        val firstIndex = lines[0].indexOf("first")
        val secondIndex = lines[1].indexOf("second")
        assertTrue(firstIndex >= 0)
        assertTrue(secondIndex >= 0)
        assertEquals(firstIndex, secondIndex)
    }

    @Test
    fun `null icons are ignored when showIcons enabled`() {
        val lines = renderPaletteLines(
            options = listOf(
                CommandOption(label = "model", description = "desc", icon = null, data = "model"),
                CommandOption(label = "star", description = "desc", icon = "*", data = "star"),
            ),
            showIcons = true,
        )

        assertTrue(lines[0].startsWith("/model"))
        assertFalse(lines[0].contains("null"))
        assertTrue(lines[1].startsWith("* "))
    }

    @Test
    fun `command prefix is not duplicated when label already prefixed`() {
        val lines = renderPaletteLines(
            options = listOf(
                CommandOption(label = "/model", description = "desc", data = "model"),
            ),
        )

        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("/model"))
        assertFalse(lines[0].startsWith("//model"))
    }

    @Test
    fun `palette visibility follows trigger`() {
        val options = listOf(
            CommandOption(label = "model", description = "desc", data = "model"),
        )
        val state = CommandPaletteState<String>()

        renderPalette(options = options, inputValue = "/", state = state)
        assertTrue(state.isVisible)

        renderPalette(options = options, inputValue = "hello/", state = state)
        assertFalse(state.isVisible)

        renderPalette(options = options, inputValue = "hello", state = state)
        assertFalse(state.isVisible)
    }

    @Test
    fun `keyboard navigation updates selected index`() {
        val options = listOf(
            CommandOption(label = "model", description = "desc", data = "model"),
            CommandOption(label = "help", description = "desc", data = "help"),
        )
        val state = CommandPaletteState<String>()
        val keyboard = KeyboardInterceptor()

        renderPalette(
            options = options,
            inputValue = "/",
            state = state,
            keyboardInterceptor = keyboard,
        )

        assertEquals(0, state.selectedIndex)
        keyboard.tryIntercept(KeyboardEvent("ArrowDown"))
        assertEquals(1, state.selectedIndex)
        keyboard.tryIntercept(KeyboardEvent("ArrowUp"))
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun `selection clamps when filtered options shrink`() {
        val options = listOf(
            CommandOption(label = "model", description = "desc", data = "model"),
            CommandOption(label = "help", description = "desc", data = "help"),
        )
        val state = CommandPaletteState<String>()

        renderPalette(options = options, inputValue = "/", state = state)
        state.selectedIndex = 1

        renderPalette(options = options, inputValue = "/m", state = state)

        assertEquals(0, state.selectedIndex)
        assertEquals(listOf("model"), state.filteredOptions.map { it.label })
    }

    @Test
    fun `selection indicator only shows on selected item`() {
        val lines = renderPaletteLines(
            options = listOf(
                CommandOption(label = "model", description = "desc", data = "model"),
                CommandOption(label = "help", description = "desc", data = "help"),
            ),
            selectionIndicator = "> ",
            showDescriptions = false,
        )

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("> /"))
        assertTrue(lines[1].startsWith("  /"))
    }

    @Test
    fun `no results text renders when filter has no matches`() {
        val lines = renderPaletteLines(
            options = listOf(
                CommandOption(label = "model", description = "desc", data = "model"),
            ),
            inputValue = "/missing",
        )

        assertEquals(1, lines.size)
        assertEquals("No matching commands", lines[0].trim())
    }

    @Test
    fun `filter text and options update from input`() {
        val state = CommandPaletteState<String>()

        renderPalette(
            options = listOf(
                CommandOption(label = "model", description = "desc", data = "model"),
                CommandOption(label = "review", description = "desc", data = "review"),
            ),
            inputValue = "/re",
            state = state,
        )

        assertEquals("re", state.filterText)
        assertEquals(listOf("review"), state.filteredOptions.map { it.label })
    }
}
