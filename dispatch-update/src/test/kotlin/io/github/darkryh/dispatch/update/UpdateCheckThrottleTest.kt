package io.github.darkryh.dispatch.update

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class UpdateCheckThrottleTest {
    @BeforeTest
    fun reset() {
        UpdateCheckThrottle.reset()
    }

    @AfterTest
    fun cleanup() {
        UpdateCheckThrottle.reset()
    }

    @Test
    fun `second check within interval is throttled`() {
        val interval = 24.hours
        val first = UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 0)
        val second = UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 1_000)

        assertTrue(first)
        assertFalse(second)
    }

    @Test
    fun `check after interval is allowed`() {
        val interval = 24.hours
        UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 0)
        val later = UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = interval.inWholeMilliseconds + 1)

        assertTrue(later)
    }

    @Test
    fun `distinct keys throttle independently`() {
        val interval = 24.hours
        assertTrue(UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 0))
        assertTrue(UpdateCheckThrottle.shouldCheck("2.0.0", interval, now = 0))
    }

    @Test
    fun `provider latestVersion invoked only once across remounts`() = runBlocking {
        // Simulates the LaunchedEffect body running twice within the throttle window (as on a
        // remount). With the throttle persisted outside composition, the provider must be hit once.
        val provider = CountingProvider("9.9.9")
        val interval = 24.hours

        repeat(2) {
            if (UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 0)) {
                provider.latestVersion()
            }
        }

        assertEquals(1, provider.invocations)
    }

    private class CountingProvider(private val version: String?) : UpdateProvider {
        var invocations = 0
            private set

        override suspend fun latestVersion(): String? {
            invocations++
            return version
        }
    }

    @Test
    fun `reset clears recorded checks`() {
        val interval = 24.hours
        UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 0)
        UpdateCheckThrottle.reset()
        assertTrue(UpdateCheckThrottle.shouldCheck("1.0.0", interval, now = 1))
    }
}
