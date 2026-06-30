package io.github.darkryh.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderPipelineDecisionTest {
    @Test
    fun `explicit region rewrites when growth occurs inside existing body`() {
        val previous =
            RenderFrameSnapshot(
                scrollingLines = listOf("status: streaming", "message", "bottom border"),
                activeLines = listOf("> input"),
                hasExplicitRenderRegions = true,
                scrollingContentStartLine = 1,
            )
        val current =
            RenderFrameSnapshot(
                scrollingLines = listOf("status: idle", "message", "cancelled", "bottom border"),
                activeLines = listOf("> input"),
                hasExplicitRenderRegions = true,
                scrollingContentStartLine = 1,
            )

        val decision =
            classifyRenderDecision(
                previous = previous,
                current = current,
                scrollUpdate = ScrollUpdate.rewrite(current.scrollingLines),
            )

        assertEquals(RenderKind.FULL_REWRITE, decision.kind)
        assertEquals("non_prefix_scrolling_change", decision.reason)
    }

    @Test
    fun `explicit scrolling region appends growth despite mutable header rows`() {
        val previous =
            RenderFrameSnapshot(
                scrollingLines = listOf("status: idle", "body"),
                activeLines = listOf("> input"),
                hasExplicitRenderRegions = true,
                scrollingContentStartLine = 1,
            )
        val current =
            RenderFrameSnapshot(
                scrollingLines = listOf("status: streaming", "body", "new message", "response"),
                activeLines = listOf("> input"),
                hasExplicitRenderRegions = true,
                scrollingContentStartLine = 1,
            )

        val decision =
            classifyRenderDecision(
                previous = previous,
                current = current,
                scrollUpdate = ScrollUpdate.rewrite(current.scrollingLines),
            )

        assertEquals(RenderKind.APPEND_ONLY, decision.kind)
        assertEquals("structural_scrolling_growth", decision.reason)
        assertEquals(listOf("new message", "response"), decision.scrollUpdate.lines)
    }

    @Test
    fun `scrolling content shrink invalidates obsolete native scrollback`() {
        val previous =
            RenderFrameSnapshot(
                scrollingLines = listOf("message 1", "message 2", "message 3"),
                activeLines = listOf("> input", "status"),
            )
        val current =
            RenderFrameSnapshot(
                scrollingLines = emptyList(),
                activeLines = listOf("> input", "status"),
            )

        val decision =
            classifyRenderDecision(
                previous = previous,
                current = current,
                scrollUpdate = ScrollUpdate.rewrite(emptyList()),
            )

        assertEquals(RenderKind.FULL_REWRITE, decision.kind)
        assertEquals("scrolling_content_reset", decision.reason)
        assertTrue(decision.clearScrollback)
    }

    @Test
    fun `append-only decision remains when active area is unchanged`() {
        val previous =
            RenderFrameSnapshot(
                scrollingLines = listOf("h1", "h2"),
                activeLines = listOf("> input", "status"),
            )
        val current =
            RenderFrameSnapshot(
                scrollingLines = listOf("h1", "h2", "h3"),
                activeLines = listOf("> input", "status"),
            )

        val decision =
            classifyRenderDecision(
                previous = previous,
                current = current,
                scrollUpdate = ScrollUpdate.append(listOf("h3")),
            )

        assertEquals(RenderKind.APPEND_ONLY, decision.kind)
        assertEquals("append_only_scrolling_change", decision.reason)
        assertEquals(ScrollUpdateKind.APPEND, decision.scrollUpdate.kind)
    }

    @Test
    fun `active boundary transfer converts append into rewrite`() {
        val previous =
            RenderFrameSnapshot(
                scrollingLines = listOf("h1", "h2"),
                activeLines = listOf("h3", "> /", "status"),
            )
        val current =
            RenderFrameSnapshot(
                scrollingLines = listOf("h1", "h2", "h3"),
                activeLines = listOf("> /", "/story", "status"),
            )

        val decision =
            classifyRenderDecision(
                previous = previous,
                current = current,
                scrollUpdate = ScrollUpdate.append(listOf("h3")),
            )

        assertEquals(RenderKind.FULL_REWRITE, decision.kind)
        assertEquals("active_to_scrolling_boundary_shift", decision.reason)
        assertEquals(ScrollUpdateKind.REWRITE, decision.scrollUpdate.kind)
        assertEquals(current.scrollingLines, decision.scrollUpdate.lines)
    }

    @Test
    fun `active area change without boundary transfer stays append-only`() {
        val previous =
            RenderFrameSnapshot(
                scrollingLines = listOf("h1", "h2"),
                activeLines = listOf("> input", "status"),
            )
        val current =
            RenderFrameSnapshot(
                scrollingLines = listOf("h1", "h2", "h3"),
                activeLines = listOf("> changed", "status"),
            )

        val decision =
            classifyRenderDecision(
                previous = previous,
                current = current,
                scrollUpdate = ScrollUpdate.append(listOf("h3")),
            )

        assertEquals(RenderKind.APPEND_ONLY, decision.kind)
        assertEquals("append_only_scrolling_change", decision.reason)
        assertEquals(ScrollUpdateKind.APPEND, decision.scrollUpdate.kind)
    }
}
