package io.github.darkryh.dispatch.sample.e2e

import java.io.Closeable
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class PtyTerminalSession private constructor(
    private val scenario: String,
    private val process: Process,
    private val artifactDirectory: Path,
    private val diagnosticsFile: Path,
) : Closeable {
    private val transcriptLock = Any()
    private val rawTranscript = StringBuilder()
    private val chunkTimeline = mutableListOf<TimedChunk>()
    private val keyTimeline = mutableListOf<TimedChunk>()
    private val rssSamples = mutableListOf<RssSample>()
    private val measurements = mutableListOf<String>()
    private val startedAtNanos = System.nanoTime()
    private val closed = AtomicBoolean(false)
    private val readerThread = thread("dispatch-sample-pty-reader") { captureOutput() }
    private val memoryThread = thread("dispatch-sample-memory-sampler") { captureMemory() }

    fun send(text: String) {
        check(process.isAlive) { "PTY process exited before input could be sent:\n${tail()}" }
        synchronized(keyTimeline) { keyTimeline += TimedChunk(elapsedNanos(), text.escapeControls()) }
        process.outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
        process.outputStream.flush()
    }

    fun sendEnter(): Unit = send("\r")

    fun sendTab(): Unit = send("\t")

    fun sendShiftTab(): Unit = send("\u001B[Z")

    fun sendSpace(): Unit = send(" ")

    fun sendEscape(): Unit = send("\u001B")

    fun sendUp(): Unit = send("\u001B[A")

    fun sendDown(): Unit = send("\u001B[B")

    fun sendLeft(): Unit = send("\u001B[D")

    fun sendRight(): Unit = send("\u001B[C")

    fun sendCtrlP(): Unit = send("\u0010")

    fun checkpoint(): Int = transcript().length

    fun rawCheckpoint(): Int = synchronized(transcriptLock) { rawTranscript.length }

    fun diagnosticCheckpoint(): Long = if (Files.exists(diagnosticsFile)) Files.size(diagnosticsFile) else 0L

    fun awaitRawRegex(
        expected: Regex,
        after: Int = 0,
        timeout: Duration = DEFAULT_TIMEOUT,
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
        timeout: Duration = DEFAULT_TIMEOUT,
    ): Int =
        awaitCondition("text '$expected'", timeout) { output ->
            output.indexOf(expected, startIndex = after).takeIf { it >= 0 }
        }

    fun awaitAnyText(
        expected: List<String>,
        after: Int = 0,
        timeout: Duration = DEFAULT_TIMEOUT,
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

    fun awaitQuiet(
        period: Duration = Duration.ofMillis(250),
        timeout: Duration = DEFAULT_TIMEOUT,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var previousSize = -1
        var unchangedSince = System.nanoTime()
        while (System.nanoTime() < deadline) {
            val size = synchronized(transcriptLock) { rawTranscript.length }
            if (size != previousSize) {
                previousSize = size
                unchangedSince = System.nanoTime()
            } else if (System.nanoTime() - unchangedSince >= period.toNanos()) {
                return
            }
            Thread.sleep(20)
        }
        error("Terminal did not become quiet within ${timeout.toMillis()} ms:\n${tail()}")
    }

    fun transcript(): String = synchronized(transcriptLock) { normalize(rawTranscript.toString()) }

    fun diagnosticEvents(afterByte: Long = 0L): String {
        if (!Files.exists(diagnosticsFile)) return ""
        val bytes = Files.readAllBytes(diagnosticsFile)
        val offset = afterByte.coerceIn(0, bytes.size.toLong()).toInt()
        return String(bytes, offset, bytes.size - offset, StandardCharsets.UTF_8)
    }

    fun requestGarbageCollection(): String {
        val handle = findApplicationProcess() ?: error("Could not resolve the sample JVM process")
        val javaCommand = (handle.info().command().orElse("")) ?: ""
        val jcmd =
            runCatching { Path.of(javaCommand).parent.resolve("jcmd") }
                .getOrNull()
                ?.takeIf(Files::isExecutable)
                ?.toString()
                ?: "jcmd"
        val command = ProcessBuilder(jcmd, handle.pid().toString(), "GC.run").redirectErrorStream(true).start()
        val output = command.inputStream.bufferedReader().readText()
        check(command.waitFor(10, TimeUnit.SECONDS) && command.exitValue() == 0) { "jcmd GC.run failed: $output" }
        synchronized(measurements) { measurements += "gc pid=${handle.pid()} output=${output.trim()}" }
        return output
    }

    fun resize(
        columns: Int,
        lines: Int,
    ) {
        require(columns > 0 && lines > 0)
        val handle = findApplicationProcess() ?: error("Could not resolve the sample process for resize")
        val tty = resolveTerminalDevice(handle)
        val osName = System.getProperty("os.name").lowercase()
        val command =
            if (osName.contains("mac")) {
                listOf("/bin/stty", "-f", tty, "cols", columns.toString(), "rows", lines.toString())
            } else {
                listOf("/bin/stty", "-F", tty, "cols", columns.toString(), "rows", lines.toString())
            }
        val resize = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = resize.inputStream.bufferedReader().readText()
        check(resize.waitFor(5, TimeUnit.SECONDS) && resize.exitValue() == 0) {
            "Could not resize PTY $tty to ${columns}x$lines: $output"
        }
        synchronized(keyTimeline) { keyTimeline += TimedChunk(elapsedNanos(), "<RESIZE:${columns}x$lines>") }
    }

    fun latestHeapUsedBytes(): Long? =
        Regex("\"heapUsedBytes\":(\\d+)")
            .findAll(diagnosticEvents())
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    fun recordMeasurement(
        name: String,
        value: Long,
    ) {
        synchronized(measurements) { measurements += "$name=$value" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
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
        memoryThread.join(1_000)
        writeArtifacts()
    }

    private fun captureOutput() {
        InputStreamReader(process.inputStream, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(2_048)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val text = String(buffer, 0, count)
                synchronized(transcriptLock) {
                    rawTranscript.append(text)
                    chunkTimeline += TimedChunk(elapsedNanos(), text.escapeControls())
                }
            }
        }
    }

    private fun captureMemory() {
        while (!closed.get() && process.isAlive) {
            val handle = findApplicationProcess()
            val rssKb = handle?.let(::readRssKb)
            if (handle != null && rssKb != null) {
                synchronized(rssSamples) { rssSamples += RssSample(elapsedNanos(), handle.pid(), rssKb) }
            }
            Thread.sleep(500)
        }
    }

    private fun findApplicationProcess(): ProcessHandle? {
        val descendants = process.descendants().filter(ProcessHandle::isAlive).toList()
        return descendants.firstOrNull { handle ->
            val command = (handle.info().command().orElse("")) ?: ""
            val arguments = (handle.info().arguments().orElse(emptyArray()) ?: emptyArray()).joinToString(" ")
            command.contains("java", ignoreCase = true) || arguments.contains("dispatch-sample")
        } ?: descendants.lastOrNull()
    }

    private fun readRssKb(handle: ProcessHandle): Long? {
        val procStatus = Path.of("/proc", handle.pid().toString(), "status")
        if (Files.isReadable(procStatus)) {
            return Files
                .readAllLines(procStatus)
                .firstOrNull { it.startsWith("VmRSS:") }
                ?.substringAfter("VmRSS:")
                ?.trim()
                ?.substringBefore(' ')
                ?.toLongOrNull()
        }
        return runCatching {
            val sampler = ProcessBuilder("/bin/ps", "-o", "rss=", "-p", handle.pid().toString()).start()
            sampler.inputStream.bufferedReader().readText().trim().toLongOrNull().also {
                sampler.waitFor(1, TimeUnit.SECONDS)
            }
        }.getOrNull()
    }

    private fun resolveTerminalDevice(handle: ProcessHandle): String {
        val procFd = Path.of("/proc", handle.pid().toString(), "fd", "0")
        if (Files.exists(procFd)) {
            return Files.readSymbolicLink(procFd).toString()
        }
        val lsof = Path.of("/usr/sbin/lsof").takeIf(Files::isExecutable)?.toString() ?: "lsof"
        val command = ProcessBuilder(lsof, "-a", "-p", handle.pid().toString(), "-d", "0", "-Fn").start()
        val output = command.inputStream.bufferedReader().readLines()
        check(command.waitFor(5, TimeUnit.SECONDS) && command.exitValue() == 0) {
            "Could not inspect terminal device for pid ${handle.pid()}"
        }
        return output.firstOrNull { it.startsWith("n/dev/") }?.removePrefix("n")
            ?: error("No terminal device found for pid ${handle.pid()}: ${output.joinToString()}")
    }

    private fun writeArtifacts() {
        Files.createDirectories(artifactDirectory)
        val raw = synchronized(transcriptLock) { rawTranscript.toString() }
        Files.writeString(artifactDirectory.resolve("terminal.raw.ansi"), raw)
        Files.writeString(artifactDirectory.resolve("terminal.normalized.txt"), normalize(raw))
        Files.writeString(
            artifactDirectory.resolve("terminal-chunks.tsv"),
            buildString {
                appendLine("elapsedNanos\tdata")
                synchronized(transcriptLock) { chunkTimeline.forEach { appendLine("${it.elapsedNanos}\t${it.data}") } }
            },
        )
        Files.writeString(
            artifactDirectory.resolve("keys.tsv"),
            buildString {
                appendLine("elapsedNanos\tdata")
                synchronized(keyTimeline) { keyTimeline.forEach { appendLine("${it.elapsedNanos}\t${it.data}") } }
            },
        )
        Files.writeString(
            artifactDirectory.resolve("rss.csv"),
            buildString {
                appendLine("elapsedNanos,pid,rssKb")
                synchronized(rssSamples) { rssSamples.forEach { appendLine("${it.elapsedNanos},${it.pid},${it.rssKb}") } }
            },
        )
        Files.writeString(
            artifactDirectory.resolve("memory-checkpoints.txt"),
            synchronized(measurements) { measurements.joinToString(separator = "\n", postfix = "\n") },
        )
        val measurementText = synchronized(measurements) { measurements.joinToString(separator = "\n", postfix = "\n") }
        val report = TerminalReliabilityReport.analyze(scenario, raw, diagnosticsFile, rssSamples.toList())
        Files.writeString(artifactDirectory.resolve("summary.json"), report.toJson())
        Files.writeString(artifactDirectory.resolve("summary.txt"), report.toText() + measurementText)
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
            checkAlive(description)
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
            val output = synchronized(transcriptLock) { rawTranscript.toString() }
            condition(output)?.let { return it }
            checkAlive(description)
            Thread.sleep(20)
        }
        error("Timed out after ${timeout.toMillis()} ms waiting for $description:\n${tail()}")
    }

    private fun checkAlive(description: String) {
        if (!process.isAlive) error("PTY process exited while waiting for $description:\n${tail()}")
    }

    private fun tail(): String = transcript().takeLast(4_000)

    private fun elapsedNanos(): Long = System.nanoTime() - startedAtNanos

    companion object {
        private val oscPattern = Regex("""\u001B][^\u0007]*(?:\u0007|\u001B\\)""")
        private val csiPattern = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
        private val borderPattern = Regex("[│╭╮╰╯─]+")
        private val whitespacePattern = Regex("\\s+")
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(10)

        fun start(
            scenario: String,
            columns: Int = 100,
            lines: Int = 30,
            environment: Map<String, String> = emptyMap(),
        ): PtyTerminalSession {
            val binary =
                System.getProperty("dispatch.sample.binary")
                    ?: error("Missing dispatch.sample.binary; run the Gradle terminalE2eTest task")
            require(Files.isExecutable(Path.of(binary))) { "Sample executable does not exist: $binary" }

            val reportRoot =
                System
                    .getProperty("dispatch.sample.reportDir")
                    ?.let(Path::of)
                    ?: Path.of("build", "reports", "terminal-reliability")
            val artifactDirectory = reportRoot.resolve(scenario.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            Files.createDirectories(artifactDirectory)
            val diagnostics = artifactDirectory.resolve("render-diagnostics.jsonl")
            Files.deleteIfExists(diagnostics)

            val osName = System.getProperty("os.name").lowercase()
            val shellCommand = "stty cols $columns rows $lines; exec ${shellQuote(binary)}"
            val command =
                if (osName.contains("mac")) {
                    listOf("/usr/bin/script", "-q", "/dev/null", "/bin/sh", "-c", shellCommand)
                } else {
                    listOf("/usr/bin/script", "-qefc", shellCommand, "/dev/null")
                }
            val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
            processBuilder.environment().putAll(
                mapOf(
                    "TERM" to "xterm-256color",
                    "COLUMNS" to columns.toString(),
                    "LINES" to lines.toString(),
                    "DISPATCH_SAMPLE_STREAM_SEED" to "7",
                    "DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS" to "20",
                    "DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS" to "40",
                    "DISPATCH_DIAGNOSTICS_FILE" to diagnostics.toAbsolutePath().toString(),
                    "DISPATCH_SCENARIO" to scenario,
                ),
            )
            processBuilder.environment().putAll(environment)
            return PtyTerminalSession(scenario, processBuilder.start(), artifactDirectory, diagnostics)
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

        private fun thread(
            name: String,
            block: () -> Unit,
        ): Thread =
            Thread(block, name).apply {
                isDaemon = true
                start()
            }
    }
}

internal data class RssSample(
    val elapsedNanos: Long,
    val pid: Long,
    val rssKb: Long,
)

private data class TimedChunk(
    val elapsedNanos: Long,
    val data: String,
)

private fun String.escapeControls(): String =
    buildString {
        for (character in this@escapeControls) {
            when (character) {
                '\u001B' -> append("<ESC>")
                '\r' -> append("<CR>")
                '\n' -> append("<LF>")
                '\t' -> append("<TAB>")
                else -> append(character)
            }
        }
    }
