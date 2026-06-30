package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.compositionLocalOf

/**
 * Read-only view of the runtime's idle-hibernation state, for diagnostics overlays and apps that
 * want to react to or display it.
 */
interface HibernationHandle {
    /**
     * True while the app is hibernating. Snapshot-backed: reading this in a composable recomposes
     * that composable when the state flips.
     */
    val isHibernating: Boolean

    /** Paint cadence used while hibernating. */
    val idleFps: Int

    /** Paint cadence used while awake (the configured [DispatchConfig.targetFps]). */
    val activeFps: Int

    /**
     * Milliseconds of inactivity remaining before hibernation. Returns 0 while hibernating or when
     * hibernation is disabled. Time-based, so poll it (it does not auto-recompose).
     */
    fun idleCountdownMillis(): Long

    /** Force an immediate wake, as if the user had interacted. */
    fun wakeNow()
}

/** CompositionLocal exposing the active [HibernationHandle], or null when unavailable. */
val LocalHibernation = compositionLocalOf<HibernationHandle?> { null }
