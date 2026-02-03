package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import com.ead.dispatch.sample.domain.embedding.EmbeddingReindexer
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.presentation.characters.CharacterViewModel
import com.ead.dispatch.sample.presentation.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.option.viewmodel.EntityOptionViewModel
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
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { SessionViewModel(sessionManager = get()) }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        EntityOptionViewModel(
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
