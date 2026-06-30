package io.github.darkryh.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputEditorTest {
    private class FakeClock {
        var nowNanos: Long = 0L

        fun advanceMillis(millis: Long) {
            nowNanos += millis * 1_000_000
        }
    }

    private class EditorHarness {
        val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 80,
                height = 20,
                interactive = false,
            )
        var value: String = ""
        var cursor: Int = 0
        var submitted: String? = null
        var historyItems: List<String> = emptyList()

        val historyState = InputHistoryIndexState()
        private val pasteTracker = PasteTracker()
        private val pasteHeuristic = PasteHeuristic(inputNowNanos)
        val editor =
            InputEditor(
                getValue = { value },
                setValue = { value = it },
                getCursor = { cursor },
                setCursor = { cursor = it },
                historyIndexState = historyState,
                pasteTracker = pasteTracker,
                pasteHeuristic = pasteHeuristic,
            )

        init {
            editor.updateDependencies(
                onValueChange = { value = it },
                onSubmit = { submitted = it },
                onCursorPositionChange = { cursor = it },
                historyItems = { historyItems },
                terminal = terminal,
                contentWidth = { terminal.size.width },
            )
        }

        fun press(
            key: String,
            ctrl: Boolean = false,
            alt: Boolean = false,
            shift: Boolean = false,
        ) {
            editor.handleKeyEvent(KeyboardEvent(key, ctrl = ctrl, alt = alt, shift = shift))
        }
    }

    @Test
    fun `insert and backspace update value and cursor`() {
        val harness = EditorHarness()

        harness.press("h")
        harness.press("i")

        assertEquals("hi", harness.value)
        assertEquals(2, harness.cursor)

        harness.press("ArrowLeft")
        assertEquals(1, harness.cursor)

        harness.press("Backspace")
        assertEquals("i", harness.value)
        assertEquals(0, harness.cursor)
    }

    @Test
    fun `delete removes character ahead of cursor`() {
        val harness = EditorHarness()
        harness.value = "hi"
        harness.cursor = 0

        harness.press("Delete")

        assertEquals("i", harness.value)
        assertEquals(0, harness.cursor)
    }

    @Test
    fun `enter submits and clears input`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("h")
            harness.press("i")
            clock.advanceMillis(200)
            harness.press("Enter")

            assertEquals("hi", harness.submitted)
            assertEquals("", harness.value)
            assertEquals(0, harness.cursor)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `single character then enter submits`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("h")
            clock.advanceMillis(200)
            harness.press("Enter")

            assertEquals("h", harness.submitted)
            assertEquals("", harness.value)
            assertEquals(0, harness.cursor)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `shift enter inserts newline`() {
        val harness = EditorHarness()

        harness.press("h")
        harness.press("i")
        harness.press("Enter", shift = true)

        assertEquals("hi\n", harness.value)
        assertNull(harness.submitted)
    }

    @Test
    fun `history navigation restores draft`() {
        val harness = EditorHarness()
        harness.historyItems = listOf("one", "two")
        harness.historyState.index = harness.historyItems.size
        harness.value = "draft"
        harness.cursor = 0

        harness.press("ArrowUp")
        assertEquals("two", harness.value)

        harness.cursor = 0
        harness.press("ArrowUp")
        assertEquals("one", harness.value)

        harness.press("ArrowDown")
        assertEquals("two", harness.value)

        harness.press("ArrowDown")
        assertEquals("draft", harness.value)
    }

    @Test
    fun `paste burst suppresses immediate enter`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("a")
            clock.advanceMillis(1)
            harness.press("b")
            clock.advanceMillis(1)
            harness.press("c")
            clock.advanceMillis(1)
            harness.press("Enter")

            assertEquals("abc\n", harness.value)
            assertNull(harness.submitted)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `paste burst enter submits after window`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("a")
            clock.advanceMillis(1)
            harness.press("b")
            clock.advanceMillis(1)
            harness.press("c")
            clock.advanceMillis(200)
            harness.press("Enter")

            assertEquals("abc", harness.submitted)
            assertEquals("", harness.value)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `multiline paste suppresses enter within long window`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("one\ntwo")
            clock.advanceMillis(400)
            harness.press("Enter")

            assertEquals("one\ntwo\n", harness.value)
            assertNull(harness.submitted)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `multiline paste enter submits after long window`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("one\ntwo")
            clock.advanceMillis(700)
            harness.press("Enter")

            assertEquals("one\ntwo", harness.submitted)
            assertEquals("", harness.value)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `chunked paste inserts in order`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            val chunks = listOf("hel", "lo", "\n", "wor", "ld")
            for (chunk in chunks) {
                harness.press(chunk)
                clock.advanceMillis(40)
            }

            assertEquals("hello\nworld", harness.value)
            assertNull(harness.submitted)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `chunked paste then enter within window inserts newline`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("hel")
            clock.advanceMillis(40)
            harness.press("lo")
            clock.advanceMillis(40)
            harness.press("\n")
            clock.advanceMillis(40)
            harness.press("world")
            clock.advanceMillis(300)
            harness.press("Enter")

            assertEquals("hello\nworld\n", harness.value)
            assertNull(harness.submitted)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `chunked paste then enter after window submits`() {
        val clock = FakeClock()
        val previousClock = inputNowNanos
        inputNowNanos = { clock.nowNanos }
        try {
            val harness = EditorHarness()

            harness.press("hel")
            clock.advanceMillis(40)
            harness.press("lo")
            clock.advanceMillis(40)
            harness.press("\n")
            clock.advanceMillis(40)
            harness.press("world")
            clock.advanceMillis(700)
            harness.press("Enter")

            assertEquals("hello\nworld", harness.submitted)
            assertEquals("", harness.value)
        } finally {
            inputNowNanos = previousClock
        }
    }

    @Test
    fun `paste with delete and backspace keeps cursor consistent`() {
        val harness = EditorHarness()
        harness.value = "ab"
        harness.cursor = 1

        harness.press("X")
        harness.press("Backspace")
        harness.press("Delete")

        assertEquals("a", harness.value)
        assertEquals(1, harness.cursor)
    }

    @Test
    fun `history navigation clamps corrupted history index`() {
        val harness = EditorHarness()
        harness.historyItems = listOf("one", "two")
        harness.historyState.index = 99
        harness.value = "draft"
        harness.cursor = 0

        harness.press("ArrowUp")

        assertEquals("two", harness.value)
        assertEquals(3, harness.cursor)
    }
}
