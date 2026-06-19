package com.ead.dispatch.navigation

import androidx.compose.runtime.Composable
import com.ead.dispatch.lifecycle.LifecycleOwner
import com.ead.dispatch.lifecycle.LifecycleRegistry
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.viewmodel.ViewModelProvider

/**
 * Static definition of a navigation entry.
 */
data class NavEntry<T : NavKey>(
    val key: T,
    val contentKey: Any = stableContentKey(key),
    val metadata: Map<String, Any> = emptyMap(),
    val content: @Composable (T) -> Unit,
)

/**
 * Cache of computed stable content keys. encodeNavKeyForSave runs full Json
 * encoding, which is expensive to repeat for the same key value while NavEntries
 * are (re)constructed across frames. Data-class / data-object keys with stable
 * equals/hashCode hit this cache instead of re-encoding every time.
 */
private val stableContentKeyCache = java.util.concurrent.ConcurrentHashMap<NavKey, String>()

@PublishedApi
internal fun stableContentKey(key: NavKey): Any =
    stableContentKeyCache.getOrPut(key) { encodeNavKeyForSave(key, DefaultRouteJson) }

/**
 * A stateful back stack entry used during rendering.
 */
class NavBackStackEntry<T : NavKey>(
    val key: T,
    val contentKey: Any,
    val metadata: Map<String, Any>,
    private val content: @Composable () -> Unit,
    val savedStateHandle: SavedStateHandle,
    val viewModelProvider: ViewModelProvider,
    val lifecycleRegistry: LifecycleRegistry,
) : LifecycleOwner {
    override val lifecycle: LifecycleRegistry
        get() = lifecycleRegistry

    @Composable
    fun Content() {
        content()
    }
}

@PublishedApi
internal fun <T : NavKey> NavBackStackEntry<T>.wrap(content: @Composable () -> Unit): NavBackStackEntry<T> =
    NavBackStackEntry(
        key = key,
        contentKey = contentKey,
        metadata = metadata,
        content = content,
        savedStateHandle = savedStateHandle,
        viewModelProvider = viewModelProvider,
        lifecycleRegistry = lifecycleRegistry,
    )
