package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import com.ead.dispatch.sample.domain.embedding.EmbeddingReindexer
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.presentation.characters.CharacterViewModel
import com.ead.dispatch.sample.presentation.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.entity_editor.EntityEditorViewModel
import com.ead.dispatch.sample.presentation.library.arcs.ArcListViewModel
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactListViewModel
import com.ead.dispatch.sample.presentation.library.characters.CharacterListViewModel
import com.ead.dispatch.sample.presentation.library.cultures.CultureListViewModel
import com.ead.dispatch.sample.presentation.library.events.EventListViewModel
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureListViewModel
import com.ead.dispatch.sample.presentation.library.locations.LocationListViewModel
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationListViewModel
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipListViewModel
import com.ead.dispatch.sample.presentation.library.timeline.TimelineListViewModel
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleListViewModel
import com.ead.dispatch.sample.presentation.session.SessionViewModel
import com.ead.dispatch.sample.presentation.story_chat.StoryChatViewModel

val module = dispatchModule {

    single { DispatchDatabaseFactory().create() }
    single { ChatAgentEmbedder() }
    single { EmbeddingIndexService(embedderProvider = get()) }
    single { EmbeddingReindexer(repository = get()) }
    single { StructuredIndexRepository(database = get(), embeddingIndexService = get()) }
    single { RagContextService(repository = get(), embeddingIndexService = get()) }
    single { CommandManager() }
    single { SessionManager(repository = get()) }
    single {
        ChatAgent(
            repository = get(),
            ragContextService = get(),
        )
    }
    single { CharacterAgent() }

    viewModel { (savedStateHandle: SavedStateHandle) ->
        ChatViewModel(
            commandManager = get(),
            sessionManager = get(),
            chatAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CharacterViewModel(
            repository = get(),
            characterAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { SessionViewModel(sessionManager = get()) }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        EntityEditorViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CharacterListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        LocationListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ArcListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        WorldRuleListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CultureListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        EventListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        OrganizationListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        RelationshipListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        LocationFeatureListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ArtifactListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        TimelineListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        StoryChatViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
}
