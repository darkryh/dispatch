package io.github.darkryh.dispatch.update

/**
 * Minimal debug logging hook. The update modules have no logging dependency, so failures that are
 * intentionally swallowed (e.g. transient network/IO errors while probing for a newer version) are
 * surfaced here instead of being silently dropped. Enable with -Ddispatch.update.debug=true.
 */
object UpdateLog {
    private val enabled: Boolean =
        System.getProperty("dispatch.update.debug")?.toBoolean() == true

    fun debug(message: String, throwable: Throwable? = null) {
        if (!enabled) return
        System.err.println("[dispatch-update] $message")
        throwable?.printStackTrace()
    }
}
