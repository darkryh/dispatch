package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.DispatchRenderer
import androidx.compose.runtime.Composable
import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Scope for configuring and driving a Dispatch application runtime.
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
     * Root dispatch content rendered by the runtime.
     */
    fun content(block: @DispatchRenderer @Composable () -> Unit)
}
