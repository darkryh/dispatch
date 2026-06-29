package com.ead.dispatch.runtime

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Idle hibernation settings.
 *
 * When the application receives no user input for [idleTimeout], it enters a low-resource
 * "hibernate" state: the paint cadence drops to [idleFps] (throttling any ongoing animation) and,
 * when [releaseCaches] is set, rebuildable caches are cleared. The very next user interaction wakes
 * it instantly and everything is reconstructed lazily.
 *
 * Hibernation is enabled by default and is non-destructive in its default (conservative) form:
 * scrollback history and all application state are kept; only caches that can be rebuilt on demand
 * are dropped.
 */
class HibernationConfig {
    /** Whether idle hibernation is active. Enabled by default. */
    var enabled: Boolean = true

    /** Inactivity (no keyboard/mouse input) after which the app hibernates. */
    var idleTimeout: Duration = 5.minutes

    /**
     * Paint cadence while hibernating. Caps the frame rate of anything still requesting frames
     * (spinners, progress bars, streaming responses) so an animated screen left unattended stops
     * burning CPU. For a fully static screen this has no effect — nothing is painting anyway.
     */
    var idleFps: Int = 1

    /** Clear rebuildable caches (markdown render cache, lazy-list heights, render diff) on hibernate. */
    var releaseCaches: Boolean = true

    /** Hint the JVM to collect after caches are released, so freed memory is actually reclaimed. */
    var requestGc: Boolean = true

    /**
     * Also drop the renderer's scrollback shadow on hibernate, forcing a clean full repaint on wake.
     * Off by default. Note this clears the framework's cached view of already-painted lines, not the
     * terminal's own scrollback nor your application's history state (e.g. a message list) — those
     * live in app-owned state the framework will not discard. A no-op for fully static screens.
     */
    var trimScrollback: Boolean = false

    /**
     * How often the idle watcher checks for inactivity. Coarse by design: a 1s cadence keeps the
     * watcher's own cost negligible while still entering hibernation promptly once idle.
     */
    var pollInterval: Duration = 1.seconds
}
