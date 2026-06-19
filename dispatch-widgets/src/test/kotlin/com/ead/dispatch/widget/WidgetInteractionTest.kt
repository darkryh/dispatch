package com.ead.dispatch.widget

import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.semantics
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.FocusRegistry
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.withComposer
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WidgetInteractionTest {
    private class FocusHarness {
        private val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 80,
                height = 20,
                interactive = false,
            )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry = FocusRegistry()
        private val composer = Composer()

        var inputValue: String = ""
        var cycleValue: String = "Alpha"
        var buttonClicks: Int = 0
        var iconClicks: Int = 0
        var checked: Boolean = false
        var radioClicks: Int = 0
        var inputEnabled: Boolean = true
        var cycleEnabled: Boolean = true
        var headerText: String = "Header"
        var inputModifier: Modifier = Modifier
        var cycleModifier: Modifier = Modifier

        fun render(): LayoutNode? {
            withComposer(composer) {
                composer.startComposition()

                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides terminal.size.width,
                    LocalTerminalHeight provides terminal.size.height,
                    LocalKeyboardInterceptor provides keyboardInterceptor,
                    LocalFocusRegistry provides focusRegistry,
                ) {
                    Column {
                        Text(headerText)
                        InputTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            icon = "> ",
                            placeholder = "Input",
                            enabled = inputEnabled,
                            modifier = inputModifier,
                        )
                        CycleButton(
                            value = cycleValue,
                            options = listOf("Alpha", "Beta"),
                            onValueChange = { cycleValue = it },
                            enabled = cycleEnabled,
                            modifier = cycleModifier,
                        )
                        Button("Run", onClick = { buttonClicks++ })
                        IconButton("!", onClick = { iconClicks++ })
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { checked = it },
                        )
                        RadioButton(
                            selected = false,
                            onClick = { radioClicks++ },
                        )
                    }
                }

                composer.endComposition()
            }

            val root = composer.getRootNode()
            if (root != null) {
                focusRegistry.sync(root)
                root.measure(Constraints.fixedWidth(80))
            }
            return root
        }

        fun press(
            key: String,
            ctrl: Boolean = false,
            alt: Boolean = false,
            shift: Boolean = false,
        ) {
            keyboardInterceptor.tryIntercept(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
            render()
        }
    }

    @Test
    fun `button family activates through focus and keyboard pipeline`() {
        val harness = FocusHarness()
        harness.render()

        repeat(2) { harness.press("Tab") }
        harness.press("Enter")
        assertEquals(1, harness.buttonClicks)

        harness.press("Tab")
        harness.press("Space")
        assertEquals(1, harness.iconClicks)

        harness.press("Tab")
        harness.press("Enter")
        assertTrue(harness.checked)

        harness.press("Tab")
        harness.press("Enter")
        assertEquals(1, harness.radioClicks)
    }

    @Test
    fun `tab moves focus from input to cycle button and enter cycles`() {
        val harness = FocusHarness()

        harness.render()
        assertEquals("Alpha", harness.cycleValue)

        harness.press("Tab")
        harness.press("Enter")

        assertEquals("Beta", harness.cycleValue)
        assertEquals("", harness.inputValue)
    }

    @Test
    fun `arrow keys traverse actions but remain inside text input`() {
        val harness = FocusHarness()

        harness.render()
        harness.press("ArrowDown")
        harness.press("x")
        assertEquals("x", harness.inputValue, "text input must consume ArrowDown instead of losing focus")

        harness.press("Tab")
        harness.press("ArrowDown")
        harness.press("Enter")
        assertEquals(1, harness.buttonClicks)

        harness.press("ArrowUp")
        harness.press("Enter")
        assertEquals("Beta", harness.cycleValue)
    }

    @Test
    fun `shift tab traverses focus backwards`() {
        val harness = FocusHarness()

        harness.render()
        harness.press("Tab", shift = true)
        harness.press("Enter")

        assertEquals(1, harness.radioClicks)
    }

    @Test
    fun `disabled input is skipped in focus order`() {
        val harness = FocusHarness()
        harness.inputEnabled = false
        harness.render()

        harness.press("Enter")
        assertEquals("Beta", harness.cycleValue)

        harness.press("x")
        assertEquals("", harness.inputValue)
    }

    @Test
    fun `focus stays on same input after recomposition`() {
        val harness = FocusHarness()
        harness.render()

        harness.press("Tab")
        harness.press("Enter")
        assertEquals("Beta", harness.cycleValue)

        harness.headerText = "Header Updated"
        harness.render()

        harness.press("Enter")
        assertEquals("Alpha", harness.cycleValue)
    }

    @Test
    fun `semantics tags surface on widget nodes`() {
        val harness =
            FocusHarness().apply {
                inputModifier = Modifier.semantics("input-main")
                cycleModifier = Modifier.semantics("cycle-main")
            }

        val root = harness.render()
        assertNotNull(root)

        val inputNodes = findNodesWithTag(root, "input-main")
        val cycleNodes = findNodesWithTag(root, "cycle-main")

        assertEquals(1, inputNodes.size)
        assertEquals(1, cycleNodes.size)
        assertTrue(inputNodes.first().name.contains("TextField"))
    }

    private fun findNodesWithTag(
        root: LayoutNode,
        tag: String,
    ): List<LayoutNode> {
        val matches = mutableListOf<LayoutNode>()

        fun visit(node: LayoutNode) {
            if (node.semanticsTags.contains(tag)) {
                matches.add(node)
            }
            node.children.forEach(::visit)
        }
        visit(root)
        return matches
    }
}
