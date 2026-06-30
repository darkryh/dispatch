package io.github.darkryh.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollStateTest {
    @Test
    fun `initial offset is zero by default`() {
        val state = ScrollState()
        assertEquals(0, state.offset, "initial offset should be 0")
    }

    @Test
    fun `initial offset can be customized`() {
        val state = ScrollState(initialOffset = 5)
        state.contentHeight = 100
        state.viewportHeight = 20
        assertEquals(5, state.offset, "initial offset should be customized value")
    }

    @Test
    fun `scrollBy positive increases offset`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollBy(10)
        assertEquals(10, state.offset, "offset should increase by delta")
    }

    @Test
    fun `scrollBy negative decreases offset`() {
        val state = ScrollState(initialOffset = 20)
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollBy(-5)
        assertEquals(15, state.offset, "offset should decrease by delta")
    }

    @Test
    fun `scrollBy clamps to zero`() {
        val state = ScrollState(initialOffset = 5)
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollBy(-10)
        assertEquals(0, state.offset, "offset should not go below 0")
    }

    @Test
    fun `scrollBy clamps to maxOffset`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollBy(100)
        assertEquals(80, state.offset, "offset should not exceed maxOffset")
    }

    @Test
    fun `scrollTo sets exact offset`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollTo(50)
        assertEquals(50, state.offset, "offset should be set exactly")
    }

    @Test
    fun `scrollTo clamps to maxOffset`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollTo(200)
        assertEquals(80, state.offset, "offset should be clamped to maxOffset")
    }

    @Test
    fun `scrollTo clamps to zero`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollTo(-10)
        assertEquals(0, state.offset, "offset should be clamped to 0")
    }

    @Test
    fun `scrollToTop sets offset to zero`() {
        val state = ScrollState(initialOffset = 50)
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollToTop()
        assertEquals(0, state.offset, "offset should be 0")
    }

    @Test
    fun `scrollToBottom sets offset to maxOffset`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollToBottom()
        assertEquals(80, state.offset, "offset should be maxOffset")
    }

    @Test
    fun `maxOffset is contentHeight minus viewportHeight`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        assertEquals(80, state.maxOffset, "maxOffset should be contentHeight - viewportHeight")
    }

    @Test
    fun `maxOffset is zero when content fits viewport`() {
        val state = ScrollState()
        state.contentHeight = 10
        state.viewportHeight = 20

        assertEquals(0, state.maxOffset, "maxOffset should be 0 when content fits viewport")
    }

    @Test
    fun `canScrollUp is false at top`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        assertFalse(state.canScrollUp, "canScrollUp should be false at offset 0")
    }

    @Test
    fun `canScrollUp is true when not at top`() {
        val state = ScrollState(initialOffset = 10)
        state.contentHeight = 100
        state.viewportHeight = 20

        assertTrue(state.canScrollUp, "canScrollUp should be true when offset > 0")
    }

    @Test
    fun `canScrollDown is true when not at bottom`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        assertTrue(state.canScrollDown, "canScrollDown should be true when offset < maxOffset")
    }

    @Test
    fun `canScrollDown is false at bottom`() {
        val state = ScrollState(initialOffset = 80)
        state.contentHeight = 100
        state.viewportHeight = 20

        assertFalse(state.canScrollDown, "canScrollDown should be false at maxOffset")
    }

    @Test
    fun `canScrollDown is false when content fits viewport`() {
        val state = ScrollState()
        state.contentHeight = 10
        state.viewportHeight = 20

        assertFalse(state.canScrollDown, "canScrollDown should be false when content fits viewport")
    }

    @Test
    fun `scrollByPage scrolls by viewportHeight`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollByPage(1)
        assertEquals(20, state.offset, "should scroll down by one page")

        state.scrollByPage(-1)
        assertEquals(0, state.offset, "should scroll up by one page")
    }

    @Test
    fun `scrollByPage respects bounds`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollByPage(10) // try to scroll way past end
        assertEquals(80, state.offset, "should be clamped to maxOffset")
    }

    @Test
    fun `scrollToItem scrolls to make item visible`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        // Item heights: each item is 5 lines
        val itemHeights = List(20) { 5 }

        // Scroll to item 10 (starts at y=50)
        state.scrollToItem(10, itemHeights)
        assertTrue(state.offset <= 50, "should scroll to make item visible")
    }

    @Test
    fun `scrollToItem handles empty list`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollToItem(5, emptyList())
        assertEquals(0, state.offset, "should not crash on empty list")
    }

    @Test
    fun `scrollToItem handles negative index`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 20

        state.scrollToItem(-1, listOf(5, 5, 5))
        assertEquals(0, state.offset, "should handle negative index gracefully")
    }

    @Test
    fun `scrollToItem scrolls up if item is above viewport`() {
        val state = ScrollState(initialOffset = 50)
        state.contentHeight = 100
        state.viewportHeight = 20

        val itemHeights = List(20) { 5 }

        // Scroll to item 2 (starts at y=10, which is above current offset 50)
        state.scrollToItem(2, itemHeights)
        assertEquals(10, state.offset, "should scroll up to show item 2")
    }

    @Test
    fun `zero viewportHeight uses minimum page of 1`() {
        val state = ScrollState()
        state.contentHeight = 100
        state.viewportHeight = 0

        state.scrollByPage(1)
        assertEquals(1, state.offset, "should scroll by minimum of 1")
    }
}
