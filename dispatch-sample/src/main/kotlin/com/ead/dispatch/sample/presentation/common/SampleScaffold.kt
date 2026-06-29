@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.common

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
import com.ead.dispatch.layout.TerminalScreen
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.ComponentSections
import com.ead.dispatch.sample.navigation.ComponentsRoute
import com.ead.dispatch.sample.navigation.HomeRoute
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Consistent screen frame for the sample. Every screen gets:
 * - a header (title + optional subtitle),
 * - the global Ctrl+P navigator overlay (jump anywhere with the arrow keys),
 * - Esc-to-go-back (when [escGoesBack] and nothing else consumed Esc),
 * - a key-hint bar describing the intuitive keys.
 */
@Composable
fun SampleScaffold(
    title: String,
    subtitle: String? = null,
    escGoesBack: Boolean = true,
    hints: List<KeyHint> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val interceptor = LocalKeyboardInterceptor.current

    // Esc returns to the previous screen, unless a widget/overlay already consumed it.
    DisposableEffect(escGoesBack, interceptor) {
        if (!escGoesBack) return@DisposableEffect onDispose {}
        val dispose =
            interceptor.register(priority = 5) { rawEvent ->
                val event = rawEvent.asKeyEvent()
                if (event.key == Key.Escape) {
                    navigator.popBackStack()
                } else {
                    false
                }
            }
        onDispose { dispose() }
    }

    TerminalScreen(
        header = {
            Text(title, style = theme.primary)
            if (subtitle != null) Text(subtitle, style = theme.muted)
            Spacer(Modifier.height(1))
        },
        footer = {
            footer?.invoke()
            CommandNavigator(navigator)
            KeyHintBar(hints = baseHints(escGoesBack) + hints)
        },
    ) {
        content()
    }
}

private fun baseHints(escGoesBack: Boolean): List<KeyHint> =
    buildList {
        add(KeyHint("Ctrl+P", "go to…"))
        add(KeyHint("↑/↓", "move"))
        add(KeyHint("Enter", "select"))
        if (escGoesBack) add(KeyHint("Esc", "back"))
        add(KeyHint("Ctrl+C x2", "exit"))
    }

private class Destination(
    val label: String,
    val hint: String,
    val go: (Navigator) -> Unit,
)

private val destinations: List<Destination> =
    buildList {
        add(Destination("Home", "main menu") { it.navigate(HomeRoute()) })
        add(Destination("Chat", "simulated streaming chat") { it.navigate(ChatRoute()) })
        ComponentSections.all.forEach { section ->
            add(
                Destination(
                    label = section.replaceFirstChar(Char::uppercase),
                    hint = "widget gallery",
                ) { it.navigate(ComponentsRoute(section)) },
            )
        }
    }

/**
 * Global "go to…" overlay. Opens with Ctrl+P from anywhere, navigable purely with the arrow keys
 * (no Tab-hunting). While open it is modal — it swallows keystrokes so they don't leak to the
 * screen behind it.
 */
@Composable
private fun CommandNavigator(navigator: Navigator) {
    val theme = LocalTheme.current
    val interceptor = LocalKeyboardInterceptor.current
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val count = destinations.size

    DisposableEffect(interceptor) {
        val dispose =
            interceptor.register(priority = 1000) { event ->
                handleNavigatorKey(
                    rawEvent = event,
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

private fun handleNavigatorKey(
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
        // Modal while open: swallow everything else so it doesn't reach the screen behind.
        else -> true
    }
}
