package com.ead.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderPipelineDecisionTest {
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
