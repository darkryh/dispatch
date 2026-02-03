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
    var windowTitle: String? = null
    var enforceWindowTitle: Boolean = true
    var mouseTracking: MouseTracking = MouseTracking.Off
    var ctrlCExitRequiresDoublePress: Boolean = true
    var ctrlCExitTimeout: Duration = 1500.milliseconds
    var captureSystemOutput: Boolean = true

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
}

data class FlagDefinition(val name: String, val shortName: Char?, val description: String)
data class ArgumentDefinition(val name: String, val shortName: Char?, val description: String, val default: String?, val required: Boolean)
