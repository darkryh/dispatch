package io.github.darkryh.dispatch.runtime

import io.github.darkryh.dispatch.layout.SegmentedSimplePlaceable
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveAreaSplitTest {
    private fun placeable(
        lines: List<String>,
        segmentHeights: List<Int>,
    ): SegmentedSimplePlaceable =
        SegmentedSimplePlaceable(
            width = 1,
            height = lines.size,
            lines = lines,
            segmentHeights = segmentHeights,
        )

    @Test
    fun `clips oversized last segment without pushing into scrollback`() {
        val lines = listOf("a1", "a2", "b1", "b2", "b3", "b4", "b5")
        val placeable = placeable(lines, listOf(2, 5))

        val (scrolling, active) = splitContentForRendering(placeable, activeAreaHeight = 3, committedLineCount = 0)

        assertEquals(listOf("a1", "a2", "b1", "b2"), scrolling)
        assertEquals(listOf("b3", "b4", "b5"), active)
    }

    @Test
    fun `clips first active segment when space is tight`() {
        val lines = listOf("s1", "s2a", "s2b", "s2c", "s3a", "s3b")
        val placeable = placeable(lines, listOf(1, 3, 2))

        val (scrolling, active) = splitContentForRendering(placeable, activeAreaHeight = 4, committedLineCount = 0)

        assertEquals(listOf("s1", "s2a"), scrolling)
        assertEquals(listOf("s2b", "s2c", "s3a", "s3b"), active)
    }

    @Test
    fun `returns all lines in active area when it fits`() {
        val lines = listOf("a1", "a2", "b1", "b2")
        val placeable = placeable(lines, listOf(2, 2))

        val (scrolling, active) = splitContentForRendering(placeable, activeAreaHeight = 5, committedLineCount = 0)

        assertEquals(emptyList(), scrolling)
        assertEquals(lines, active)
    }

    @Test
    fun `input growth keeps scrolling content stable`() {
        val tracker = ScrollingContentTracker()
        val history = listOf("h1", "h2")

        val initial =
            placeable(
                lines = history + listOf("i1", "i2", "s1"),
                segmentHeights = listOf(2, 2, 1),
            )
        val (scrollingInitial, activeInitial) =
            splitContentForRendering(
                initial,
                activeAreaHeight = 3,
                committedLineCount = 0,
            )
        assertEquals(history, scrollingInitial)
        assertEquals(listOf("i1", "i2", "s1"), activeInitial)
        assertEquals(ScrollUpdate(history, ScrollUpdateKind.APPEND), tracker.consume(scrollingInitial))

        val grown =
            placeable(
                lines = history + listOf("i1", "i2", "i3", "s1"),
                segmentHeights = listOf(2, 3, 1),
            )
        val (scrollingGrown, activeGrown) =
            splitContentForRendering(
                grown,
                activeAreaHeight = 3,
                committedLineCount = scrollingInitial.size,
            )
        assertEquals(listOf("h1", "h2", "i1"), scrollingGrown)
        assertEquals(listOf("i2", "i3", "s1"), activeGrown)
        assertEquals(ScrollUpdate(listOf("i1"), ScrollUpdateKind.APPEND), tracker.consume(scrollingGrown))
    }

    @Test
    fun `palette toggle does not reset scrolling content`() {
        val tracker = ScrollingContentTracker()
        val history = listOf("h1", "h2", "h3")

        val withoutPalette =
            placeable(
                lines = history + listOf("i1", "status"),
                segmentHeights = listOf(3, 1, 1),
            )
        val (scrollingInitial, activeInitial) =
            splitContentForRendering(
                withoutPalette,
                activeAreaHeight = 3,
                committedLineCount = 0,
            )
        assertEquals(listOf("h1", "h2"), scrollingInitial)
        assertEquals(listOf("h3", "i1", "status"), activeInitial)
        assertEquals(
            ScrollUpdate(listOf("h1", "h2"), ScrollUpdateKind.APPEND),
            tracker.consume(scrollingInitial),
        )

        val withPalette =
            placeable(
                lines = history + listOf("i1", "p1", "p2"),
                segmentHeights = listOf(3, 1, 2),
            )
        val (scrollingPalette, activePalette) =
            splitContentForRendering(
                withPalette,
                activeAreaHeight = 3,
                committedLineCount = scrollingInitial.size,
            )
        assertEquals(history, scrollingPalette)
        assertEquals(listOf("i1", "p1", "p2"), activePalette)
        assertEquals(
            ScrollUpdate(listOf("h3"), ScrollUpdateKind.APPEND),
            tracker.consume(scrollingPalette),
        )
    }

    @Test
    fun `submit after long input keeps active area visible`() {
        val history = listOf("h1", "h2", "h3")
        val tracker = ScrollingContentTracker()

        val beforeSubmit =
            placeable(
                lines = history + listOf("i1", "i2", "i3", "i4", "i5", "i6", "i7", "i8", "status"),
                segmentHeights = listOf(3, 8, 1),
            )
        val (scrollingBeforeSubmit, activeBeforeSubmit) =
            splitContentForRendering(
                beforeSubmit,
                activeAreaHeight = 4,
                committedLineCount = 0,
            )
        assertEquals(listOf("i6", "i7", "i8", "status"), activeBeforeSubmit)
        tracker.consume(scrollingBeforeSubmit)

        val processingAfterSubmit =
            placeable(
                lines = history + listOf("spin1", "spin2", "input", "status"),
                segmentHeights = listOf(3, 2, 1, 1),
            )
        val (scrollingAfterSubmit, activeAfterSubmit) =
            splitContentForRendering(
                processingAfterSubmit,
                activeAreaHeight = 4,
                committedLineCount = scrollingBeforeSubmit.size,
            )

        assertEquals(history, scrollingAfterSubmit)
        assertEquals(listOf("spin1", "spin2", "input", "status"), activeAfterSubmit)
    }

    @Test
    fun `selection rewrite in scrolling region requests rewrite`() {
        val tracker = ScrollingContentTracker()
        val initial = listOf("> New", "  Dark", "  Silas", "status")
        val changed = listOf("  New", "> Dark", "  Silas", "status")

        tracker.consume(initial).let { update ->
            assertEquals(ScrollUpdate(initial, ScrollUpdateKind.APPEND), update)
        }
        tracker.consume(changed).let { update ->
            assertEquals(ScrollUpdate(changed, ScrollUpdateKind.REWRITE), update)
        }
    }
}
