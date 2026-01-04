package com.ead.dispatch.sample.presentation.help

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.inject
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.NavController
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.navigation.HelpRoute
import com.ead.dispatch.state.remember
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun HelpScreen(
    navController: NavController,
    route: HelpRoute
) {
    val theme = LocalTheme.current
    val scope = dispatchScope()
    val commandsManager by inject<CommandManager>()
    val commands = commandsManager.data

    val prefix = "/"
    val labelWidth = remember(commands) {
        commands.maxOfOrNull { (prefix + it.label).length } ?: 0
    }

    val source = route.from?.trim()?.takeIf { it.isNotEmpty() }

    scope.onKeyEvent { event ->
        if (event.key == "Escape") {
            navController.popBackStack()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        if (source != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(2))
                    Text(
                        text = "Opened from: $source",
                        style = theme.muted,
                    )
                }
            }
            item { Spacer(Modifier.height(1)) }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "Commands",
                    style = theme.primary + TextStyle(bold = true),
                )
            }
        }
        item { Spacer(Modifier.height(1)) }
        items(commands) { command ->
            val labelText = (prefix + command.label).padEnd(labelWidth + 2)
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = labelText,
                    style = theme.accent,
                )
                Text(
                    text = command.description,
                    style = theme.muted,
                )
            }
        }
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "Press Esc to go back",
                    style = theme.muted,
                )
            }
        }
    }
}
