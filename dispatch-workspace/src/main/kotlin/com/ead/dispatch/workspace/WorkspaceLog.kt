package com.ead.dispatch.workspace

/**
 * Minimal debug logging hook. The framework has no logging dependency, so failures that are
 * intentionally swallowed (e.g. transient IO during hashing) are surfaced here instead of being
 * silently dropped. Enable with -Ddispatch.workspace.debug=true.
 */
internal object WorkspaceLog {
    private val enabled: Boolean =
        System.getProperty("dispatch.workspace.debug")?.toBoolean() == true

    fun debug(message: String, throwable: Throwable? = null) {
        if (!enabled) return
        System.err.println("[dispatch-workspace] $message")
        throwable?.printStackTrace()
    }
}
