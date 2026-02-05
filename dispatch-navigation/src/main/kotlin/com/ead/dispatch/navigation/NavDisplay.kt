package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Box
import com.ead.dispatch.lifecycle.LifecycleState
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.LocalSavedStateHandle
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.viewmodel.DefaultViewModelFactory
import com.ead.dispatch.viewmodel.LocalViewModelProvider
import com.ead.dispatch.viewmodel.ViewModelFactory
import kotlinx.serialization.json.Json

/**
 * A nav display that renders the current back stack entry.
 */
@Dispatchable
fun <T : NavKey> NavDisplay(
    backStack: NavBackStack<T>,
    entryProvider: (key: T) -> NavEntry<T>,
    modifier: Modifier = Modifier,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
    viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
    json: Json = DefaultRouteJson,
) {
    require(backStack.isNotEmpty()) { "NavDisplay backstack cannot be empty" }

    val composer = Composer.current
    val lastContentKeyState = remember { mutableStateOf<Any?>(null) }
    val screenSlotRange = remember { mutableStateOf<IntRange?>(null) }

    val localDecorator = rememberNavEntryLocalsDecorator<T>()
    val decoratedEntries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(localDecorator) + entryDecorators,
            entryProvider = entryProvider,
            viewModelFactory = viewModelFactory,
            json = json,
        )

    val currentEntry = decoratedEntries.lastOrNull() ?: return
    val contentKey = currentEntry.contentKey

    val navigator = remember(backStack) {
        object : Navigator {
            @Suppress("UNCHECKED_CAST")
            override fun <K : NavKey> navigate(key: K) {
                backStack.navigate(key as T)
            }

            override fun popBackStack(): Boolean = backStack.popBackStack()
        }
    }

    if (lastContentKeyState.value != contentKey) {
        screenSlotRange.value?.let { range ->
            composer.clearSlotsInRange(range.first, range.last + 1)
        }
        lastContentKeyState.value = contentKey
    }

    // Update lifecycle states: current is STARTED, others STOPPED.
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

    CompositionLocalProvider(
        LocalNavigator provides navigator,
    ) {
        val screenSlotStart = composer.currentPositionKey()
        Box(modifier = modifier) {
            currentEntry.Content()
        }
        val screenSlotEnd = composer.currentPositionKey()
        screenSlotRange.value = screenSlotStart until screenSlotEnd
    }
}

@Dispatchable
private fun <T : NavKey> rememberNavEntryLocalsDecorator(): NavEntryDecorator<T> {
    return remember {
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
}
