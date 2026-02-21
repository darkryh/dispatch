package com.ead.dispatch.sample.presentation.chat_mode.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatProgressAnimationTest {

    @Test
    fun `formats zero seconds`() {
        assertEquals("0s", formatProcessingDuration(0))
    }

    @Test
    fun `formats seconds under one minute`() {
        assertEquals("59s", formatProcessingDuration(59))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("1m 05s", formatProcessingDuration(65))
    }

    @Test
    fun `formats hours minutes and seconds`() {
        assertEquals("1h 01m 01s", formatProcessingDuration(3_661))
    }

    @Test
    fun `clamps negative values to zero`() {
        assertEquals("0s", formatProcessingDuration(-12))
    }
}
