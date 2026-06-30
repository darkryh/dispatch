package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import io.github.darkryh.dispatch.lifecycle.LifecycleState
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import io.github.darkryh.dispatch.runtime.SavedStateRegistry
import io.github.darkryh.dispatch.viewmodel.DefaultViewModelFactory
import io.github.darkryh.dispatch.viewmodel.ViewModelFactory
import io.github.darkryh.dispatch.viewmodel.ViewModelProvider
import kotlinx.serialization.json.Json

/**
 * Decorate NavBackStackEntries with additional behavior or providers.
 */
open class NavEntryDecorator<T : NavKey>(
    internal val onPop: (key: Any) -> Unit = {},
    internal val decorate: @Composable (entry: NavBackStackEntry<T>) -> Unit,
)

@Composable
fun <T : NavKey> rememberDecoratedNavEntries(
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
    entryProvider: (key: T) -> NavEntry<T>,
    viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
    json: Json = DefaultRouteJson,
): List<NavBackStackEntry<T>> =
    rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
        viewModelFactory = viewModelFactory,
        json = json,
        store = rememberNavEntryStateStore(),
    )

@Composable
internal fun <T : NavKey> rememberDecoratedNavEntries(
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>>,
    entryProvider: (key: T) -> NavEntry<T>,
    viewModelFactory: ViewModelFactory,
    json: Json,
    store: NavEntryStateStore<T>,
): List<NavBackStackEntry<T>> {
    val entries =
        rememberNavEntries(
            backStack = backStack,
            entryProvider = entryProvider,
            viewModelFactory = viewModelFactory,
            json = json,
            store = store,
        )
    return rememberDecoratedNavEntries(entries, entryDecorators)
}

@Composable
fun <T : NavKey> rememberDecoratedNavEntries(
    entries: List<NavBackStackEntry<T>>,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
): List<NavBackStackEntry<T>> {
    val activeContentKeys = remember { mutableSetOf<Any>() }
    val currentKeys = LinkedHashSet<Any>(entries.size)
    entries.forEach { currentKeys.add(it.contentKey) }

    // When the back stack is unchanged (the common frame), skip the set-subtraction and the
    // clear/addAll entirely. If equal, there are no removed keys, so onPop never fires anyway.
    if (activeContentKeys != currentKeys) {
        val removedKeys = activeContentKeys - currentKeys
        if (removedKeys.isNotEmpty()) {
            removedKeys.forEach { key ->
                entryDecorators.forEach { decorator -> decorator.onPop(key) }
            }
        }
        activeContentKeys.clear()
        activeContentKeys.addAll(currentKeys)
    }

    // Memoize decorated entries keyed by (contentKey, source entry, decorators).
    // Decorating wraps the entry once; recompositions that leave the back stack
    // unchanged reuse the same NavBackStackEntry identities instead of rebuilding
    // a fresh fold chain every frame.
    val cache = remember { mutableMapOf<Any, DecoratedEntryCacheValue<T>>() }
    val decorated =
        entries.map { entry ->
            val cached = cache[entry.contentKey]
            if (cached != null && cached.source === entry && cached.decorators === entryDecorators) {
                cached.decorated
            } else {
                val value = decorateEntry(entry, entryDecorators)
                cache[entry.contentKey] = DecoratedEntryCacheValue(entry, entryDecorators, value)
                value
            }
        }
    // Drop cache entries for content keys no longer present.
    if (cache.keys.retainAll(currentKeys)) Unit
    return decorated
}

private class DecoratedEntryCacheValue<T : NavKey>(
    val source: NavBackStackEntry<T>,
    val decorators: List<NavEntryDecorator<T>>,
    val decorated: NavBackStackEntry<T>,
)

private fun <T : NavKey> decorateEntry(
    entry: NavBackStackEntry<T>,
    decorators: List<NavEntryDecorator<T>>,
): NavBackStackEntry<T> =
    decorators.foldRight(entry) { decorator, wrapped ->
        wrapped.wrap { decorator.decorate(wrapped) }
    }

internal class NavEntryState<T : NavKey>(
    val savedStateRegistry: SavedStateRegistry,
    val savedStateHandle: SavedStateHandle,
    val viewModelProvider: ViewModelProvider,
    val lifecycleRegistry: io.github.darkryh.dispatch.lifecycle.LifecycleRegistry,
) {
    // Cached rendered entry; invalidated when the source NavEntry changes.
    var entry: NavBackStackEntry<T>? = null
    var sourceEntry: NavEntry<T>? = null

    // The encoded route payload is a pure function of this entry's key (its contentKey embeds the
    // class + payload), so it never changes for the life of the state. Encode it once instead of
    // re-running the reflective serializer lookup every recomposition.
    var encodedRoutePayload: String? = null

    fun dispose() {
        if (lifecycleRegistry.currentState != LifecycleState.DESTROYED) {
            lifecycleRegistry.moveTo(LifecycleState.DESTROYED)
        }
        viewModelProvider.clear()
        savedStateRegistry.clear()
        entry = null
        sourceEntry = null
        encodedRoutePayload = null
    }
}

