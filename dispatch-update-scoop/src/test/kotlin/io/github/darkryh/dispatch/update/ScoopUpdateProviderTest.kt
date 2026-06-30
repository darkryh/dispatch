package io.github.darkryh.dispatch.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoopUpdateProviderTest {
    @Test
    fun `parses latest version from scoop status`() {
        val output =
            """
            Name  Installed Version  Latest Version  Missing Info
            ----  -----------------  -------------  ------------
            xtory 1.0.0              1.2.0
            """.trimIndent()
        val runner = RecordingCommandRunner(mapOf("scoop status" to CommandResult(0, output, "")))
        val provider = ScoopUpdateProvider("xtory", runner)

        val version = runBlocking { provider.latestVersion() }

        assertEquals("1.2.0", version)
    }

    @Test
    fun `returns null when no outdated apps`() {
        val output = "No outdated apps"
        val runner = RecordingCommandRunner(mapOf("scoop status" to CommandResult(0, output, "")))
        val provider = ScoopUpdateProvider("xtory", runner)

        val version = runBlocking { provider.latestVersion() }

        assertNull(version)
    }

    @Test
    fun `refreshes before status when configured`() {
        val output =
            """
            Name  Installed Version  Latest Version  Missing Info
            ----  -----------------  -------------  ------------
            xtory 1.0.0              2.0.0
            """.trimIndent()
        val runner =
            RecordingCommandRunner(
                mapOf(
                    "scoop update" to CommandResult(0, "", ""),
                    "scoop status" to CommandResult(0, output, ""),
                ),
            )
        val provider = ScoopUpdateProvider("xtory", runner, refreshBeforeCheck = true)

        val version = runBlocking { provider.latestVersion() }

        assertEquals(listOf("scoop update", "scoop status"), runner.calls)
        assertEquals("2.0.0", version)
    }

    private class RecordingCommandRunner(
        private val responses: Map<String, CommandResult>,
    ) : CommandRunner {
        val calls = mutableListOf<String>()

        override fun run(vararg args: String): CommandResult {
            val key = args.joinToString(" ")
            calls.add(key)
            return responses[key] ?: CommandResult(1, "", "missing response")
        }
    }
}
