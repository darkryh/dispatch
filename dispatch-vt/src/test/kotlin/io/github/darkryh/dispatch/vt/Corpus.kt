package io.github.darkryh.dispatch.vt

import java.io.File

/**
 * A recorded PTY session: the exact bytes Dispatch wrote, the chunk boundaries at which they
 * arrived, and the render decisions the app logged while writing them.
 *
 * These artifacts are already produced by `dispatch-sample`'s PTY suite. Until now nothing read
 * them back.
 */
data class CorpusScenario(
    val name: String,
    val width: Int,
    val height: Int,
    val chunks: List<OutputChunk>,
    val renderDecisions: List<RenderDecision>,
    /** Count of `terminal_write` events the app logged — one per `flushBuffer` call. */
    val terminalWrites: Int,
) {
    /**
     * Group chunks into frames by arrival gap.
     *
     * Chunks separated by less than [gapNanos] belong to the same repaint; a larger gap means the
     * renderer went quiet and the next burst is a new frame. At 16 ms — one 60 Hz refresh — this
     * reproduces the app's own `terminalWrites` count exactly, which is what validates the choice.
     */
    fun frames(gapNanos: Long = FRAME_GAP_NANOS): List<Frame> {
        if (chunks.isEmpty()) return emptyList()
        val frames = mutableListOf<Frame>()
        var current = mutableListOf(chunks.first())
        for (i in 1 until chunks.size) {
            if (chunks[i].elapsedNanos - chunks[i - 1].elapsedNanos >= gapNanos) {
                frames += Frame(frames.size, current)
                current = mutableListOf()
            }
            current += chunks[i]
        }
        if (current.isNotEmpty()) frames += Frame(frames.size, current)
        return frames
    }

    companion object {
        const val FRAME_GAP_NANOS: Long = 16_000_000L
    }
}

/** One `render_decision` / `render_frame` entry from the app's diagnostics sidecar. */
data class RenderDecision(
    val kind: String,
    val reason: String,
    val clearScrollback: Boolean,
)

object Corpus {
    /** Directory of recorded scenarios, supplied by the Gradle test task. */
    fun directory(): File? =
        System.getProperty("dispatch.vt.corpusDir")
            ?.let(::File)
            ?.takeIf { it.isDirectory }

    fun scenarios(): List<CorpusScenario> {
        val root = directory() ?: return emptyList()
        return root
            .listFiles { file: File -> file.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { load(it) }
    }

    fun load(dir: File): CorpusScenario? {
        val chunkFile = File(dir, "terminal-chunks.tsv")
        if (!chunkFile.isFile) return null
        val chunks = readChunks(chunkFile)
        if (chunks.isEmpty()) return null

        val diagnostics = File(dir, "render-diagnostics.jsonl")
        val decisions = if (diagnostics.isFile) readDecisions(diagnostics) else emptyList()
        val (width, height) = if (diagnostics.isFile) readGeometry(diagnostics) else DEFAULT_GEOMETRY

        val writes =
            if (diagnostics.isFile) {
                diagnostics.readLines().count { it.contains("\"event\":\"terminal_write\"") }
            } else {
                0
            }
        return CorpusScenario(dir.name, width, height, chunks, decisions, writes)
    }

    private fun readChunks(file: File): List<OutputChunk> =
        file
            .readLines()
            .drop(1)
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val nanos = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
                OutputChunk(unescape(line.substring(tab + 1)), nanos)
            }

    /** Reverses `PtyTerminalSession.escapeControls()`. */
    fun unescape(text: String): String =
        text
            .replace("<ESC>", "\u001B")
            .replace("<CR>", "\r")
            .replace("<LF>", "\n")
            .replace("<TAB>", "\t")

    private fun readDecisions(file: File): List<RenderDecision> =
        file.readLines().mapNotNull { line ->
            if (!line.contains("\"event\":\"render_frame\"")) return@mapNotNull null
            RenderDecision(
                kind = string(line, "kind") ?: return@mapNotNull null,
                reason = string(line, "reason").orEmpty(),
                clearScrollback = line.contains("\"clearScrollback\":true"),
            )
        }

    private fun readGeometry(file: File): Pair<Int, Int> {
        for (line in file.readLines()) {
            if (!line.contains("\"event\":\"render_frame\"")) continue
            val w = int(line, "terminalWidth")
            val h = int(line, "terminalHeight")
            if (w != null && h != null) return w to h
        }
        return DEFAULT_GEOMETRY
    }

    private fun string(line: String, key: String): String? =
        Regex("\"$key\":\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1)

    private fun int(line: String, key: String): Int? =
        Regex("\"$key\":\\s*(-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()

    private val DEFAULT_GEOMETRY = 100 to 30
}
