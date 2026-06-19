package com.ead.dispatch.sample.e2e

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertTrue

@Tag("terminal-e2e")
@EnabledOnOs(OS.MAC, OS.LINUX)
class TerminalApplicationE2eTest {
    @BeforeEach
    fun requirePtyLauncher() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")), "PTY launcher /usr/bin/script is unavailable")
    }

    @Test
    fun `rapid input survives PTY decoding and completes a visible stream`() {
        PtyTerminalSession.start().use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            terminal.sendEnter()
            terminal.awaitText("Simulated streaming chat")

            terminal.send("validate streaming")
            terminal.awaitText("validate streaming")
            Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
            val streamStart = terminal.checkpoint()
            terminal.sendEnter()

            terminal.awaitText("Receiving irregular local chunks...", after = streamStart)
            terminal.awaitText("Sample [streaming]", after = streamStart)
            val responsePrefix =
                terminal.awaitAnyText(
                    expected =
                        listOf(
                            "I received 'validate streaming'",
                            "Here is a local response to 'validate streaming'",
                            "The UI is handling 'validate streaming'",
                        ),
                    after = streamStart,
                )
            terminal.awaitText(
                expected = "Ready - no network or agent runtime is used.",
                after = streamStart,
                timeout = Duration.ofSeconds(12),
            )

            assertTrue(responsePrefix.contains("validate streaming"))
        }
    }

    @Test
    fun `escape cancels an active stream and preserves partial output`() {
        PtyTerminalSession.start().use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            terminal.sendEnter()
            terminal.awaitText("Simulated streaming chat")
            terminal.send("cancel this response")
            Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
            val streamStart = terminal.checkpoint()
            terminal.sendEnter()

            terminal.awaitText("Sample [streaming]", after = streamStart)
            terminal.awaitAnyText(
                expected = listOf("I received", "Here is a local response", "The UI is handling"),
                after = streamStart,
            )
            val cancelStart = terminal.checkpoint()
            terminal.sendEscape()

            terminal.awaitText("[cancelled]", after = cancelStart)
            terminal.awaitText("Ready - no network or agent runtime is used.", after = cancelStart)
        }
    }

    @Test
    fun `navigation and component controls react through the installed application`() {
        PtyTerminalSession.start().use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            terminal.sendTab()
            terminal.sendEnter()
            terminal.awaitText("Controls widget gallery")

            terminal.send("Ada")
            terminal.awaitText("Name: Ada")
            terminal.sendTab()
            terminal.send("secret")
            terminal.awaitText("Password length: 6")
            assertTrue(!terminal.transcript().contains("secret"), "Password value leaked into terminal output")

            val focusStart = terminal.rawCheckpoint()
            terminal.sendTab()
            val focusedButtonPattern = Regex("""\u001B\[[0-9;]*m(?:[^\u001B]|\u001B\[[0-9;]*m){0,80}Outlined""")
            terminal.awaitRawRegex(focusedButtonPattern, after = focusStart)
            terminal.sendEnter()
            terminal.awaitText("Count: 1")
        }
    }

    private companion object {
        const val PASTE_SUPPRESSION_SETTLE_MILLIS = 300L
    }
}
