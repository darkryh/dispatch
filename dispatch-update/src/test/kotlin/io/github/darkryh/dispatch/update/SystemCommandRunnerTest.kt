package io.github.darkryh.dispatch.update

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemCommandRunnerTest {
    private val isWindows =
        System
            .getProperty("os.name")
            .orEmpty()
            .lowercase()
            .contains("win")

    @Test
    fun `non existent command returns minus one without throwing`() {
        val runner = SystemCommandRunner()

        val result = runner.run("this-command-definitely-does-not-exist-xyz", "arg")

        assertEquals(-1, result.exitCode)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `short command succeeds`() {
        if (isWindows) return
        val runner = SystemCommandRunner()

        val result = runner.run("/bin/echo", "hello")

        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout.trim())
    }

    @Test
    fun `large stderr output completes without deadlock`() {
        if (isWindows) return
        val runner = SystemCommandRunner()

        // Emit a large stream on stderr only. Sequential stdout-then-stderr reading would block
        // forever once the stderr pipe buffer fills; concurrent draining must complete quickly.
        val script = "i=0; while [ \$i -lt 20000 ]; do echo \"line-\$i\" 1>&2; i=\$((i+1)); done"
        val finished = AtomicReference<CommandResult?>(null)
        val latch = CountDownLatch(1)
        val worker =
            Thread {
                finished.set(runner.run("/bin/sh", "-c", script))
                latch.countDown()
            }
        worker.start()

        assertTrue(latch.await(20, TimeUnit.SECONDS), "command deadlocked draining stderr")
        val result = assertNotNull(finished.get())
        assertEquals(0, result.exitCode)
        assertTrue(result.stderr.contains("line-19999"))
        assertEquals("", result.stdout.trim())
    }

    @Test
    fun `interrupt sets interrupt flag and does not leave a live process`() {
        if (isWindows) return
        val runner = SystemCommandRunner()
        val interruptObserved = AtomicReference(false)
        val done = CountDownLatch(1)

        val worker =
            Thread {
                // sleep 30s child; the worker thread is interrupted while waitFor() blocks.
                runner.run("/bin/sh", "-c", "sleep 30")
                interruptObserved.set(Thread.currentThread().isInterrupted)
                done.countDown()
            }
        worker.start()
        // Give the process time to start.
        Thread.sleep(500)
        worker.interrupt()

        assertTrue(done.await(20, TimeUnit.SECONDS), "interrupted run did not return")
        assertTrue(interruptObserved.get(), "interrupt flag should be restored")
    }
}
