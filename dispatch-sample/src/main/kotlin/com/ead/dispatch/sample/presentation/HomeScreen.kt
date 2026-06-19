@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.layout.TerminalScreen
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.ComponentSections
import com.ead.dispatch.sample.navigation.ComponentsRoute
import com.ead.dispatch.widget.Button
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.SectionHeader
import com.ead.dispatch.widget.Text

@Composable
fun HomeScreen() {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current

    TerminalScreen(
        header = {
            Text("Dispatch UI Sample", style = theme.primary)
            Text("A deterministic application for exercising terminal UI behavior.", style = theme.muted)
            Spacer(Modifier.height(1))
        },
        footer = {
            KeyHintBar(
                hints =
                    listOf(
                        KeyHint("Tab", "next control"),
                        KeyHint("Shift+Q", "previous control"),
                        KeyHint("Enter", "activate"),
                        KeyHint("Ctrl+C x2", "exit"),
                    ),
            )
        },
    ) {
        SectionHeader("Workflows", subtitle = "Enter opens the focused item")
        Button("Simulated chat", onClick = { navigator.navigate(ChatRoute()) })
        Spacer(Modifier.height(1))
        SectionHeader("Widget catalogue")
        ComponentSections.all.forEach { section ->
            Button(
                text = section.replaceFirstChar(Char::uppercase),
                onClick = { navigator.navigate(ComponentsRoute(section)) },
            )
        }
    }
}
