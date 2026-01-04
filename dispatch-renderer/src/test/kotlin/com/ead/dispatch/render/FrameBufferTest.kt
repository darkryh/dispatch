package com.ead.dispatch.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameBufferTest {
    @Test
    fun `diff reports only changed ranges`() {
        val buffer = FrameBuffer(initialWidth = 6, initialHeight = 2)

        buffer.clear()
        buffer.write(0, 0, "hello")
        buffer.write(1, 1, "xy")

        val changes = buffer.diff()

        assertEquals(2, changes.size)
        assertTrue(changes.any { it.x == 0 && it.y == 0 && it.text == "hello" })
        assertTrue(changes.any { it.x == 1 && it.y == 1 && it.text == "xy" })
    }

    @Test
    fun `swap and copyCurrentToNext reset diffs`() {
        val buffer = FrameBuffer(initialWidth = 4, initialHeight = 1)

        buffer.clear()
        buffer.write(0, 0, "test")
        assertEquals(1, buffer.diff().size)

        buffer.swap()
        buffer.copyCurrentToNext()
        assertTrue(buffer.diff().isEmpty())
    }
}
