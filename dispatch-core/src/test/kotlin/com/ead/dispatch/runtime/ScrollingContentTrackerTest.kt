package com.ead.dispatch.runtime

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ScrollingContentTrackerTest {
    @Test
    fun `first consume returns all lines`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(listOf("a", "b"), reset = false)
    }

    @Test
    fun `second consume with same lines returns empty`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b"))
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(emptyList(), reset = false)
    }

    @Test
    fun `consume with appended lines returns only new lines`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a"))
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(listOf("b", "c"), reset = false)
    }

    @Test
    fun `consume with replaced lines requests reset`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b", "c"))
        tracker.consume(listOf("x", "y")) shouldBe ScrollUpdate(listOf("x", "y"), reset = true)
    }

    @Test
    fun `sync updates internal state without appending`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(listOf("a", "b"), reset = false)
        tracker.sync(listOf("a", "b", "c"))
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(emptyList(), reset = false)
    }

    @Test
    fun `repeated resize cycles do not append scrollback`() {
        val tracker = ScrollingContentTracker()
        val history = listOf("h1", "h2", "h3", "h4")
        val input = listOf("i1", "i2")
        val placeable = com.ead.dispatch.layout.SegmentedSimplePlaceable(
            width = 1,
            height = (history + input).size,
            lines = history + input,
            segmentHeights = listOf(history.size, input.size),
        )

        val (initialScrolling, _) = splitContentForRendering(placeable, activeAreaHeight = 2, committedLineCount = 0)
        tracker.consume(initialScrolling) shouldBe ScrollUpdate(history, reset = false)

        repeat(5) {
            val (scrollingExpanded, _) = splitContentForRendering(placeable, activeAreaHeight = 3, committedLineCount = initialScrolling.size)
            tracker.sync(scrollingExpanded)
            tracker.consume(scrollingExpanded) shouldBe ScrollUpdate(emptyList(), reset = false)

            val (scrollingShrunk, _) = splitContentForRendering(placeable, activeAreaHeight = 2, committedLineCount = initialScrolling.size)
            tracker.sync(scrollingShrunk)
            tracker.consume(scrollingShrunk) shouldBe ScrollUpdate(emptyList(), reset = false)
        }
    }
}
