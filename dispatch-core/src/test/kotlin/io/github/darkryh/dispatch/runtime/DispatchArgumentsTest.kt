package io.github.darkryh.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DispatchArgumentsTest {
    @Test
    fun `single dash long flags and arguments are recognized`() {
        val config =
            DispatchConfig().apply {
                flag("resume")
                argument("start")
            }

        val parsed = parseDispatchArguments(arrayOf("-resume", "-start", "session"), config)

        assertTrue("resume" in parsed.flags)
        assertEquals("session", parsed.arguments["start"])
    }

    @Test
    fun `short flags remain supported`() {
        val config =
            DispatchConfig().apply {
                flag("resume", shortName = 'r')
            }

        val parsed = parseDispatchArguments(arrayOf("-r"), config)

        assertTrue("resume" in parsed.flags)
    }
}
