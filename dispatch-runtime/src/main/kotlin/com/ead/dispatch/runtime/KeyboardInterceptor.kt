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
class KeyboardInterceptor {
    private val interceptors = CopyOnWriteArrayList<(KeyboardEvent) -> Boolean>()

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
        interceptors.add(handler)
        return { interceptors.remove(handler) }
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
        // Check handlers in reverse order (last registered = highest priority)
        for (index in interceptors.size - 1 downTo 0) {
            if (interceptors[index](event)) return true
        }
        return false
    }

    /**
     * Check if there are any registered interceptors.
     */
    fun hasInterceptors(): Boolean = interceptors.isNotEmpty()
}
