package io.github.darkryh.dispatch.render

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Sidecar diagnostics for installed-terminal reliability tests.
 *
 * Diagnostics must never be written to stdout or stderr because either stream may share the PTY
 * that is being measured. Set `DISPATCH_DIAGNOSTICS_FILE` to enable JSON Lines output.
 */
object RenderDiagnostics {
    private val lock = ReentrantLock()
    private val startedAtNanos = System.nanoTime()
    private val lastMemorySampleNanos = AtomicLong(0L)
    private val outputPath: Path? =
        System
            .getenv("DISPATCH_DIAGNOSTICS_FILE")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)

    /**
     * Whether diagnostics output is active. Callers MUST guard the construction of any
     * per-frame `fields` map with this flag — when diagnostics are disabled (the production
     * default), [record] discards its argument, so building the map is pure wasted work on
     * the render hot path.
     */
    val isEnabled: Boolean
        get() = outputPath != null

    fun record(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val path = outputPath ?: return
        val values =
            linkedMapOf<String, Any?>(
                "elapsedNanos" to System.nanoTime() - startedAtNanos,
                "pid" to ProcessHandle.current().pid(),
                "event" to event,
            ).apply { putAll(fields) }
        val line =
            values.entries.joinToString(prefix = "{", postfix = "}\n") { (key, value) ->
                "\"${key.jsonEscape()}\":${value.toJsonValue()}"
            }

        lock.withLock {
            path.parent?.let(Files::createDirectories)
            Files.writeString(
                path,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
        }
    }

    fun recordMemoryIfDue(intervalNanos: Long = 500_000_000L) {
        if (outputPath == null) return
        val now = System.nanoTime()
        val previous = lastMemorySampleNanos.get()
        if (now - previous < intervalNanos || !lastMemorySampleNanos.compareAndSet(previous, now)) return
        val runtime = Runtime.getRuntime()
        record(
            event = "memory_sample",
            fields =
                mapOf(
                    "heapUsedBytes" to runtime.totalMemory() - runtime.freeMemory(),
                    "heapCommittedBytes" to runtime.totalMemory(),
                    "heapMaxBytes" to runtime.maxMemory(),
                    "threadCount" to Thread.activeCount(),
                ),
        )
    }

    private fun Any?.toJsonValue(): String =
        when (this) {
            null -> "null"
            is Number, is Boolean -> toString()
            else -> "\"${toString().jsonEscape()}\""
        }

    private fun String.jsonEscape(): String =
        buildString(length + 8) {
            for (character in this@jsonEscape) {
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
        }
}
