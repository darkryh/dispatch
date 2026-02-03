package com.ead.dispatch.sample.presentation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.KoinViewModelFactory
import com.ead.dispatch.koin.inject
import com.ead.dispatch.navigation.NavDisplay
import com.ead.dispatch.navigation.entryProvider
import com.ead.dispatch.navigation.rememberNavBackStack
import com.ead.dispatch.runtime.LaunchedEffect
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.domain.embedding.EmbeddingReindexer
import com.ead.dispatch.sample.navigation.*
import com.ead.dispatch.sample.presentation.characters.CharacterScreen
import com.ead.dispatch.sample.presentation.chat.ChatScreen
import com.ead.dispatch.sample.presentation.option.screen.EntityOptionScreen
import com.ead.dispatch.sample.presentation.session.SessionScreen
import com.ead.dispatch.sample.presentation.story_chat.StoryChatScreen

@Dispatchable
fun DispatchSampleApp() {
    val embeddingReindexer by inject<EmbeddingReindexer>()

    val scope = dispatchScope()
    val startArg = scope.getArgument("start")?.lowercase()

    LaunchedEffect(Unit) {
        embeddingReindexer.startPeriodic()
    }


    val startDestination = when {
        scope.hasFlag("resume") || startArg == "resume" || startArg == "session" -> {
            SessionRoute()
        }
        else -> {
            ChatRoute()
        }
    }

    val backStack = rememberNavBackStack(startDestination)

    NavDisplay(
        backStack = backStack,
        viewModelFactory = KoinViewModelFactory(),
        entryProvider = entryProvider {
            entry<SessionRoute> {
                SessionScreen(backStack)
            }
            entry<ChatRoute> {
                ChatScreen(backStack)
            }
            entry<CharacterRoute> { route ->
                CharacterScreen(backStack, route)
            }
            entry<EntityOptionRoute> { route ->
                EntityOptionScreen(backStack, route)
            }
            entry<StoryChatRoute> { route ->
                StoryChatScreen(backStack, route)
            }
        }
    )
}
