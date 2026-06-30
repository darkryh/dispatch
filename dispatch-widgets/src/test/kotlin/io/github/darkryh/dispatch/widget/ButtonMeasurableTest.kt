package io.github.darkryh.dispatch.widget

import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ButtonMeasurableTest {
    @Test
    fun `focused button uses selection style without changing measured width`() {
        val measurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Outlined,
                modifier = Modifier,
                isFocused = true,
                focusedStyle = TextStyle(inverse = true),
            )

        val placeable = measurable.measure(Constraints())

        assertEquals(6, placeable.width)
        assertTrue(placeable.lines.first().contains("\u001B["))
        assertTrue(placeable.lines.first().contains("[ OK ]"))
    }

    @Test
    fun `ButtonStyle Outlined renders with brackets`() {
        val measurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Outlined,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[ OK ]", placeable.lines.first(), "Outlined style should have [ ] brackets")
        assertEquals(6, placeable.width, "width should include brackets and spacing")
        assertEquals(1, placeable.height, "button should be single line")
    }

    @Test
    fun `ButtonStyle Angled renders with angle brackets`() {
        val measurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Angled,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("<OK>", placeable.lines.first(), "Angled style should have < > brackets")
        assertEquals(4, placeable.width)
    }

    @Test
    fun `ButtonStyle Text renders without decoration`() {
        val measurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Text,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("OK", placeable.lines.first(), "Text style should have no decoration")
        assertEquals(2, placeable.width)
    }

    @Test
    fun `ButtonStyle Filled renders with fill characters`() {
        val measurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Filled,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.first().contains("OK"), "Filled style should contain text")
        assertTrue(placeable.width >= 2, "width should include fill characters")
    }

    @Test
    fun `ButtonStyle Rounded renders with parentheses`() {
        val measurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Rounded,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("(OK)", placeable.lines.first(), "Rounded style should have ( ) brackets")
        assertEquals(4, placeable.width)
    }

    @Test
    fun `disabled button still measures correctly`() {
        val measurable =
            ButtonMeasurable(
                text = "Submit",
                enabled = false,
                style = ButtonStyle.Outlined,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "disabled button should still be single line")
        assertTrue(placeable.width > 0, "disabled button should have positive width")
    }

    @Test
    fun `button respects maxWidth constraint`() {
        val measurable =
            ButtonMeasurable(
                text = "Very Long Button Text",
                enabled = true,
                style = ButtonStyle.Outlined,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints(maxWidth = 10))
        assertTrue(placeable.width <= 10, "button width should respect maxWidth")
    }

    @Test
    fun `empty text button measures correctly`() {
        val measurable =
            ButtonMeasurable(
                text = "",
                enabled = true,
                style = ButtonStyle.Outlined,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[  ]", placeable.lines.first(), "empty button should show brackets with space")
    }

    @Test
    fun `ToggleButton Checkbox checked shows checkmark`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = true,
                label = null,
                enabled = true,
                style = ToggleStyle.Checkbox,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[✓]", placeable.lines.first(), "checked Checkbox should show checkmark")
    }

    @Test
    fun `ToggleButton Checkbox unchecked shows empty box`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = false,
                label = null,
                enabled = true,
                style = ToggleStyle.Checkbox,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[ ]", placeable.lines.first(), "unchecked Checkbox should show empty box")
    }

    @Test
    fun `ToggleButton Square checked shows filled square`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = true,
                label = null,
                enabled = true,
                style = ToggleStyle.Square,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[■]", placeable.lines.first(), "checked Square should show filled square")
    }

    @Test
    fun `ToggleButton Circle checked shows filled circle`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = true,
                label = null,
                enabled = true,
                style = ToggleStyle.Circle,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("(●)", placeable.lines.first(), "checked Circle should show filled circle")
    }

    @Test
    fun `ToggleButton Circle unchecked shows empty circle`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = false,
                label = null,
                enabled = true,
                style = ToggleStyle.Circle,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("( )", placeable.lines.first(), "unchecked Circle should show empty circle")
    }

    @Test
    fun `ToggleButton Switch checked shows ON`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = true,
                label = null,
                enabled = true,
                style = ToggleStyle.Switch,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[ON ]", placeable.lines.first(), "checked Switch should show ON")
    }

    @Test
    fun `ToggleButton Switch unchecked shows OFF`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = false,
                label = null,
                enabled = true,
                style = ToggleStyle.Switch,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[OFF]", placeable.lines.first(), "unchecked Switch should show OFF")
    }

    @Test
    fun `ToggleButton Emoji checked shows checkmark emoji`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = true,
                label = null,
                enabled = true,
                style = ToggleStyle.Emoji,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.first().contains("✅"), "checked Emoji should show checkmark emoji")
    }

    @Test
    fun `ToggleButton Emoji unchecked shows empty square emoji`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = false,
                label = null,
                enabled = true,
                style = ToggleStyle.Emoji,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.first().contains("⬜"), "unchecked Emoji should show empty square emoji")
    }

    @Test
    fun `ToggleButton with label includes label text`() {
        val measurable =
            ToggleButtonMeasurable(
                checked = true,
                label = "Enable feature",
                enabled = true,
                style = ToggleStyle.Checkbox,
                modifier = Modifier,
            )

        val placeable = measurable.measure(Constraints())
        assertEquals("[✓] Enable feature", placeable.lines.first(), "should include label after indicator")
    }

    @Test
    fun `ButtonRow with single button`() {
        val buttonMeasurable =
            ButtonMeasurable(
                text = "OK",
                enabled = true,
                style = ButtonStyle.Outlined,
                modifier = Modifier,
            )

        val rowMeasurable =
            ButtonRowMeasurable(
                modifier = Modifier,
                buttons = listOf(buttonMeasurable),
                spacing = 2,
            )

        val placeable = rowMeasurable.measure(Constraints())
        assertEquals(1, placeable.height, "row should be single line")
        assertEquals("[ OK ]", placeable.lines.first())
    }

    @Test
    fun `ButtonRow with multiple buttons has spacing`() {
        val button1 =
            ButtonMeasurable(
                text = "A",
                enabled = true,
                style = ButtonStyle.Text,
                modifier = Modifier,
            )
        val button2 =
            ButtonMeasurable(
                text = "B",
                enabled = true,
                style = ButtonStyle.Text,
                modifier = Modifier,
            )

        val rowMeasurable =
            ButtonRowMeasurable(
                modifier = Modifier,
                buttons = listOf(button1, button2),
                spacing = 2,
            )

        val placeable = rowMeasurable.measure(Constraints())
        assertEquals("A  B", placeable.lines.first(), "buttons should have spacing between them")
        assertEquals(4, placeable.width, "width should include spacing")
    }

    @Test
    fun `ButtonRow with zero spacing`() {
        val button1 =
            ButtonMeasurable(
                text = "X",
                enabled = true,
                style = ButtonStyle.Text,
                modifier = Modifier,
            )
        val button2 =
            ButtonMeasurable(
                text = "Y",
                enabled = true,
                style = ButtonStyle.Text,
                modifier = Modifier,
            )

        val rowMeasurable =
            ButtonRowMeasurable(
                modifier = Modifier,
                buttons = listOf(button1, button2),
                spacing = 0,
            )

        val placeable = rowMeasurable.measure(Constraints())
        assertEquals("XY", placeable.lines.first(), "buttons should be adjacent with zero spacing")
    }

    @Test
    fun `ButtonRow with empty buttons list`() {
        val rowMeasurable =
            ButtonRowMeasurable(
                modifier = Modifier,
                buttons = emptyList(),
                spacing = 2,
            )

        val placeable = rowMeasurable.measure(Constraints())
        assertEquals(0, placeable.width, "empty row should have zero width")
        assertEquals(1, placeable.height, "empty row should still have height 1")
    }
}
