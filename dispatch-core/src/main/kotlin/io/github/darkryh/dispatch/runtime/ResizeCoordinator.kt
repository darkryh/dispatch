package io.github.darkryh.dispatch.runtime

import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.render.RenderDiagnostics
import sun.misc.Signal

internal class ResizeCoordinator(
    private val requestRecomposition: () -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
    private val settleNanos: Long = DEFAULT_RESIZE_SETTLE_NANOS,
) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    @Volatile
    private var sizeDirty: Boolean = true

    @Volatile
    private var lastResizeSignalNanos: Long = Long.MIN_VALUE

    @Volatile
    private var pendingSignalCount: Int = 0

    private var waitingRecorded: Boolean = false
    private var pendingResizeReset: Boolean = false
    private var resizeHandlerRegistered: Boolean = false

    fun markInitialized(
        width: Int,
        height: Int,
    ) {
        this.width = width
        this.height = height
        sizeDirty = false
    }

    fun registerSignalHandler() {
        if (resizeHandlerRegistered) return
        try {
            Signal.handle(Signal("WINCH")) {
                sizeDirty = true
                lastResizeSignalNanos = nowNanos()
                pendingSignalCount += 1
                requestRecomposition()
            }
            resizeHandlerRegistered = true
        } catch (_: Throwable) {
            // No-op when signals aren't supported.
        }
    }

    fun updateIfNeeded(terminal: Terminal?) {
        if (terminal == null || !sizeDirty) return
        if (!resizeHasSettled(lastResizeSignalNanos, nowNanos(), settleNanos)) {
            if (!waitingRecorded) {
                waitingRecorded = true
                RenderDiagnostics.record("resize_waiting")
            }
            requestRecomposition()
            return
        }

        val previousWidth = width
        val previousHeight = height
        val size = terminal.updateSize()
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = sizeDirty,
                previousWidth = width,
                previousHeight = height,
                currentWidth = size.width,
                currentHeight = size.height,
            )
        width = update.width
        height = update.height
        if (update.reset) {
            pendingResizeReset = true
            RenderDiagnostics.record(
                event = "resize_applied",
                fields =
                    mapOf(
                        "previousWidth" to previousWidth,
                        "previousHeight" to previousHeight,
                        "width" to update.width,
                        "height" to update.height,
                        "coalescedSignals" to pendingSignalCount,
                    ),
            )
        }
        pendingSignalCount = 0
        waitingRecorded = false
        sizeDirty = update.dirty
    }

    fun isSettling(): Boolean = sizeDirty && !resizeHasSettled(lastResizeSignalNanos, nowNanos(), settleNanos)

    fun consumePendingReset(): Boolean {
        val pending = pendingResizeReset
        pendingResizeReset = false
        return pending
    }

    companion object {
        internal const val DEFAULT_RESIZE_SETTLE_NANOS: Long = 150_000_000L
    }
}

internal fun resizeHasSettled(
    lastSignalNanos: Long,
    nowNanos: Long,
    settleNanos: Long,
): Boolean = lastSignalNanos == Long.MIN_VALUE || nowNanos - lastSignalNanos >= settleNanos
