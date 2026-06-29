@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ead.dispatch.koin.KoinViewModelFactory
import com.ead.dispatch.layout.Column
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxSize
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.navigation.NavDisplay
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.entryProvider
import com.ead.dispatch.navigation.rememberNavBackStack
import com.ead.dispatch.sample.designsystem.HibernationOverlay
import com.ead.dispatch.sample.designsystem.isHibernationOverlayEnabled
import com.ead.dispatch.sample.navigation.ButtonsRoute
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.HierarchyRoute
import com.ead.dispatch.sample.navigation.HomeRoute
import com.ead.dispatch.sample.navigation.InputsRoute
import com.ead.dispatch.sample.navigation.LayoutRoute
import com.ead.dispatch.sample.navigation.ListsRoute
import com.ead.dispatch.sample.navigation.ProgressRoute
import com.ead.dispatch.sample.navigation.ReviewRoute
import com.ead.dispatch.sample.navigation.SurfacesRoute
import com.ead.dispatch.sample.navigation.TablesRoute
import com.ead.dispatch.sample.navigation.TasksRoute
import com.ead.dispatch.sample.presentation.buttons.ButtonsScreen
import com.ead.dispatch.sample.presentation.chat.ChatScreen
import com.ead.dispatch.sample.presentation.hierarchy.HierarchyScreen
import com.ead.dispatch.sample.presentation.home.HomeScreen
import com.ead.dispatch.sample.presentation.inputs.InputsScreen
import com.ead.dispatch.sample.presentation.layout.LayoutScreen
import com.ead.dispatch.sample.presentation.lists.ListsScreen
import com.ead.dispatch.sample.presentation.progress.ProgressScreen
import com.ead.dispatch.sample.presentation.review.ReviewScreen
import com.ead.dispatch.sample.presentation.surfaces.SurfacesScreen
import com.ead.dispatch.sample.presentation.tables.TablesScreen
import com.ead.dispatch.sample.presentation.tasks.TasksScreen

/**
 * Root of the sample. Sets up the navigation back stack (starting at Home) and maps every
 * [NavKey] route to its screen. View-models are resolved from Koin via [KoinViewModelFactory].
 */
@Composable
fun DispatchSampleApp() {
    val backStack = rememberNavBackStack(HomeRoute())
    val showOverlay = remember { isHibernationOverlayEnabled() }

    val navDisplay: @Composable (Modifier) -> Unit = { modifier ->
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            viewModelFactory = KoinViewModelFactory(),
            entryProvider =
                entryProvider<NavKey> {
                    entry<HomeRoute> { HomeScreen() }
                    entry<InputsRoute> { InputsScreen() }
                    entry<ButtonsRoute> { ButtonsScreen() }
                    entry<ListsRoute> { ListsScreen() }
                    entry<TablesRoute> { TablesScreen() }
                    entry<HierarchyRoute> { HierarchyScreen() }
                    entry<TasksRoute> { TasksScreen() }
                    entry<ProgressRoute> { ProgressScreen() }
                    entry<SurfacesRoute> { SurfacesScreen() }
                    entry<LayoutRoute> { LayoutScreen() }
                    entry<ReviewRoute> { ReviewScreen() }
                    entry<ChatRoute> { ChatScreen() }
                },
        )
    }

    if (showOverlay) {
        // Opt-in debug build: pin the screen above a one-line hibernation status footer.
        Column(modifier = Modifier.fillMaxSize()) {
            navDisplay(Modifier.weight(1f))
            HibernationOverlay()
        }
    } else {
        navDisplay(Modifier)
    }
}
