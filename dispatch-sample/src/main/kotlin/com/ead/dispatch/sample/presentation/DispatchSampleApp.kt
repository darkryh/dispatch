@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation

import androidx.compose.runtime.Composable
import com.ead.dispatch.koin.KoinViewModelFactory
import com.ead.dispatch.navigation.NavDisplay
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.entryProvider
import com.ead.dispatch.navigation.rememberNavBackStack
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.ComponentsRoute
import com.ead.dispatch.sample.navigation.HomeRoute
import com.ead.dispatch.sample.presentation.chat.ChatScreen
import com.ead.dispatch.sample.presentation.components.ComponentsScreen

@Composable
fun DispatchSampleApp() {
    val backStack = rememberNavBackStack(HomeRoute())

    NavDisplay(
        backStack = backStack,
        viewModelFactory = KoinViewModelFactory(),
        entryProvider =
            entryProvider<NavKey> {
                entry<HomeRoute> { HomeScreen() }
                entry<ChatRoute> { ChatScreen() }
                entry<ComponentsRoute> { route -> ComponentsScreen(route) }
            },
    )
}
