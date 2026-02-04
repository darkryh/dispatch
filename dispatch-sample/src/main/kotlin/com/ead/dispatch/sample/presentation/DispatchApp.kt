package com.ead.dispatch.sample.presentation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.KoinViewModelFactory
import com.ead.dispatch.koin.inject
import com.ead.dispatch.navigation.NavDisplay
import com.ead.dispatch.navigation.entryProvider
import com.ead.dispatch.navigation.rememberNavBackStack
import com.ead.dispatch.runtime.LaunchedEffect
import com.ead.dispatch.runtime.LocalDispatchArgs
import com.ead.dispatch.sample.domain.embedding.EmbeddingReindexer
import com.ead.dispatch.sample.navigation.*
import com.ead.dispatch.sample.presentation.characters.CharacterScreen
import com.ead.dispatch.sample.presentation.chat.ChatScreen
import com.ead.dispatch.sample.presentation.entity_editor.EntityEditorScreen
import com.ead.dispatch.sample.presentation.library.arcs.ArcListScreen
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactListScreen
import com.ead.dispatch.sample.presentation.library.characters.CharacterListScreen
import com.ead.dispatch.sample.presentation.library.cultures.CultureListScreen
import com.ead.dispatch.sample.presentation.library.events.EventListScreen
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureListScreen
import com.ead.dispatch.sample.presentation.library.locations.LocationListScreen
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationListScreen
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipListScreen
import com.ead.dispatch.sample.presentation.library.timeline.TimelineListScreen
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleListScreen
import com.ead.dispatch.sample.presentation.session.SessionScreen
import com.ead.dispatch.sample.presentation.story_chat.StoryChatScreen

@Dispatchable
fun DispatchSampleApp() {
    val embeddingReindexer by inject<EmbeddingReindexer>()

    val args = LocalDispatchArgs.current
    val startArg = args.getArgument("start")?.lowercase()

    LaunchedEffect(Unit) { embeddingReindexer.startPeriodic() }

    val startDestination = when {
        args.hasFlag("resume") || startArg == "resume" || startArg == "session" -> {
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
            entry<EntityEditorRoute> { route ->
                EntityEditorScreen(backStack, route)
            }
            entry<CharacterListRoute> { route ->
                CharacterListScreen(backStack, route)
            }
            entry<LocationListRoute> { route ->
                LocationListScreen(backStack, route)
            }
            entry<ArcListRoute> { route ->
                ArcListScreen(backStack, route)
            }
            entry<WorldRuleListRoute> { route ->
                WorldRuleListScreen(backStack, route)
            }
            entry<CultureListRoute> { route ->
                CultureListScreen(backStack, route)
            }
            entry<EventListRoute> { route ->
                EventListScreen(backStack, route)
            }
            entry<OrganizationListRoute> { route ->
                OrganizationListScreen(backStack, route)
            }
            entry<RelationshipListRoute> { route ->
                RelationshipListScreen(backStack, route)
            }
            entry<LocationFeatureListRoute> { route ->
                LocationFeatureListScreen(backStack, route)
            }
            entry<ArtifactListRoute> { route ->
                ArtifactListScreen(backStack, route)
            }
            entry<TimelineListRoute> { route ->
                TimelineListScreen(backStack, route)
            }
            entry<StoryChatRoute> { route ->
                StoryChatScreen(backStack, route)
            }
        }
    )
}
