package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.sample.domain.ChatRepository
import com.ead.dispatch.sample.domain.ResponseSimulator
import com.ead.dispatch.sample.domain.responseSimulatorFromEnvironment
import com.ead.dispatch.sample.presentation.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.components.ComponentsViewModel

val sampleModule =
    dispatchModule {
        single { ChatRepository() }
        single<ResponseSimulator> { responseSimulatorFromEnvironment() }
        viewModel { ChatViewModel(repository = get(), simulator = get()) }
        viewModel { ComponentsViewModel() }
    }
