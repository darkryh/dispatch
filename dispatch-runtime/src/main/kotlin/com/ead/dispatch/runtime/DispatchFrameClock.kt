package com.ead.dispatch.runtime

import androidx.compose.runtime.MonotonicFrameClock

/** Frame clock for terminal composition; terminal paint throttling remains in FrameScheduler. */
object DispatchFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        onFrame(System.nanoTime())
}
