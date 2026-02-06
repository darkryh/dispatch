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
import com.ead.dispatch.sample.presentation.chat_mode.chat.ChatScreen
import com.ead.dispatch.sample.presentation.library.arcs.ArcListScreen
import com.ead.dispatch.sample.presentation.library.arcs.ArcEditorScreen
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactListScreen
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactEditorScreen
import com.ead.dispatch.sample.presentation.library.characters.CharacterListScreen
import com.ead.dispatch.sample.presentation.library.cultures.CultureListScreen
import com.ead.dispatch.sample.presentation.library.cultures.CultureEditorScreen
import com.ead.dispatch.sample.presentation.library.events.EventListScreen
import com.ead.dispatch.sample.presentation.library.events.EventEditorScreen
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureListScreen
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureEditorScreen
import com.ead.dispatch.sample.presentation.library.locations.LocationListScreen
import com.ead.dispatch.sample.presentation.library.locations.LocationEditorScreen
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationListScreen
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationEditorScreen
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipListScreen
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipEditorScreen
import com.ead.dispatch.sample.presentation.library.timeline.TimelineListScreen
import com.ead.dispatch.sample.presentation.library.timeline.TimelineEditorScreen
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleListScreen
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleEditorScreen
import com.ead.dispatch.sample.presentation.session.SessionScreen
import com.ead.dispatch.sample.presentation.chat_mode.story.StoryChatScreen

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
                SessionScreen()
            }
            entry<ChatRoute> {
                ChatScreen()
            }
            entry<CharacterRoute> { route ->
                CharacterScreen(route)
            }
            entry<CharacterListRoute> { route ->
                CharacterListScreen(route)
            }
            entry<LocationListRoute> { route ->
                LocationListScreen(route)
            }
            entry<LocationEditorRoute> { route ->
                LocationEditorScreen(route)
            }
            entry<ArcListRoute> { route ->
                ArcListScreen(route)
            }
            entry<ArcEditorRoute> { route ->
                ArcEditorScreen(route)
            }
            entry<WorldRuleListRoute> { route ->
                WorldRuleListScreen(route)
            }
            entry<WorldRuleEditorRoute> { route ->
                WorldRuleEditorScreen(route)
            }
            entry<CultureListRoute> { route ->
                CultureListScreen(route)
            }
            entry<CultureEditorRoute> { route ->
                CultureEditorScreen(route)
            }
            entry<EventListRoute> { route ->
                EventListScreen(route)
            }
            entry<EventEditorRoute> { route ->
                EventEditorScreen(route)
            }
            entry<OrganizationListRoute> { route ->
                OrganizationListScreen(route)
            }
            entry<OrganizationEditorRoute> { route ->
                OrganizationEditorScreen(route)
            }
            entry<RelationshipListRoute> { route ->
                RelationshipListScreen(route)
            }
            entry<RelationshipEditorRoute> { route ->
                RelationshipEditorScreen(route)
            }
            entry<LocationFeatureListRoute> { route ->
                LocationFeatureListScreen(route)
            }
            entry<LocationFeatureEditorRoute> { route ->
                LocationFeatureEditorScreen(route)
            }
            entry<ArtifactListRoute> { route ->
                ArtifactListScreen(route)
            }
            entry<ArtifactEditorRoute> { route ->
                ArtifactEditorScreen(route)
            }
            entry<TimelineListRoute> { route ->
                TimelineListScreen(route)
            }
            entry<TimelineEditorRoute> { route ->
                TimelineEditorScreen(route)
            }
            entry<StoryChatRoute> { route ->
                StoryChatScreen(route)
            }
        }
    )
}
