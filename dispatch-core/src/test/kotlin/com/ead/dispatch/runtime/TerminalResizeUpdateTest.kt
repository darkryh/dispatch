package com.ead.dispatch.runtime

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TerminalResizeUpdateTest {
    @Test
    fun `no update when size is not dirty`() {
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = false,
                previousWidth = 120,
                previousHeight = 24,
                currentWidth = 80,
                currentHeight = 20,
            )

        update shouldBe
            TerminalSizeUpdate(
                width = 120,
                height = 24,
                reset = false,
                dirty = false,
            )
    }

    @Test
    fun `dirty size with same dimensions does not reset`() {
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = true,
                previousWidth = 120,
                previousHeight = 24,
                currentWidth = 120,
                currentHeight = 24,
            )

        update shouldBe
            TerminalSizeUpdate(
                width = 120,
                height = 24,
                reset = false,
                dirty = false,
            )
    }

    @Test
    fun `dirty size with change resets`() {
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = true,
                previousWidth = 120,
                previousHeight = 24,
                currentWidth = 80,
                currentHeight = 20,
            )

        update shouldBe
            TerminalSizeUpdate(
                width = 80,
                height = 20,
                reset = true,
                dirty = false,
            )
    }

    @Test
    fun `viewport scrolling lines keeps only visible tail`() {
        val visible =
            viewportScrollingLines(
                scrollingLines = listOf("s1", "s2", "s3", "s4", "s5"),
                activeLines = listOf("a1", "a2"),
                terminalHeight = 5,
            )

        visible shouldBe listOf("s3", "s4", "s5")
    }

    @Test
    fun `viewport scrolling lines empty when active area fills terminal`() {
        val visible =
            viewportScrollingLines(
                scrollingLines = listOf("s1", "s2"),
                activeLines = listOf("a1", "a2", "a3"),
                terminalHeight = 3,
            )

        visible shouldBe emptyList()
    }
}
