package io.github.darkryh.dispatch.update

import java.io.IOException
import java.io.InputStream

interface CommandRunner {
    fun run(vararg args: String): CommandResult
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

/**
 * Default [CommandRunner] backed by [ProcessBuilder].
 *
 * Public so applications can hand it to the command-based providers
 * (BrewUpdateProvider, ScoopUpdateProvider, AptUpdateProvider) without writing their own
 * process plumbing.
 */
class SystemCommandRunner : CommandRunner {
    override fun run(vararg args: String): CommandResult {
        var process: Process? = null
        return try {
            val started =
                ProcessBuilder(*args)
                    .redirectErrorStream(false)
                    .start()
            process = started

            // Drain stdout and stderr on dedicated threads. Reading them sequentially can deadlock
            // if the child fills the stderr pipe buffer while we block on stdout. Draining on
            // separate threads also lets the calling thread block on the *interruptible*
            // waitFor() -- a blocking InputStream.read() on a process pipe is NOT interruptible,
            // so reading on the calling thread would ignore Thread.interrupt() entirely.
            val stdoutCapture = StreamCapture(started.inputStream)
            val stderrCapture = StreamCapture(started.errorStream)
            val stdoutThread =
                Thread(stdoutCapture, "dispatch-cmd-stdout").apply {
                    isDaemon = true
                    start()
                }
            val stderrThread =
                Thread(stderrCapture, "dispatch-cmd-stderr").apply {
                    isDaemon = true
                    start()
                }

            val exitCode = started.waitFor()
            stdoutThread.join()
            stderrThread.join()
            CommandResult(exitCode, stdoutCapture.text, stderrCapture.text)
        } catch (exception: IOException) {
            CommandResult(-1, "", exception.message.orEmpty())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult(-1, "", exception.message.orEmpty())
        } finally {
            val current = process
            if (current != null && current.isAlive) {
                current.destroyForcibly()
            }
        }
    }

    private class StreamCapture(
        private val stream: InputStream,
    ) : Runnable {
        @Volatile
        var text: String = ""
            private set

        override fun run() {
            text = stream.readTextSafely()
        }
    }
}

private fun InputStream.readTextSafely(): String =
    try {
        bufferedReader().use { it.readText() }
    } catch (_: IOException) {
        ""
    }
