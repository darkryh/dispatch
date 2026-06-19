@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation

import androidx.compose.runtime.Composable
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.ComponentSections
import com.ead.dispatch.sample.navigation.ComponentsRoute
import com.ead.dispatch.sample.presentation.common.MenuItem
import com.ead.dispatch.sample.presentation.common.SampleScaffold
import com.ead.dispatch.sample.presentation.common.SelectMenu

@Composable
fun HomeScreen() {
    val navigator = LocalNavigator.current

    val items =
        buildList {
            add(MenuItem("Simulated chat", "streaming chat with a slash-command palette") { navigator.navigate(ChatRoute()) })
            ComponentSections.all.forEach { section ->
                add(
                    MenuItem(
                        label = section.replaceFirstChar(Char::uppercase),
                        hint = "widget gallery",
                    ) { navigator.navigate(ComponentsRoute(section)) },
                )
            }
        }

    SampleScaffold(
        title = "Dispatch UI Sample",
        subtitle = "Move with ↑/↓ and press Enter. Ctrl+P jumps anywhere from any screen.",
        escGoesBack = false,
    ) {
        SelectMenu(items = items)
    }
}