/**
 * Owns the per-entry state store and anchors its cleanup to composition lifetime.
 *
 * As a [RememberObserver], [onForgotten]/[onAbandoned] fire when the host
 * composable (NavDisplay) leaves composition, disposing every retained entry so
 * the whole back stack's ViewModel scopes are cancelled on screen teardown
 * (root leak fix). Cleanup of individually removed entries (pops) still happens
 * eagerly during recomposition as a backstop.
 */
internal class NavEntryStateStore<T : NavKey> : RememberObserver {
    val states = mutableMapOf<Any, NavEntryState<T>>()
    val activeContentKeys = mutableSetOf<Any>()
    val lastStableBackStack = mutableListOf<T>()

    private fun disposeAll() {
        states.values.forEach { it.dispose() }
        states.clear()
        activeContentKeys.clear()
    }

    /**
     * Deterministically dispose any retained entry whose content key is no longer
     * present in [remainingContentKeys] (used by NavDisplay on pop).
     */
    fun disposeRemoved(remainingContentKeys: Set<Any>) {
        val removed = states.keys - remainingContentKeys
        if (removed.isEmpty()) return
        removed.forEach { key ->
            states.remove(key)?.dispose()
            activeContentKeys.remove(key)
        }
    }

    override fun onRemembered() = Unit

    override fun onForgotten() = disposeAll()

    override fun onAbandoned() = disposeAll()
}

@Composable
internal fun <T : NavKey> rememberNavEntryStateStore(): NavEntryStateStore<T> = remember { NavEntryStateStore() }

@Composable
private fun <T : NavKey> rememberNavEntries(
    backStack: List<T>,
    entryProvider: (key: T) -> NavEntry<T>,
    viewModelFactory: ViewModelFactory,
    json: Json,
    store: NavEntryStateStore<T> = rememberNavEntryStateStore(),
): List<NavBackStackEntry<T>> {
    val stateStore = store.states
    val activeContentKeys = store.activeContentKeys
    val lastStableBackStack = store.lastStableBackStack

    val stableBackStack = snapshotBackStack(backStack, fallback = lastStableBackStack)
    lastStableBackStack.clear()
    lastStableBackStack.addAll(stableBackStack)

    val entries =
        stableBackStack.map { key ->
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
                    NavEntryState(
                        registry,
                        handle,
                        provider,
                        io.github.darkryh.dispatch.lifecycle
                            .LifecycleRegistry(),
                    )
                }

            val encodedPayload =
                state.encodedRoutePayload
                    ?: encodeNavKeyPayload(key, json).also { state.encodedRoutePayload = it }
            state.savedStateHandle[ROUTE_PAYLOAD_KEY] = encodedPayload

            // Reuse the cached NavBackStackEntry when the source NavEntry is
            // unchanged so entries are not reconstructed on every frame.
            val cached = state.entry
            if (cached != null && state.sourceEntry === entry) {
                cached
            } else {
                NavBackStackEntry(
                    key = entry.key,
                    contentKey = entry.contentKey,
                    metadata = entry.metadata,
                    content = { entry.content(entry.key) },
                    savedStateHandle = state.savedStateHandle,
                    viewModelProvider = state.viewModelProvider,
                    lifecycleRegistry = state.lifecycleRegistry,
                ).also {
                    state.entry = it
                    state.sourceEntry = entry
                }
            }
        }

    val currentKeys = LinkedHashSet<Any>(entries.size)
    entries.forEach { currentKeys.add(it.contentKey) }
    // Unchanged back stack: nothing removed, nothing to re-store. Skip the subtraction + copy.
    if (activeContentKeys != currentKeys) {
        val removed = activeContentKeys - currentKeys
        if (removed.isNotEmpty()) {
            removed.forEach { key ->
                val state = stateStore.remove(key) ?: return@forEach
                state.dispose()
            }
        }
        activeContentKeys.clear()
        activeContentKeys.addAll(currentKeys)
    }

    return entries
}

internal fun <T> snapshotBackStack(
    source: List<T>,
    fallback: List<T> = emptyList(),
    maxRetries: Int = 8,
): List<T> {
    repeat(maxRetries.coerceAtLeast(1)) {
        val snapshot = trySnapshot(source)
        if (snapshot != null) return snapshot
    }
    return fallback.toList()
}

private fun <T> trySnapshot(source: List<T>): List<T>? =
    try {
        val size = source.size
        val snapshot = ArrayList<T>(size)
        for (index in 0 until size) {
            snapshot.add(source[index])
        }
        snapshot
    } catch (_: ConcurrentModificationException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }
