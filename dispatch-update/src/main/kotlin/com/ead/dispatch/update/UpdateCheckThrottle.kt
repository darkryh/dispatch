package com.ead.dispatch.update

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Tracks the last update-check timestamp outside of Compose state so that remounting a composable
 * does not reset the throttle and re-trigger external commands / HTTP requests on every remount.
 *
 * Keyed by the logical check identity (e.g. the app version) so distinct apps throttle independently.
 */
object UpdateCheckThrottle {
    private val lastCheckAt = ConcurrentHashMap<String, Long>()

    /**
     * Returns true and records [now] if a check is allowed for [key]; returns false if the previous
     * check for [key] happened within [interval].
     */
    fun shouldCheck(
        key: String,
        interval: Duration,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val previous = lastCheckAt[key]
        if (previous != null && now - previous < interval.inWholeMilliseconds) {
            return false
        }
        lastCheckAt[key] = now
        return true
    }

    internal fun reset() {
        lastCheckAt.clear()
    }
}
