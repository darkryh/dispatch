package com.ead.dispatch.sample.e2e

import java.io.Closeable
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class PtyTerminalSession private constructor(
    private val process: Process,
) : Closeable {
    private val transcriptLock = Any()
    private val rawTranscript = StringBuilder()
    private val readerThread =
        Thread({ captureOutput() }, "dispatch-sample-pty-reader").apply {
            isDaemon = true
            start()
        }

    fun send(text: String) {
        check(process.isAlive) { "PTY process exited before input could be sent:\n${tail()}" }
        process.outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
        process.outputStream.flush()
    }

    fun sendEnter(): Unit = send("\r")

    fun sendTab(): Unit = send("\t")

    fun sendSpace(): Unit = send(" ")

    fun sendEscape(): Unit = send("\u001B")

    fun checkpoint(): Int = transcript().length

    fun rawCheckpoint(): Int = rawTranscript().length

    fun awaitRawRegex(
        expected: Regex,
        after: Int = 0,
        timeout: Duration = Duration.ofSeconds(8),
    ): MatchResult {
        var match: MatchResult? = null
        awaitRawCondition("pattern '$expected'", timeout) { output ->
            expected
                .find(output, startIndex = after)
                ?.also { match = it }
                ?.range
                ?.first
        }
        return checkNotNull(match)
    }

    fun awaitText(
        expected: String,
        after: Int = 0,
        timeout: Duration = Duration.ofSeconds(8),
    ): Int =
        awaitCondition("text '$expected'", timeout) { output ->
            output.indexOf(expected, startIndex = after).takeIf { it >= 0 }
        }

    fun awaitAnyText(
        expected: List<String>,
        after: Int = 0,
        timeout: Duration = Duration.ofSeconds(8),
    ): String {
        var matched = ""
        awaitCondition("any of ${expected.joinToString()}", timeout) { output ->
            expected.firstOrNull { output.indexOf(it, startIndex = after) >= 0 }?.let {
                matched = it
                output.indexOf(it, startIndex = after)
            }
        }
        return matched
    }

    fun transcript(): String = synchronized(transcriptLock) { normalize(rawTranscript.toString()) }

    private fun rawTranscript(): String = synchronized(transcriptLock) { rawTranscript.toString() }

    override fun close() {
        if (process.isAlive) {
            runCatching { send("\u0003\u0003") }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().forEach { it.destroy() }
                process.destroy()
            }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
        readerThread.join(1_000)
    }

    private fun captureOutput() {
        InputStreamReader(process.inputStream, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(2_048)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                synchronized(transcriptLock) {
                    rawTranscript.append(buffer, 0, count)
                }
            }
        }
    }

    private fun awaitCondition(
        description: String,
        timeout: Duration,
        condition: (String) -> Int?,
    ): Int {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val output = transcript()
            condition(output)?.let { return it }
            if (!process.isAlive) {
                error("PTY process exited while waiting for $description:\n${tail(output)}")
            }
            Thread.sleep(20)
        }
        error("Timed out after ${timeout.toMillis()} ms waiting for $description:\n${tail()}")
    }

    private fun awaitRawCondition(
        description: String,
        timeout: Duration,
        condition: (String) -> Int?,
    ): Int {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val output = rawTranscript()
            condition(output)?.let { return it }
            if (!process.isAlive) {
                error("PTY process exited while waiting for $description:\n${tail()}")
            }
            Thread.sleep(20)
        }
        error("Timed out after ${timeout.toMillis()} ms waiting for $description:\n${tail()}")
    }

    private fun tail(output: String = transcript()): String = output.takeLast(4_000)

    companion object {
        private val oscPattern = Regex("""\u001B\][^\u0007]*(?:\u0007|\u001B\\)""")
        private val csiPattern = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
        private val borderPattern = Regex("[│╭╮╰╯─]+")
        private val whitespacePattern = Regex("\\s+")

        fun start(): PtyTerminalSession {
            val binary =
                System.getProperty("dispatch.sample.binary")
                    ?: error("Missing dispatch.sample.binary; run the Gradle terminalE2eTest task")
            require(Files.isExecutable(Path.of(binary))) { "Sample executable does not exist: $binary" }

            val osName = System.getProperty("os.name").lowercase()
            val command =
                if (osName.contains("mac")) {
                    listOf("/usr/bin/script", "-q", "/dev/null", binary)
                } else {
                    listOf("/usr/bin/script", "-qefc", shellQuote(binary), "/dev/null")
                }

            val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
            processBuilder.environment()["TERM"] = "xterm-256color"
            processBuilder.environment()["COLUMNS"] = "100"
            processBuilder.environment()["LINES"] = "30"
            processBuilder.environment()["DISPATCH_SAMPLE_STREAM_SEED"] = "7"
            processBuilder.environment()["DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS"] = "80"
            processBuilder.environment()["DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS"] = "120"
            val process = processBuilder.start()
            return PtyTerminalSession(process)
        }

        private fun normalize(raw: String): String =
            raw
                .replace(oscPattern, "")
                .replace(csiPattern, "")
                .replace(borderPattern, " ")
                .replace("\r", "")
                .replace("\u0000", "")
                .replace(whitespacePattern, " ")

        private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
    }
}
