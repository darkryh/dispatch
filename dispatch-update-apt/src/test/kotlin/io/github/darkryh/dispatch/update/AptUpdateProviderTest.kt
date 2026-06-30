package io.github.darkryh.dispatch.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AptUpdateProviderTest {
    @Test
    fun `parses candidate version from apt-cache policy`() {
        val output = """
            xtory:
              Installed: 1.0.0
              Candidate: 1.2.3
              Version table:
                 1.2.3 500
        """.trimIndent()
        val runner = FakeCommandRunner(CommandResult(0, output, ""))
        val provider = AptUpdateProvider("xtory", runner)

        val version = runBlocking { provider.latestVersion() }

        assertEquals("1.2.3", version)
    }

    @Test
    fun `returns null when candidate is none`() {
        val output = """
            xtory:
              Installed: (none)
              Candidate: (none)
        """.trimIndent()
        val runner = FakeCommandRunner(CommandResult(0, output, ""))
        val provider = AptUpdateProvider("xtory", runner)

        val version = runBlocking { provider.latestVersion() }

        assertNull(version)
    }

    private class FakeCommandRunner(private val result: CommandResult) : CommandRunner {
        override fun run(vararg args: String): CommandResult = result
    }
}
