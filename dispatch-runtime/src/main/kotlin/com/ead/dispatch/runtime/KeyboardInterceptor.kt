package com.ead.dispatch.runtime

import com.github.ajalt.mordant.input.KeyboardEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for keyboard event interceptors.
 *
 * Allows multiple widgets to register handlers that are checked
 * BEFORE the default InputTextField handling. This enables widgets
 * like CommandPalette to intercept specific keys (Arrow, Enter, Escape)
 * when they are active, while letting other keys pass through.
 *
 * Example:
 * ```kotlin
 * val interceptor = LocalKeyboardInterceptor.current
 *
 * DisposableEffect(isActive) {
 *     if (!isActive) return@DisposableEffect onDispose {}
 *
 *     val dispose = interceptor.register { event ->
 *         when (event.key) {
 *             "ArrowUp" -> { handleUp(); true }
 *             "ArrowDown" -> { handleDown(); true }
 *             else -> false  // Let other handlers process
 *         }
 *     }
 *
 *     onDispose { dispose() }
 * }
 * ```
 */
class KeyboardInterceptor(
    private val parent: KeyboardInterceptor? = null,
) {
    private data class InterceptorEntry(
        val priority: Int,
        val order: Long,
        val handler: (KeyboardEvent) -> Boolean,
    )

    private val interceptors = CopyOnWriteArrayList<InterceptorEntry>()
    @Volatile
    private var orderedInterceptors: List<InterceptorEntry> = emptyList()
    @Volatile
    private var nextOrder = 0L
    private var lastEvent: KeyboardEvent? = null
    private var lastEventConsumed: Boolean = false

    /**
     * Register an interceptor.
     *
     * Interceptors are called in reverse order (last registered = highest priority).
     * An interceptor should return `true` if it consumed the event, `false` otherwise.
     *
     * @param handler The handler function that receives keyboard events.
     * @return A dispose function to unregister the handler.
     */
    fun register(handler: (KeyboardEvent) -> Boolean): () -> Unit {
        return register(priority = 0, handler = handler)
    }

    /**
     * Register an interceptor with a priority.
     *
     * Higher priority handlers run first. For equal priority, the most recently
     * registered handler runs first.
     */
    fun register(priority: Int, handler: (KeyboardEvent) -> Boolean): () -> Unit {
        val entry = InterceptorEntry(priority = priority, order = nextOrder, handler = handler)
        nextOrder += 1
        interceptors.add(entry)
        rebuildOrder()
        return {
            interceptors.remove(entry)
            rebuildOrder()
        }
    }

    /**
     * Try to intercept the event.
     *
     * Calls all registered interceptors in reverse order until one returns `true`.
     *
     * @param event The keyboard event to process.
     * @return `true` if any interceptor consumed the event, `false` otherwise.
     */
    fun tryIntercept(event: KeyboardEvent): Boolean {
        // Avoid re-processing the same event when multiple handlers call tryIntercept.
        if (event === lastEvent) return lastEventConsumed
        lastEvent = event

        // Check handlers by priority, then registration order (last = highest).
        for (entry in orderedInterceptors) {
            if (entry.handler(event)) {
                lastEventConsumed = true
                return true
            }
        }

        val handledByParent = parent?.tryIntercept(event) == true
        lastEventConsumed = handledByParent
        return handledByParent
    }

    /**
     * Check if there are any registered interceptors.
     */
    fun hasInterceptors(): Boolean = interceptors.isNotEmpty() || (parent?.hasInterceptors() == true)

    private fun rebuildOrder() {
        orderedInterceptors = interceptors.sortedWith(
            compareByDescending<InterceptorEntry> { it.priority }
                .thenByDescending { it.order }
        )
    }
}
