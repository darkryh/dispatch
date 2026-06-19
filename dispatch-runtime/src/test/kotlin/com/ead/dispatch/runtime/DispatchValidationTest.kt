package com.ead.dispatch.runtime

import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DispatchValidationTest {
    @Test
    fun `requireArgument returns value when provided`() {
        val args =
            DispatchArgs(
                rawArgs = listOf("--start", "session"),
                flags = setOf("resume"),
                arguments = mapOf("start" to "session"),
            )

        var argument: String? = null
        var observedArgs: DispatchArgs? = null
        DispatchComposition().use { composition ->
            composition.setContent {
                CompositionLocalProvider(LocalDispatchArgs provides args) {
                    argument = requireArgument("start")
                    observedArgs = dispatchArgs()
                }
            }
        }
        assertEquals("session", argument)
        assertEquals(args, observedArgs)
    }

    @Test
    fun `requireArgument throws when missing`() {
        val args =
            DispatchArgs(
                rawArgs = emptyList(),
                flags = emptySet(),
                arguments = emptyMap(),
            )

        assertFailsWith<IllegalStateException> {
            args.requireArgument("start")
        }
    }

    @Test
    fun `requireFlag throws when missing`() {
        val args =
            DispatchArgs(
                rawArgs = emptyList(),
                flags = emptySet(),
                arguments = emptyMap(),
            )

        assertFailsWith<IllegalStateException> {
            args.requireFlag("resume")
        }
    }
}
