package com.ead.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedStateHandleTest {
    @Test
    fun `remove clears saved state entry`() {
        val handle = SavedStateHandle()
        handle["key"] = "value"

        assertTrue(handle.contains("key"))

        handle.remove("key")

        assertFalse(handle.contains("key"))
        assertNull(handle.get<String>("key"))
    }
}
