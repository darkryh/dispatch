package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.ChatCrudTools
import com.ead.dispatch.sample.presentation.characters.CharacterViewModel
import com.ead.dispatch.sample.presentation.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.session.SessionViewModel
import com.ead.dispatch.sample.presentation.story_info.StoryInfoViewModel

val module = dispatchModule {

    single { DispatchDatabaseFactory().create() }
    single { StructuredIndexRepository(database = get()) }
    single { CommandManager() }
    single { SessionManager(repository = get()) }
    single {
        val repository : StructuredIndexRepository = get()

        ChatAgent(
            repository = repository,
            chatCrudTools = ChatCrudTools(repository)
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
    viewModel { CharacterViewModel() }
    viewModel { SessionViewModel(sessionManager = get()) }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        StoryInfoViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
}
