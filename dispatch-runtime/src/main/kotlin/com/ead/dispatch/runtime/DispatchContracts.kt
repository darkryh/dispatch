package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.DispatchRenderer
import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Scope for configuring the Dispatch application.
 */
@Suppress("ComplexInterface")
interface DispatchScope {
    val terminal: Terminal
    val theme: DispatchTheme
    val args: Array<String>
    val terminalWidth: Int
    val terminalHeight: Int

    fun config(block: DispatchConfig.() -> Unit)
    fun exit(code: Int = 0)
    fun hasFlag(name: String): Boolean
    fun getArgument(name: String): String?
    fun launch(block: suspend CoroutineScope.() -> Unit): Job
    fun clearScreen(clearScrollback: Boolean = false)
    fun onKeyEvent(handler: (KeyboardEvent) -> Unit)
    fun onMouseEvent(handler: (MouseEvent) -> Unit)

    /**
     * Renderer Active UI block - this content updates in-place at the bottom.
     * Only this part recomposes on state changes.
     */
    @DispatchRenderer
    fun renderer(block: @Dispatchable () -> Unit)

}

/**
 * Application configuration.
 */
class DispatchConfig {
    var name: String? = null
    var version: String? = null
    var description: String? = null
    var theme: DispatchTheme = DispatchTheme.Dark
    var activeAreaHeight: Int = 12
    /**
     * Target frame rate for coalescing renders. Set to 0 for immediate rendering.
     */
    var targetFps: Int = 60
    var windowTitle: String? = null
    var enforceWindowTitle: Boolean = true
    var mouseTracking: MouseTracking = MouseTracking.Off
    var requireExitDoublePress: Boolean = true
    var exitTimeoutOnDoublePress: Duration = 1500.milliseconds
    var exitKeyBindings: List<ExitKeyBinding> = listOf(ExitKeyBinding.ctrl("C"))
    var exitKeyPredicate: ((KeyboardEvent) -> Boolean)? = null
    var captureSystemOutput: Boolean = true
    private val exitActions = mutableListOf<() -> Unit>()
    private var exitActionsExecuted: Boolean = false

    val flags = mutableMapOf<String, FlagDefinition>()
    val arguments = mutableMapOf<String, ArgumentDefinition>()

    fun flag(name: String, shortName: Char? = null, description: String = "") {
        flags[name] = FlagDefinition(name, shortName, description)
    }

    fun argument(
        name: String,
        shortName: Char? = null,
        description: String = "",
        default: String? = null,
        required: Boolean = false
    ) {
        arguments[name] = ArgumentDefinition(name, shortName, description, default, required)
    }

    fun exitKeys(vararg bindings: ExitKeyBinding) {
        exitKeyBindings = bindings.toList()
    }

    fun exitKeyPredicate(predicate: (KeyboardEvent) -> Boolean) {
        exitKeyPredicate = predicate
    }

    /**
     * Register a callback to run when Dispatch finishes shutting down.
     *
     * Callbacks execute once in reverse registration order.
     */
    fun onExit(action: () -> Unit) {
        exitActions += action
    }

    /**
     * Executes registered exit callbacks exactly once.
     *
     * Intended for Dispatch runtime internals.
     */
    fun runExitActions() {
        if (exitActionsExecuted) return
        exitActionsExecuted = true
        val snapshot = exitActions.toList().asReversed()
        snapshot.forEach { action ->
            runCatching { action() }
        }
    }
}

data class FlagDefinition(val name: String, val shortName: Char?, val description: String)
data class ArgumentDefinition(
    val name: String,
    val shortName: Char?,
    val description: String,
    val default: String?,
    val required: Boolean,
)
