package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressBarMeasurableTest {

    @Test
    fun `progress 0 shows empty bar`() {
        val measurable = ProgressBarMeasurable(
            progress = 0f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.startsWith("["), "should start with left cap")
        assertTrue(line.endsWith("]"), "should end with right cap")
        assertTrue(line.contains("░"), "should contain empty characters")
    }

    @Test
    fun `progress 1 shows full bar`() {
        val measurable = ProgressBarMeasurable(
            progress = 1f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("█"), "should contain filled characters")
    }

    @Test
    fun `progress 0_5 shows half filled bar`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints(maxWidth = 22))
        val line = placeable.lines.first()
        assertTrue(line.contains("█"), "should contain filled characters")
        assertTrue(line.contains("░"), "should contain empty characters")
    }

    @Test
    fun `progress above 1 is clamped to 1`() {
        val measurable = ProgressBarMeasurable(
            progress = 1.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "should still render")
    }

    @Test
    fun `progress below 0 is clamped to 0`() {
        val measurable = ProgressBarMeasurable(
            progress = -0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "should still render")
    }

    @Test
    fun `showPercentage true appends percentage text`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.75f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = true
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("75%"), "should contain percentage")
    }

    @Test
    fun `ProgressBarStyle Blocks uses block characters`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("█") || line.contains("░"), "should use block characters")
    }

    @Test
    fun `ProgressBarStyle Ascii uses hash and dash`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Ascii,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("#") || line.contains("-"), "should use ASCII characters")
    }

    @Test
    fun `ProgressBarStyle Dots uses circle characters`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Dots,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("●") || line.contains("○"), "should use dot characters")
    }

    @Test
    fun `ProgressBarStyle Arrows uses arrow characters`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Arrows,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("▶") || line.contains("▷"), "should use arrow characters")
    }

    @Test
    fun `ProgressBarStyle Line uses line characters`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Line,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("━") || line.contains("─"), "should use line characters")
    }

    @Test
    fun `ProgressBarStyle Minimal uses minimal characters`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Minimal,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("■") || line.contains("□"), "should use minimal characters")
    }

    @Test
    fun `respects maxWidth constraint`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints(maxWidth = 30))
        assertEquals(30, placeable.width, "should respect maxWidth")
    }

    @Test
    fun `unbounded width uses default width`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = false
        )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.width > 0, "should have positive width")
    }

    @Test
    fun `progress bar is always single line`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = true
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "progress bar should be single line")
        assertEquals(1, placeable.lines.size, "should have exactly one line")
    }

    @Test
    fun `very small width with percentage does not throw`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = true
        )

        val placeable = measurable.measure(Constraints(maxWidth = 4))
        assertEquals(1, placeable.height)
    }

    @Test
    fun `zero width constraint does not throw`() {
        val measurable = ProgressBarMeasurable(
            progress = 0.5f,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks,
            showPercentage = true
        )

        val placeable = measurable.measure(Constraints(maxWidth = 0))
        assertEquals(1, placeable.height)
    }
}
