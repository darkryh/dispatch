package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.sample.domain.ChatRepository
import com.ead.dispatch.sample.domain.ResponseSimulator
import com.ead.dispatch.sample.domain.responseSimulatorFromEnvironment
import com.ead.dispatch.sample.presentation.buttons.ButtonsViewModel
import com.ead.dispatch.sample.presentation.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.hierarchy.HierarchyViewModel
import com.ead.dispatch.sample.presentation.home.HomeViewModel
import com.ead.dispatch.sample.presentation.inputs.InputsViewModel
import com.ead.dispatch.sample.presentation.layout.LayoutViewModel
import com.ead.dispatch.sample.presentation.lists.ListsViewModel
import com.ead.dispatch.sample.presentation.progress.ProgressViewModel
import com.ead.dispatch.sample.presentation.review.ReviewViewModel
import com.ead.dispatch.sample.presentation.surfaces.SurfacesViewModel
import com.ead.dispatch.sample.presentation.tables.TablesViewModel
import com.ead.dispatch.sample.presentation.tasks.TasksViewModel

/**
 * Koin wiring for the sample. Domain singletons live for the whole process; each screen's
 * view-model is registered with `viewModel { }` so the navigation layer can create and dispose one
 * per back-stack entry.
 */
val sampleModule =
    dispatchModule {
        single { ChatRepository() }
        single<ResponseSimulator> { responseSimulatorFromEnvironment() }

        viewModel { HomeViewModel() }
        viewModel { ChatViewModel(repository = get(), simulator = get()) }
        viewModel { InputsViewModel() }
        viewModel { ButtonsViewModel() }
        viewModel { ListsViewModel() }
        viewModel { TablesViewModel() }
        viewModel { HierarchyViewModel() }
        viewModel { TasksViewModel() }
        viewModel { ProgressViewModel() }
        viewModel { SurfacesViewModel() }
        viewModel { LayoutViewModel() }
        viewModel { ReviewViewModel() }
    }
