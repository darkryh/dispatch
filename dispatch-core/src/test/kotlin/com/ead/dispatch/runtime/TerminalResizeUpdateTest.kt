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
}
