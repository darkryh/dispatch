@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.sample.designsystem.AppKeyPriority
import com.ead.dispatch.sample.designsystem.AppScaffold
import com.ead.dispatch.sample.designsystem.LauncherGrid
import com.ead.dispatch.sample.navigation.CatalogDestination
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.KeyHint

/**
 * The Home screen: a centered launcher grid of every category, navigated in two dimensions with the
 * arrow keys and opened with Enter.
 *
 * Demonstrates: [LauncherGrid], an [com.ead.dispatch.viewmodel.MviViewModel] driving pure 2-D
 * navigation, and a keyboard interceptor registered just below the global palette so `Ctrl+P` still
 * wins. Esc does nothing here ([AppScaffold] `escGoesBack = false`) because Home is the root.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val navigator = LocalNavigator.current
    val interceptor = LocalKeyboardInterceptor.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(HomeIntent.Init(CatalogDestination.COLUMNS))
    }

    DisposableEffect(interceptor) {
        val dispose =
            interceptor.register(priority = AppKeyPriority.HOME_GRID) { rawEvent ->
                when (rawEvent.asKeyEvent().key) {
                    Key.ArrowUp -> consumeMove(viewModel, MoveDirection.UP)
                    Key.ArrowDown -> consumeMove(viewModel, MoveDirection.DOWN)
                    Key.ArrowLeft -> consumeMove(viewModel, MoveDirection.LEFT)
                    Key.ArrowRight -> consumeMove(viewModel, MoveDirection.RIGHT)
                    Key.Enter -> {
                        val destination = CatalogDestination.entries[viewModel.currentState.cursor]
                        navigator.navigate(destination.route)
                        true
                    }
                    else -> false
                }
            }
        onDispose { dispose() }
    }

    AppScaffold(
        title = "Dispatch — Terminal UI Showcase",
        subtitle = "Pick a category. Each screen is an interactive playground for a family of widgets.",
        escGoesBack = false,
        hints =
            listOf(
                KeyHint("↑↓←→", "move"),
                KeyHint("Enter", "open"),
            ),
    ) {
        LauncherGrid(selectedIndex = state.cursor)
    }
}

private fun consumeMove(
    viewModel: HomeViewModel,
    direction: MoveDirection,
): Boolean {
    viewModel.sendIntent(HomeIntent.Move(direction))
    return true
}
