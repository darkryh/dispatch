package com.ead.dispatch.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrewUpdateProviderTest {
    @Test
    fun `reads stable version from brew info json`() {
        val runner = FakeCommandRunner(
            CommandResult(
                exitCode = 0,
                stdout = """
                {"formulae":[{"name":"xtory","versions":{"stable":"2.1.0"}}]}
                """.trimIndent(),
                stderr = "",
            )
        )
        val provider = BrewUpdateProvider("xtory", runner)

        val version = runBlocking { provider.latestVersion() }

        assertEquals("2.1.0", version)
    }

    @Test
    fun `returns null on command failure`() {
        val runner = FakeCommandRunner(CommandResult(exitCode = 1, stdout = "", stderr = "boom"))
        val provider = BrewUpdateProvider("xtory", runner)

        val version = runBlocking { provider.latestVersion() }

        assertNull(version)
    }

    private class FakeCommandRunner(private val result: CommandResult) : CommandRunner {
        override fun run(vararg args: String): CommandResult = result
    }
}
