package com.ead.dispatch.sample.presentation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.KoinViewModelFactory
import com.ead.dispatch.navigation.NavHost
import com.ead.dispatch.navigation.rememberNavController
import com.ead.dispatch.navigation.screen
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.HelpRoute
import com.ead.dispatch.sample.navigation.SessionRoute
import com.ead.dispatch.sample.presentation.chat.ChatScreen
import com.ead.dispatch.sample.presentation.help.HelpScreen
import com.ead.dispatch.sample.presentation.session.SessionScreen

@Dispatchable
fun DispatchSampleApp() {
    val navController = rememberNavController(viewModelFactory = KoinViewModelFactory())

    val scope = dispatchScope()
    val startArg = scope.getArgument("start")?.lowercase()

    val startDestination: Any = when {
        scope.hasFlag("resume") || startArg == "resume" || startArg == "session" -> {
            SessionRoute()
        }
        scope.hasFlag("help") || startArg == "help" -> {
            HelpRoute(from = "cli")
        }
        else -> {
            ChatRoute()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        screen<SessionRoute> { _ ->
            SessionScreen(navController)
        }

        screen<ChatRoute> {
            ChatScreen(navController)
        }

        screen<HelpRoute> { route ->
            HelpScreen(navController, route)
        }
    }
}
