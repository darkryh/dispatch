package com.ead.dispatch.runtime

import com.ead.dispatch.render.OutputCapture
import com.ead.dispatch.render.TerminalRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.io.PrintStream

internal class SystemOutputRouter(
    private val renderer: TerminalRenderer,
    private val scope: CoroutineScope,
    private val enabled: Boolean,
) {
    private var originalOut: PrintStream? = null
    private var originalErr: PrintStream? = null
    private var job: Job? = null
    private var channel: Channel<List<String>>? = null
    private var outStream: RoutedOutputStream? = null
    private var errStream: RoutedOutputStream? = null

    fun start() {
        if (!enabled) return
        if (originalOut != null || originalErr != null) return

        val outputChannel = Channel<List<String>>(Channel.UNLIMITED)
        channel = outputChannel

        job = scope.launch(Dispatchers.IO) {
            for (lines in outputChannel) {
                renderer.appendScrollingContent(lines)
            }
        }

        originalOut = System.out
        originalErr = System.err

        outStream = RoutedOutputStream(outputChannel, originalOut!!)
        errStream = RoutedOutputStream(outputChannel, originalErr!!)

        System.setOut(PrintStream(outStream, true))
        System.setErr(PrintStream(errStream, true))
    }

    fun stop() {
        if (!enabled) return
        val out = originalOut
        val err = originalErr
        if (out != null) {
            System.setOut(out)
        }
        if (err != null) {
            System.setErr(err)
        }

        outStream?.flushPendingTo(out)
        errStream?.flushPendingTo(err)

        channel?.close()
        job?.cancel()
        channel = null
        job = null
        originalOut = null
        originalErr = null
        outStream = null
        errStream = null
    }

    private class RoutedOutputStream(
        private val channel: Channel<List<String>>,
        private val passthrough: OutputStream,
    ) : OutputStream() {
        private val buffer = StringBuilder()

        override fun write(b: Int) {
            if (OutputCapture.isSuppressed()) {
                passthrough.write(b)
                return
            }

            val c = b.toChar()
            when (c) {
                '\n', '\r' -> flushPending()
                else -> buffer.append(c)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) {
                write(b[i].toInt())
            }
        }

        private fun flushPending() {
            if (buffer.isEmpty()) return
            val line = buffer.toString()
            buffer.setLength(0)
            channel.trySend(listOf(line))
        }

        fun flushPendingTo(target: OutputStream?) {
            if (buffer.isEmpty()) return
            val line = buffer.toString()
            buffer.setLength(0)
            if (target != null) {
                target.write(line.toByteArray())
                target.flush()
            }
        }
    }
}
