package com.ead.dispatch.sample.presentation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.KoinViewModelFactory
import com.ead.dispatch.navigation.NavHost
import com.ead.dispatch.navigation.rememberNavController
import com.ead.dispatch.navigation.screen
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.CharacterRoute
import com.ead.dispatch.sample.navigation.EntityListRoute
import com.ead.dispatch.sample.navigation.HelpRoute
import com.ead.dispatch.sample.navigation.SessionRoute
import com.ead.dispatch.sample.navigation.StoryInfoRoute
import com.ead.dispatch.sample.navigation.StorySummaryRoute
import com.ead.dispatch.sample.presentation.chat.ChatScreen
import com.ead.dispatch.sample.presentation.characters.CharacterScreen
import com.ead.dispatch.sample.presentation.entity_list.EntityListScreen
import com.ead.dispatch.sample.presentation.help.HelpScreen
import com.ead.dispatch.sample.presentation.session.SessionScreen
import com.ead.dispatch.sample.presentation.story_info.StoryInfoScreen
import com.ead.dispatch.sample.presentation.story_summary.StorySummaryScreen

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

        screen<CharacterRoute> { route ->
            CharacterScreen(navController, route)
        }

        screen<StoryInfoRoute> {
            StoryInfoScreen(navController)
        }

        screen<StorySummaryRoute> { route ->
            StorySummaryScreen(navController, route)
        }

        screen<EntityListRoute> { route ->
            EntityListScreen(navController, route)
        }

        screen<HelpRoute> { route ->
            HelpScreen(navController, route)
        }
    }
}
