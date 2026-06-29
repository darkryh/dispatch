package com.ead.dispatch.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * A single declarative keyboard binding.
 *
 * Matches an incoming [KeyboardEvent] when its [key] equals [KeyboardEvent.key] and the
 * required modifier flags ([ctrl], [alt], [shift]) match the event's modifiers exactly.
 *
 * @param key The key name to match (e.g. `"Enter"`, `"ArrowUp"`, `"p"`). Compared case-sensitively
 *   against [KeyboardEvent.key]; use the exact casing Mordant reports.
 * @param description Optional human-readable description (useful for building key-hint bars).
 * @param ctrl Whether the Ctrl modifier must be held.
 * @param alt Whether the Alt modifier must be held.
 * @param shift Whether the Shift modifier must be held.
 * @param action Invoked when this binding matches an incoming event.
 */
data class KeyBinding(
    val key: String,
    val description: String = "",
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val action: () -> Unit,
) {
    /**
     * Returns `true` if [event] matches this binding's key and modifier flags.
     */
    fun matches(event: KeyboardEvent): Boolean =
        event.key == key &&
            event.ctrl == ctrl &&
            event.alt == alt &&
            event.shift == shift
}

/**
 * Builder scope for declaring [KeyBinding]s.
 *
 * Collect bindings via [on], then match an incoming event with [handle]. This is the declarative
 * replacement for the duplicated key-string `when` blocks scattered across widgets.
 */
class KeyBindingsScope {
    private val bindings = mutableListOf<KeyBinding>()

    /**
     * Declare a key binding.
     *
     * @param key The key name to match (see [KeyBinding.key]).
     * @param description Optional human-readable description.
     * @param ctrl Whether the Ctrl modifier must be held.
     * @param alt Whether the Alt modifier must be held.
     * @param shift Whether the Shift modifier must be held.
     * @param action Invoked when this binding matches.
     */
    fun on(
        key: String,
        description: String = "",
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        action: () -> Unit,
    ) {
        bindings.add(
            KeyBinding(
                key = key,
                description = description,
                ctrl = ctrl,
                alt = alt,
                shift = shift,
                action = action,
            ),
        )
    }

    /**
     * Snapshot of the bindings declared so far, in declaration order.
     */
    fun bindings(): List<KeyBinding> = bindings.toList()

    /**
     * Match [event] against the declared bindings (in declaration order), invoking the first
     * matching binding's action.
     *
     * @return `true` if a binding matched and consumed the event, `false` otherwise.
     */
    fun handle(event: KeyboardEvent): Boolean {
        for (binding in bindings) {
            if (binding.matches(event)) {
                binding.action()
                return true
            }
        }
        return false
    }
}

/**
 * Declaratively register keyboard bindings on the current [LocalKeyboardInterceptor].
 *
 * Builds the bindings by running [block] (re-running when [block] changes), then registers a
 * single interceptor handler that matches incoming events against the bindings and invokes the
 * matching action, consuming the event. The handler is unregistered automatically when the
 * composition leaves or when [enabled]/[priority] change.
 *
 * Example:
 * ```kotlin
 * KeyBindings {
 *     on("ArrowUp", description = "move up") { state.moveUp() }
 *     on("ArrowDown", description = "move down") { state.moveDown() }
 *     on("p", ctrl = true, description = "go to…") { openPalette() }
 * }
 * ```
 *
 * @param enabled When `false`, no handler is registered.
 * @param priority Higher-priority handlers run before lower-priority ones (see [KeyboardInterceptor.register]).
 * @param block Declares the bindings via [KeyBindingsScope.on].
 */
@Composable
fun KeyBindings(
    enabled: Boolean = true,
    priority: Int = 0,
    block: KeyBindingsScope.() -> Unit,
) {
    val interceptor = LocalKeyboardInterceptor.current
    val scope = remember(block) { KeyBindingsScope().apply(block) }

    DisposableEffect(interceptor, enabled, priority, scope) {
        if (!enabled) return@DisposableEffect onDispose {}
        val dispose =
            interceptor.register(priority = priority) { event ->
                scope.handle(event)
            }
        onDispose { dispose() }
    }
}
