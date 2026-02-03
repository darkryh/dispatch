package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.lifecycle.LifecycleState
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.runtime.SavedStateRegistry
import com.ead.dispatch.state.remember
import com.ead.dispatch.viewmodel.DefaultViewModelFactory
import com.ead.dispatch.viewmodel.ViewModelFactory
import com.ead.dispatch.viewmodel.ViewModelProvider
import kotlinx.serialization.json.Json

/**
 * Decorate NavBackStackEntries with additional behavior or providers.
 */
open class NavEntryDecorator<T : NavKey>(
    internal val onPop: (key: Any) -> Unit = {},
    internal val decorate: @Dispatchable (entry: NavBackStackEntry<T>) -> Unit,
)

@Dispatchable
fun <T : NavKey> rememberDecoratedNavEntries(
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
    entryProvider: (key: T) -> NavEntry<T>,
    viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
    json: Json = DefaultRouteJson,
): List<NavBackStackEntry<T>> {
    val entries =
        rememberNavEntries(
            backStack = backStack,
            entryProvider = entryProvider,
            viewModelFactory = viewModelFactory,
            json = json,
        )
    return rememberDecoratedNavEntries(entries, entryDecorators)
}

@Dispatchable
fun <T : NavKey> rememberDecoratedNavEntries(
    entries: List<NavBackStackEntry<T>>,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
): List<NavBackStackEntry<T>> {
    val activeContentKeys = remember { mutableSetOf<Any>() }
    val currentKeys = entries.map { it.contentKey }.toSet()
    val removedKeys = activeContentKeys - currentKeys
    if (removedKeys.isNotEmpty()) {
        removedKeys.forEach { key ->
            entryDecorators.forEach { decorator -> decorator.onPop(key) }
        }
    }
    activeContentKeys.clear()
    activeContentKeys.addAll(currentKeys)

    return entries.map { entry -> decorateEntry(entry, entryDecorators) }
}

private fun <T : NavKey> decorateEntry(
    entry: NavBackStackEntry<T>,
    decorators: List<NavEntryDecorator<T>>,
): NavBackStackEntry<T> {
    return decorators.foldRight(entry) { decorator, wrapped ->
        wrapped.wrap { decorator.decorate(wrapped) }
    }
}

private class NavEntryState(
    val savedStateRegistry: SavedStateRegistry,
    val savedStateHandle: SavedStateHandle,
    val viewModelProvider: ViewModelProvider,
    val lifecycleRegistry: com.ead.dispatch.lifecycle.LifecycleRegistry,
)

private fun <T : NavKey> rememberNavEntries(
    backStack: List<T>,
    entryProvider: (key: T) -> NavEntry<T>,
    viewModelFactory: ViewModelFactory,
    json: Json,
): List<NavBackStackEntry<T>> {
    val stateStore = remember { mutableMapOf<Any, NavEntryState>() }
    val activeContentKeys = remember { mutableSetOf<Any>() }

    val entries =
        backStack.map { key ->
            val entry = entryProvider(key)
            require(entry.key == key) {
                "EntryProvider must return a NavEntry for the provided key. " +
                    "Expected $key, got ${entry.key}."
            }
            val state =
                stateStore.getOrPut(entry.contentKey) {
                    val registry = SavedStateRegistry()
                    val handle = SavedStateHandle(registry)
                    val provider = ViewModelProvider(viewModelFactory, handle)
                    NavEntryState(registry, handle, provider, com.ead.dispatch.lifecycle.LifecycleRegistry())
                }

            state.savedStateHandle[ROUTE_PAYLOAD_KEY] = encodeNavKeyPayload(key, json)

            NavBackStackEntry(
                key = entry.key,
                contentKey = entry.contentKey,
                metadata = entry.metadata,
                content = { entry.content(entry.key) },
                savedStateHandle = state.savedStateHandle,
                viewModelProvider = state.viewModelProvider,
                lifecycleRegistry = state.lifecycleRegistry,
            )
        }

    val currentKeys = entries.map { it.contentKey }.toSet()
    val removed = activeContentKeys - currentKeys
    if (removed.isNotEmpty()) {
        removed.forEach { key ->
            val state = stateStore.remove(key) ?: return@forEach
            if (state.lifecycleRegistry.currentState != LifecycleState.DESTROYED) {
                state.lifecycleRegistry.moveTo(LifecycleState.DESTROYED)
            }
            state.viewModelProvider.clear()
            state.savedStateRegistry.clear()
        }
    }
    activeContentKeys.clear()
    activeContentKeys.addAll(currentKeys)

    return entries
}
