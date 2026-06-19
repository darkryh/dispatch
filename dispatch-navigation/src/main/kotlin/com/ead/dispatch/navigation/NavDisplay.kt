package com.ead.dispatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import com.ead.dispatch.layout.Box
import com.ead.dispatch.lifecycle.LifecycleState
import com.ead.dispatch.modifier.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.LocalSavedStateHandle
import androidx.compose.runtime.remember
import com.ead.dispatch.viewmodel.DefaultViewModelFactory
import com.ead.dispatch.viewmodel.LocalViewModelProvider
import com.ead.dispatch.viewmodel.ViewModelFactory
import kotlinx.serialization.json.Json

/**
 * A nav display that renders the current back stack entry.
 */
@Composable
fun <T : NavKey> NavDisplay(
    backStack: NavBackStack<T>,
    entryProvider: (key: T) -> NavEntry<T>,
    modifier: Modifier = Modifier,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
    viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
    json: Json = DefaultRouteJson,
) {
    require(backStack.isNotEmpty()) { "NavDisplay backstack cannot be empty" }

    val localDecorator = rememberNavEntryLocalsDecorator<T>()
    val decorators =
        remember(localDecorator, entryDecorators) { listOf(localDecorator) + entryDecorators }
    val store = rememberNavEntryStateStore<T>()

    // Install a deterministic disposal hook so popped entries clear their
    // ViewModel scopes immediately, not only when the diff next recomposes.
    DisposableEffect(backStack, store) {
        backStack.onEntriesRemoved = { remaining -> store.disposeRemoved(remaining) }
        onDispose { backStack.onEntriesRemoved = null }
    }

    val decoratedEntries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = decorators,
            entryProvider = entryProvider,
            viewModelFactory = viewModelFactory,
            json = json,
            store = store,
        )

    val currentEntry = decoratedEntries.lastOrNull() ?: return
    val contentKey = currentEntry.contentKey

    val navigator =
        remember(backStack) {
            object : Navigator {
                @Suppress("UNCHECKED_CAST")
                override fun <K : NavKey> navigate(key: K) {
                    backStack.navigate(key as T)
                }

                override fun popBackStack(): Boolean = backStack.popBackStack()
            }
        }

    // Update lifecycle states: current is STARTED, others STOPPED. Run as a
    // SideEffect so transitions fire once after a successful (re)composition
    // rather than as a raw side effect in the composable body every frame.
    SideEffect {
        decoratedEntries.forEach { entry ->
            val desired =
                if (entry === currentEntry) {
                    LifecycleState.STARTED
                } else {
                    LifecycleState.STOPPED
                }
            if (entry.lifecycleRegistry.currentState != desired &&
                entry.lifecycleRegistry.currentState != LifecycleState.DESTROYED
            ) {
                entry.lifecycleRegistry.moveTo(desired)
            }
        }
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
    ) {
        key(contentKey) {
            Box(modifier = modifier) {
                currentEntry.Content()
            }
        }
    }
}

@Composable
private fun <T : NavKey> rememberNavEntryLocalsDecorator(): NavEntryDecorator<T> =
    remember {
        NavEntryDecorator { entry ->
            CompositionLocalProvider(
                LocalNavBackStackEntry provides entry,
                LocalLifecycleOwner provides entry,
                LocalViewModelProvider provides entry.viewModelProvider,
                LocalSavedStateHandle provides entry.savedStateHandle,
            ) {
                entry.Content()
            }
        }
    }
