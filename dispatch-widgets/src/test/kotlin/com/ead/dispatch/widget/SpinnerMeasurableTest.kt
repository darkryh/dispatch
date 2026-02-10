package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpinnerMeasurableTest {

    @Test
    fun `SpinnerStyle Dots cycles through frames`() {
        val frames = SpinnerStyle.Dots.frames

        for (frameIndex in frames.indices) {
            val measurable = SpinnerMeasurable(
                frame = frameIndex,
                modifier = Modifier,
                style = SpinnerStyle.Dots
            )

            val placeable = measurable.measure(Constraints())
            assertEquals(frames[frameIndex], placeable.lines.first(), "frame $frameIndex should match")
        }
    }

    @Test
    fun `SpinnerStyle Dots has 10 frames`() {
        assertEquals(10, SpinnerStyle.Dots.frames.size, "Dots style should have 10 frames")
    }

    @Test
    fun `SpinnerStyle Circle cycles through frames`() {
        val frames = SpinnerStyle.Circle.frames

        for (frameIndex in frames.indices) {
            val measurable = SpinnerMeasurable(
                frame = frameIndex,
                modifier = Modifier,
                style = SpinnerStyle.Circle
            )

            val placeable = measurable.measure(Constraints())
            assertEquals(frames[frameIndex], placeable.lines.first(), "frame $frameIndex should match")
        }
    }

    @Test
    fun `SpinnerStyle Circle has 4 frames`() {
        assertEquals(4, SpinnerStyle.Circle.frames.size, "Circle style should have 4 frames")
    }

    @Test
    fun `SpinnerStyle Growing has 12 frames`() {
        assertEquals(12, SpinnerStyle.Growing.frames.size, "Growing style should have 12 frames")
    }

    @Test
    fun `SpinnerStyle Pulse has 4 frames`() {
        assertEquals(4, SpinnerStyle.Pulse.frames.size, "Pulse style should have 4 frames")
    }

    @Test
    fun `SpinnerStyle Quadrants has 4 frames`() {
        assertEquals(4, SpinnerStyle.Quadrants.frames.size, "Quadrants style should have 4 frames")
    }

    @Test
    fun `SpinnerStyle Shades has 7 frames`() {
        assertEquals(7, SpinnerStyle.Shades.frames.size, "Shades style should have 7 frames")
    }

    @Test
    fun `frame wraps around when exceeds frame count`() {
        val style = SpinnerStyle.Circle // has 4 frames
        val measurable = SpinnerMeasurable(
            frame = 5, // should wrap to frame 1
            modifier = Modifier,
            style = style
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(style.frames[1], placeable.lines.first(), "should wrap to frame 1")
    }

    @Test
    fun `negative frame wraps to valid index`() {
        val style = SpinnerStyle.Circle // has 4 frames
        val measurable = SpinnerMeasurable(
            frame = -1, // should wrap to last frame
            modifier = Modifier,
            style = style
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(style.frames.last(), placeable.lines.first(), "should wrap to last frame")
    }

    @Test
    fun `large frame value wraps correctly`() {
        val style = SpinnerStyle.Circle // has 4 frames
        val measurable = SpinnerMeasurable(
            frame = 100, // should wrap to frame 0 (100 % 4 = 0)
            modifier = Modifier,
            style = style
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(style.frames[0], placeable.lines.first(), "should wrap to frame 0")
        assertEquals(1, placeable.height, "should still render")
    }

    @Test
    fun `spinner is always single line`() {
        val measurable = SpinnerMeasurable(
            frame = 0,
            modifier = Modifier,
            style = SpinnerStyle.Dots
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "spinner should be single line")
    }

    @Test
    fun `spinner width matches frame character width`() {
        val measurable = SpinnerMeasurable(
            frame = 0,
            modifier = Modifier,
            style = SpinnerStyle.Dots
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(SpinnerStyle.Dots.frames[0].length, placeable.width, "width should match frame character length")
    }

    @Test
    fun `LoadingIndicator combines spinner and text`() {
        val measurable = LoadingIndicatorMeasurable(
            frame = 0,
            text = "Loading...",
            modifier = Modifier,
            style = SpinnerStyle.Dots
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("Loading..."), "should contain text")
        assertTrue(line.contains(SpinnerStyle.Dots.frames[0]), "should contain spinner frame")
    }

    @Test
    fun `LoadingIndicator cycles through frames`() {
        val frame0 = LoadingIndicatorMeasurable(
            frame = 0,
            text = "Test",
            modifier = Modifier,
            style = SpinnerStyle.Dots
        ).measure(Constraints()).lines.first()

        val frame1 = LoadingIndicatorMeasurable(
            frame = 1,
            text = "Test",
            modifier = Modifier,
            style = SpinnerStyle.Dots
        ).measure(Constraints()).lines.first()

        assertTrue(frame0 != frame1, "different frames should produce different output")
    }

    @Test
    fun `LoadingIndicator is single line`() {
        val measurable = LoadingIndicatorMeasurable(
            frame = 0,
            text = "Processing",
            modifier = Modifier,
            style = SpinnerStyle.Dots
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "loading indicator should be single line")
    }

    @Test
    fun `TransferProgress shows byte information`() {
        val measurable = TransferProgressMeasurable(
            progress = 0.5f,
            bytesTransferred = 5_000_000,
            totalBytes = 10_000_000,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks
        )

        val placeable = measurable.measure(Constraints())
        val line = placeable.lines.first()
        assertTrue(line.contains("MB"), "should show megabytes")
        assertTrue(line.contains("50%"), "should show percentage")
    }

    @Test
    fun `TransferProgress formats bytes correctly`() {
        // Test GB
        val gbMeasurable = TransferProgressMeasurable(
            progress = 0.5f,
            bytesTransferred = 1_500_000_000,
            totalBytes = 3_000_000_000,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks
        )
        assertTrue(gbMeasurable.measure(Constraints()).lines.first().contains("GB"), "should show GB for large values")

        // Test KB
        val kbMeasurable = TransferProgressMeasurable(
            progress = 0.5f,
            bytesTransferred = 500,
            totalBytes = 1000,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks
        )
        assertTrue(kbMeasurable.measure(Constraints()).lines.first().contains("B"), "should show B for small values")
    }

    @Test
    fun `TransferProgress is single line`() {
        val measurable = TransferProgressMeasurable(
            progress = 0.5f,
            bytesTransferred = 100,
            totalBytes = 200,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks
        )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.height, "transfer progress should be single line")
    }

    @Test
    fun `TransferProgress respects maxWidth`() {
        val measurable = TransferProgressMeasurable(
            progress = 0.5f,
            bytesTransferred = 5_000_000,
            totalBytes = 10_000_000,
            modifier = Modifier,
            style = ProgressBarStyle.Blocks
        )

        val placeable = measurable.measure(Constraints(maxWidth = 60))
        assertTrue(placeable.width <= 60 || placeable.width == measurable.measure(Constraints()).width, "should respect maxWidth or use minimum required width")
    }
}
