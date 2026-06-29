package com.ead.dispatch.runtime

import androidx.compose.runtime.mutableStateOf
import com.ead.dispatch.render.RenderDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Drives idle hibernation: watches for user inactivity and, once [HibernationConfig.idleTimeout] is
 * crossed, throttles the paint cadence to [HibernationConfig.idleFps] and releases rebuildable
 * caches. The next user interaction wakes it instantly and restores the active FPS.
 *
 * Threading: the watcher runs on [watcherScope] (background) but every state mutation — entering and
 * waking — runs on [uiScope] so cache release happens on the same thread that measures/renders,
 * avoiding races with composition-owned caches. Wake is invoked from the input handler, which is
 * already on the UI dispatcher.
 */
internal class HibernationController(
    private val config: HibernationConfig,
    private val watcherScope: CoroutineScope,
    private val uiScope: CoroutineScope,
    private val frameScheduler: FrameScheduler,
    private val awakeFps: Int,
    private val onReleaseResources: () -> Unit,
    private val nanoTime: () -> Long = { System.nanoTime() },
) : HibernationHandle {
    private val lastActivityNanos = AtomicLong(nanoTime())
    private val hibernating = AtomicBoolean(false)
    private val hibernatingState = mutableStateOf(false)

    @Volatile
    private var hibernateEnteredAtNanos = 0L

    private var watcherJob: Job? = null

    override val isHibernating: Boolean get() = hibernatingState.value
    override val idleFps: Int get() = config.idleFps
    override val activeFps: Int get() = awakeFps

    override fun idleCountdownMillis(): Long {
        if (!config.enabled || hibernating.get()) return 0L
        val idleNanos = nanoTime() - lastActivityNanos.get()
        val remainingNanos = config.idleTimeout.inWholeNanoseconds - idleNanos
        return (remainingNanos / 1_000_000L).coerceAtLeast(0L)
    }

    override fun wakeNow() {
        if (hibernating.get()) uiScope.launch { wake(nanoTime()) }
    }

    /** Begin watching for inactivity. No-op when hibernation is disabled. */
    fun start() {
        if (!config.enabled || watcherJob != null) return
        lastActivityNanos.set(nanoTime())
        watcherJob =
            watcherScope.launch {
                while (isActive) {
                    delay(config.pollInterval)
                    if (hibernating.get()) continue
                    val idleNanos = nanoTime() - lastActivityNanos.get()
                    if (idleNanos >= config.idleTimeout.inWholeNanoseconds) {
                        uiScope.launch { enterHibernate() }
                    }
                }
            }
    }

    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
    }

    /**
     * Record a user interaction. Resets the idle timer and, if hibernating, wakes immediately.
     * Must be called on the UI dispatcher (it is invoked from the input handler).
     *
     * @param activityNanos the [System.nanoTime] at which the input event was read, used to measure
     *   wake latency.
     */
    fun onUserActivity(activityNanos: Long = nanoTime()) {
        if (!config.enabled) return
        lastActivityNanos.set(activityNanos)
        if (hibernating.get()) wake(activityNanos)
    }

    private fun enterHibernate() {
        if (!hibernating.compareAndSet(false, true)) return
        hibernateEnteredAtNanos = nanoTime()

        val heapBefore = usedHeapBytes()
        frameScheduler.setTargetFps(config.idleFps)
        var releasedHooks = 0
        if (config.releaseCaches) {
            releasedHooks = runCatching { onReleaseResources(); 1 }.getOrDefault(0)
        }
        if (config.requestGc) System.gc()
        hibernatingState.value = true
        val heapAfter = usedHeapBytes()

        RenderDiagnostics.record(
            event = "hibernate_enter",
            fields =
                mapOf(
                    "idleTimeoutMs" to config.idleTimeout.inWholeMilliseconds,
                    "idleFps" to config.idleFps,
                    "releasedCaches" to (config.releaseCaches && releasedHooks > 0),
                    "requestedGc" to config.requestGc,
                    "heapBeforeBytes" to heapBefore,
                    "heapAfterBytes" to heapAfter,
                    "heapFreedBytes" to (heapBefore - heapAfter),
                ),
        )
    }

    private fun wake(activityNanos: Long) {
        if (!hibernating.compareAndSet(true, false)) return
        frameScheduler.setTargetFps(awakeFps)
        hibernatingState.value = false
        frameScheduler.requestImmediateFrame()

        val now = nanoTime()
        RenderDiagnostics.record(
            event = "hibernate_exit",
            fields =
                mapOf(
                    "hibernatedDurationMs" to ((now - hibernateEnteredAtNanos) / 1_000_000L),
                    "wakeLatencyNanos" to (now - activityNanos).coerceAtLeast(0L),
                    "activeFps" to awakeFps,
                ),
        )
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
