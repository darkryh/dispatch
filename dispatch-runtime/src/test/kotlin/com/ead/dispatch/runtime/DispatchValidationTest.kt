package com.ead.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DispatchValidationTest {
    @Test
    fun `requireArgument returns value when provided`() {
        val args = DispatchArgs(
            rawArgs = listOf("--start", "session"),
            flags = setOf("resume"),
            arguments = mapOf("start" to "session"),
        )

        CompositionLocalProvider(LocalDispatchArgs provides args) {
            assertEquals("session", requireArgument("start"))
            assertEquals(args, dispatchArgs())
        }
    }

    @Test
    fun `requireArgument throws when missing`() {
        val args = DispatchArgs(
            rawArgs = emptyList(),
            flags = emptySet(),
            arguments = emptyMap(),
        )

        CompositionLocalProvider(LocalDispatchArgs provides args) {
            assertFailsWith<IllegalStateException> {
                requireArgument("start")
            }
        }
    }

    @Test
    fun `requireFlag throws when missing`() {
        val args = DispatchArgs(
            rawArgs = emptyList(),
            flags = emptySet(),
            arguments = emptyMap(),
        )

        CompositionLocalProvider(LocalDispatchArgs provides args) {
            assertFailsWith<IllegalStateException> {
                requireFlag("resume")
            }
        }
    }
}
