package io.github.darkryh.dispatch.sample.di

import io.github.darkryh.dispatch.koin.dispatchModule
import io.github.darkryh.dispatch.sample.domain.ChatRepository
import io.github.darkryh.dispatch.sample.domain.ResponseSimulator
import io.github.darkryh.dispatch.sample.domain.responseSimulatorFromEnvironment
import io.github.darkryh.dispatch.sample.presentation.buttons.ButtonsViewModel
import io.github.darkryh.dispatch.sample.presentation.chat.ChatViewModel
import io.github.darkryh.dispatch.sample.presentation.hierarchy.HierarchyViewModel
import io.github.darkryh.dispatch.sample.presentation.home.HomeViewModel
import io.github.darkryh.dispatch.sample.presentation.inputs.InputsViewModel
import io.github.darkryh.dispatch.sample.presentation.layout.LayoutViewModel
import io.github.darkryh.dispatch.sample.presentation.lists.ListsViewModel
import io.github.darkryh.dispatch.sample.presentation.progress.ProgressViewModel
import io.github.darkryh.dispatch.sample.presentation.review.ReviewViewModel
import io.github.darkryh.dispatch.sample.presentation.surfaces.SurfacesViewModel
import io.github.darkryh.dispatch.sample.presentation.tables.TablesViewModel
import io.github.darkryh.dispatch.sample.presentation.tasks.TasksViewModel

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
