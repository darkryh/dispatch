package com.ead.dispatch.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.KeyStroke
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.input.stroke
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * A single declarative keyboard binding: a [KeyStroke] plus an [action] to run when it matches.
 *
 * @param stroke The key + required modifiers this binding matches (e.g. `Key.Enter.stroke()`,
 *   `ctrl('p')`, `Key.Tab.shift`).
 * @param description Optional human-readable description (useful for building key-hint bars).
 * @param action Invoked when this binding matches an incoming event.
 */
data class KeyBinding(
    val stroke: KeyStroke,
    val description: String = "",
    val action: () -> Unit,
) {
    /** Returns `true` if [event] matches this binding's [stroke]. */
    fun matches(event: KeyboardEvent): Boolean = stroke.matches(event.asKeyEvent())
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
     * Declare a key binding from a [KeyStroke] (e.g. `on(ctrl('p'))` or `on(Key.Tab.shift)`).
     *
     * @param stroke The key + required modifiers to match.
     * @param description Optional human-readable description.
     * @param action Invoked when this binding matches.
     */
    fun on(
        stroke: KeyStroke,
        description: String = "",
        action: () -> Unit,
    ) {
        bindings.add(KeyBinding(stroke = stroke, description = description, action = action))
    }

    /**
     * Declare a key binding from a [Key] with no required modifiers (e.g. `on(Key.Enter)`).
     *
     * @param key The key to match.
     * @param description Optional human-readable description.
     * @param action Invoked when this binding matches.
     */
    fun on(
        key: Key,
        description: String = "",
        action: () -> Unit,
    ) {
        on(stroke = key.stroke(), description = description, action = action)
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
 *     on(Key.ArrowUp, description = "move up") { state.moveUp() }
 *     on(Key.ArrowDown, description = "move down") { state.moveDown() }
 *     on(ctrl('p'), description = "go to…") { openPalette() }
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
