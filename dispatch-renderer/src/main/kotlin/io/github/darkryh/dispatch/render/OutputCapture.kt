package io.github.darkryh.dispatch.render

/**
 * Tracks when renderer output is in progress so system output capture can avoid recursion.
 */
object OutputCapture {
    private val suppressed = ThreadLocal.withInitial { false }

    fun isSuppressed(): Boolean = suppressed.get()

    fun <T> suppress(block: () -> T): T {
        val previous = suppressed.get()
        suppressed.set(true)
        return try {
            block()
        } finally {
            suppressed.set(previous)
        }
    }
}
