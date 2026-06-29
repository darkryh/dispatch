package com.ead.dispatch.runtime

import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Application configuration.
 */
class DispatchConfig : DispatchLifecycleHooks {
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

    /** Idle hibernation settings. Enabled by default; see [HibernationConfig]. */
    val hibernation = HibernationConfig()

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

    /** Configure idle hibernation, e.g. `hibernation { idleTimeout = 2.minutes; idleFps = 1 }`. */
    fun hibernation(block: HibernationConfig.() -> Unit) = hibernation.block()

    override fun onExit(action: () -> Unit) {
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

