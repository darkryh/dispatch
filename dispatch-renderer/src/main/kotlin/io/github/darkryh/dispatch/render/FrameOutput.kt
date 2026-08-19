package io.github.darkryh.dispatch.render

import java.io.BufferedOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * Owns the process's stdout so that one frame becomes one `write(2)`.
 *
 * The JDK builds `System.out` as
 * `PrintStream(BufferedOutputStream(FileOutputStream(FileDescriptor.out), 8192), autoFlush = true)`.
 * That 8 KB buffer is invisible from Kotlin and it is the reason a frame built in a single
 * `StringBuilder` and written with a single `print()` still leaves the process in pieces: measured
 * on JDK 21, a 68 200-byte frame emerges as nine `write(2)` calls, eight of them exactly 8192 bytes
 * long, split at arbitrary offsets — mid-row, and potentially mid-escape-sequence.
 *
 * A terminal is free to paint at any of those boundaries. Since Dispatch's frames begin by erasing
 * the rows they are about to repaint, a boundary landing between the erase and the repaint shows
 * the user a blank screen. That is the blink.
 *
 * Installing a stream with a buffer larger than any realistic frame collapses those nine writes to
 * one. It does not change a single byte of what is written, and it does not touch scrollback,
 * scrolling, or the alternate screen — it only changes how many syscalls carry the same bytes.
 */
object FrameOutput {
    /**
     * Comfortably larger than a full-screen truecolour frame. A 227x50 frame with heavy styling
     * measures well under 256 KB; a megabyte leaves room for the pathological cases without being
     * a meaningful allocation.
     */
    private const val BUFFER_BYTES: Int = 1 shl 20

    private var installed: Boolean = false
    private var originalOut: PrintStream? = null

    /**
     * Replaces `System.out` with a large-buffered, non-auto-flushing stream over the same file
     * descriptor.
     *
     * Auto-flush is disabled deliberately: with it enabled the JDK flushes on every string
     * containing a newline, which would defeat the buffer for exactly the multi-row frames this
     * exists to protect. The renderer flushes explicitly, once, at the end of each frame.
     *
     * Safe to call more than once; only the first call takes effect.
     */
    @Synchronized
    fun install() {
        if (installed) return
        originalOut = System.out
        System.setOut(
            PrintStream(
                BufferedOutputStream(FileOutputStream(FileDescriptor.out), BUFFER_BYTES),
                false,
                StandardCharsets.UTF_8.name(),
            ),
        )
        installed = true
    }

    /**
     * Restores the JDK's original `System.out`, flushing anything still buffered.
     *
     * Called on shutdown so that a host application that keeps running after Dispatch exits — the
     * embedded-TUI case — gets its normal console back.
     */
    @Synchronized
    fun uninstall() {
        if (!installed) return
        System.out.flush()
        originalOut?.let(System::setOut)
        originalOut = null
        installed = false
    }

    /** True when Dispatch owns stdout. */
    val isInstalled: Boolean
        @Synchronized get() = installed
}
