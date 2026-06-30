package io.github.darkryh.dispatch.runtime

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ScrollingContentTrackerTest {
    @Test
    fun `first consume returns all lines`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(listOf("a", "b"), ScrollUpdateKind.APPEND)
    }

    @Test
    fun `second consume with same lines returns empty`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b"))
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(emptyList(), ScrollUpdateKind.NONE)
    }

    @Test
    fun `consume with appended lines returns only new lines`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a"))
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(listOf("b", "c"), ScrollUpdateKind.APPEND)
    }

    @Test
    fun `consume with replaced lines requests rewrite`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b", "c"))
        tracker.consume(listOf("x", "y")) shouldBe ScrollUpdate(listOf("x", "y"), ScrollUpdateKind.REWRITE)
    }

    @Test
    fun `consume with rewritten same-size prefix requests rewrite`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(listOf("a", "b", "c"), ScrollUpdateKind.APPEND)
        tracker.consume(listOf("x", "b", "c")) shouldBe ScrollUpdate(listOf("x", "b", "c"), ScrollUpdateKind.REWRITE)
    }

    @Test
    fun `consume with rewritten prefix and appended tail requests rewrite`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(listOf("a", "b", "c"), ScrollUpdateKind.APPEND)
        tracker.consume(listOf("x", "b", "c", "d")) shouldBe ScrollUpdate(listOf("x", "b", "c", "d"), ScrollUpdateKind.REWRITE)
    }

    @Test
    fun `consume empty after committed content requests empty rewrite`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(listOf("a", "b"), ScrollUpdateKind.APPEND)
        tracker.consume(emptyList()) shouldBe ScrollUpdate(emptyList(), ScrollUpdateKind.REWRITE)
        tracker.consume(emptyList()) shouldBe ScrollUpdate(emptyList(), ScrollUpdateKind.NONE)
    }

    @Test
    fun `sync updates internal state without appending`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(listOf("a", "b"), ScrollUpdateKind.APPEND)
        tracker.sync(listOf("a", "b", "c"))
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(emptyList(), ScrollUpdateKind.NONE)
    }

    @Test
    fun `rewrite then append emits rewritten then tail append`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(listOf("a", "b", "c"), ScrollUpdateKind.APPEND)
        tracker.consume(listOf("x", "y")) shouldBe ScrollUpdate(listOf("x", "y"), ScrollUpdateKind.REWRITE)
        tracker.consume(listOf("x", "y", "z")) shouldBe ScrollUpdate(listOf("z"), ScrollUpdateKind.APPEND)
    }

    @Test
    fun `mode switching reports rewrites between histories`() {
        val tracker = ScrollingContentTracker()
        val chatLines = listOf("chat-1", "chat-2", "chat-status")
        val storyLines = listOf("story-header", "story-body", "story-status")

        tracker.consume(chatLines) shouldBe ScrollUpdate(chatLines, ScrollUpdateKind.APPEND)
        repeat(5) {
            tracker.consume(storyLines) shouldBe ScrollUpdate(storyLines, ScrollUpdateKind.REWRITE)
            tracker.consume(chatLines) shouldBe ScrollUpdate(chatLines, ScrollUpdateKind.REWRITE)
        }
    }

    @Test
    fun `repeated resize cycles do not append scrollback`() {
        val tracker = ScrollingContentTracker()
        val history = listOf("h1", "h2", "h3", "h4")
        val input = listOf("i1", "i2")
        val placeable =
            io.github.darkryh.dispatch.layout.SegmentedSimplePlaceable(
                width = 1,
                height = (history + input).size,
                lines = history + input,
                segmentHeights = listOf(history.size, input.size),
            )

        val (initialScrolling, _) = splitContentForRendering(placeable, activeAreaHeight = 2, committedLineCount = 0)
        tracker.consume(initialScrolling) shouldBe ScrollUpdate(history, ScrollUpdateKind.APPEND)

        repeat(5) {
            val (scrollingExpanded, _) =
                splitContentForRendering(
                    placeable,
                    activeAreaHeight = 3,
                    committedLineCount = initialScrolling.size,
                )
            tracker.sync(scrollingExpanded)
            tracker.consume(scrollingExpanded) shouldBe ScrollUpdate(emptyList(), ScrollUpdateKind.NONE)

            val (scrollingShrunk, _) =
                splitContentForRendering(
                    placeable,
                    activeAreaHeight = 2,
                    committedLineCount = initialScrolling.size,
                )
            tracker.sync(scrollingShrunk)
            tracker.consume(scrollingShrunk) shouldBe ScrollUpdate(emptyList(), ScrollUpdateKind.NONE)
        }
    }
}
