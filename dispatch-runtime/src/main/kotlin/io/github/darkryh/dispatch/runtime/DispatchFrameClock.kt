package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.yield

/** Frame clock for terminal composition; terminal paint throttling remains in FrameScheduler. */
object DispatchFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        // Run the frame callback (recompose + applyChanges, animation steps) first so any
        // resulting frameScheduler.requestFrame() is enqueued before we relinquish the thread —
        // this preserves apply-before-paint and never interleaves a stale frame.
        val result = onFrame(System.nanoTime())
        // Cooperative reschedule AFTER apply: this is NOT a delay/FPS gate. Paints do not flow
        // through this clock — they go FrameScheduler -> onFrame -> composeAndRender, and
        // FrameScheduler remains the sole paint pacer. So this yield cannot change frame cadence;
        // it only stops a `while (isActive) { withFrameNanos {} }` loop (the idiomatic Compose
        // animation shape) from monopolizing the single limitedParallelism(1) UI thread and
        // starving recompose, render, and input.
        yield()
        return result
    }
}
