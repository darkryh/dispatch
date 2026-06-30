package io.github.darkryh.dispatch.runtime

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide registry of "release on hibernate" callbacks.
 *
 * Lets caches in higher modules (e.g. widget render caches) contribute reclaimable memory to the
 * idle-hibernation path without the runtime engine depending on those modules. Releasers run on the
 * UI thread when the app goes idle; everything they drop must be cheap to rebuild lazily on wake.
 *
 * Releasers must be idempotent. Register from a [androidx.compose.runtime.DisposableEffect] and
 * close the returned handle on dispose to avoid retaining per-instance caches past their lifetime.
 */
object HibernationRegistry {
    private val releasers = CopyOnWriteArrayList<() -> Unit>()

    /** Register a cache-clearing callback. Close the returned handle to unregister. */
    fun registerReleaser(release: () -> Unit): AutoCloseable {
        releasers.add(release)
        return AutoCloseable { releasers.remove(release) }
    }

    /** Invoke every registered releaser, isolating individual failures. Returns the count invoked. */
    fun releaseAll(): Int {
        val snapshot = releasers.toList()
        snapshot.forEach { runCatching { it() } }
        return snapshot.size
    }
}
