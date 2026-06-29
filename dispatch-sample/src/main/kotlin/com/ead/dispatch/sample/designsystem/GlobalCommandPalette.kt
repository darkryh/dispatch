@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.input.ctrl
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.CatalogDestination
import com.ead.dispatch.sample.navigation.HomeRoute
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Global "go to…" overlay. Opens with `Ctrl+P` from any screen and is navigable purely with the
 * arrow keys — no Tab-hunting. While open it is *modal*: it swallows every keystroke so nothing
 * leaks to the screen behind it.
 *
 * Destinations are generated from [CatalogDestination] (plus Home), so this list never drifts from
 * the launcher grid. Demonstrates: the keyboard-interceptor pattern at the highest priority
 * ([AppKeyPriority.GLOBAL_PALETTE]) and a modal overlay.
 */
@Composable
fun GlobalCommandPalette() {
    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val interceptor = LocalKeyboardInterceptor.current

    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val count = destinations.size

    DisposableEffect(interceptor) {
        val dispose =
            interceptor.register(priority = AppKeyPriority.GLOBAL_PALETTE) { rawEvent ->
                handlePaletteKey(
                    rawEvent = rawEvent,
                    isOpen = open,
                    count = count,
                    setOpen = { open = it },
                    moveSelection = { delta -> selected = (selected + delta + count) % count },
                    confirm = {
                        destinations[selected].go(navigator)
                        open = false
                    },
                    resetSelection = { selected = 0 },
                )
            }
        onDispose { dispose() }
    }

    if (!open) return

    Spacer(Modifier.height(1))
    Panel(modifier = Modifier.fillMaxWidth(), title = "Go to  ·  ↑↓ move · Enter open · Esc close") {
        Column {
            destinations.forEachIndexed { index, destination ->
                val isSelected = index == selected
                Row {
                    Text(if (isSelected) "❯ " else "  ", style = if (isSelected) theme.accent else theme.muted)
                    Text(destination.label, style = if (isSelected) theme.accent else theme.primary)
                    Text("  —  ${destination.hint}", style = theme.muted)
                }
            }
        }
    }
}

private class Destination(
    val label: String,
    val hint: String,
    val go: (Navigator) -> Unit,
)

private val destinations: List<Destination> =
    buildList {
        add(Destination("Home", "main menu") { it.navigate(HomeRoute()) })
        CatalogDestination.entries.forEach { destination ->
            add(Destination(destination.title, destination.blurb) { it.navigate(destination.route) })
        }
    }

private fun handlePaletteKey(
    rawEvent: KeyboardEvent,
    isOpen: Boolean,
    count: Int,
    setOpen: (Boolean) -> Unit,
    moveSelection: (Int) -> Unit,
    confirm: () -> Unit,
    resetSelection: () -> Unit,
): Boolean {
    val event = rawEvent.asKeyEvent()
    if (event.matches(ctrl('p'))) {
        val next = !isOpen
        setOpen(next)
        if (next) resetSelection()
        return true
    }
    if (!isOpen || count == 0) return false
    return when (event.key) {
        Key.ArrowUp -> {
            moveSelection(-1)
            true
        }
        Key.ArrowDown -> {
            moveSelection(1)
            true
        }
        Key.Enter -> {
            confirm()
            true
        }
        Key.Escape -> {
            setOpen(false)
            true
        }
        // Modal while open: swallow everything else so it never reaches the screen behind.
        else -> true
    }
}
