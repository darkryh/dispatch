@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.darkryh.dispatch.koin.KoinViewModelFactory
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxSize
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.navigation.NavDisplay
import io.github.darkryh.dispatch.navigation.NavKey
import io.github.darkryh.dispatch.navigation.entryProvider
import io.github.darkryh.dispatch.navigation.rememberNavBackStack
import io.github.darkryh.dispatch.sample.designsystem.HibernationOverlay
import io.github.darkryh.dispatch.sample.designsystem.isHibernationOverlayEnabled
import io.github.darkryh.dispatch.sample.navigation.ButtonsRoute
import io.github.darkryh.dispatch.sample.navigation.ChatRoute
import io.github.darkryh.dispatch.sample.navigation.HierarchyRoute
import io.github.darkryh.dispatch.sample.navigation.HomeRoute
import io.github.darkryh.dispatch.sample.navigation.InputsRoute
import io.github.darkryh.dispatch.sample.navigation.LayoutRoute
import io.github.darkryh.dispatch.sample.navigation.ListsRoute
import io.github.darkryh.dispatch.sample.navigation.ProgressRoute
import io.github.darkryh.dispatch.sample.navigation.ReviewRoute
import io.github.darkryh.dispatch.sample.navigation.SurfacesRoute
import io.github.darkryh.dispatch.sample.navigation.TablesRoute
import io.github.darkryh.dispatch.sample.navigation.TasksRoute
import io.github.darkryh.dispatch.sample.presentation.buttons.ButtonsScreen
import io.github.darkryh.dispatch.sample.presentation.chat.ChatScreen
import io.github.darkryh.dispatch.sample.presentation.hierarchy.HierarchyScreen
import io.github.darkryh.dispatch.sample.presentation.home.HomeScreen
import io.github.darkryh.dispatch.sample.presentation.inputs.InputsScreen
import io.github.darkryh.dispatch.sample.presentation.layout.LayoutScreen
import io.github.darkryh.dispatch.sample.presentation.lists.ListsScreen
import io.github.darkryh.dispatch.sample.presentation.progress.ProgressScreen
import io.github.darkryh.dispatch.sample.presentation.review.ReviewScreen
import io.github.darkryh.dispatch.sample.presentation.surfaces.SurfacesScreen
import io.github.darkryh.dispatch.sample.presentation.tables.TablesScreen
import io.github.darkryh.dispatch.sample.presentation.tasks.TasksScreen

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
        // Default: reserve the bottom for the hibernation banner, which only renders while the
        // runtime is actually hibernating (hide entirely via DISPATCH_SAMPLE_DEBUG_OVERLAY=0).
        Column(modifier = Modifier.fillMaxSize()) {
            navDisplay(Modifier.weight(1f))
            HibernationOverlay()
        }
    } else {
        navDisplay(Modifier)
    }
}
