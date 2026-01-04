package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.sample.data.db.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.db.StructuredIndexRepository
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.presentation.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.session.SessionViewModel
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.domain.agents.ChatAgent

val module = dispatchModule {
    val deepseekApiKey = System.getenv("DEEPSEEK_API_KEY")

    single { DispatchDatabaseFactory().create() }
    single { StructuredIndexRepository(database = get()) }
    single { CommandManager() }
    single { SessionManager(repository = get()) }

    single {
        ChatAgent(apiKey = deepseekApiKey)
    }

    viewModel { (savedStateHandle: SavedStateHandle) ->
        ChatViewModel(
            commandManager = get(),
            sessionManager = get(),
            chatAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { SessionViewModel(sessionManager = get()) }
}
