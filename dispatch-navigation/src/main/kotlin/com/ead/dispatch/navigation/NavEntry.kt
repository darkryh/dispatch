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
 * Maximum number of distinct route keys whose encoded form is retained. Caps memory for long
 * sessions that visit many parameterized routes (e.g. DetailRoute(id) per item); the cached value
 * is a pure function of the key, so an evicted key simply re-encodes to an equal String on its next
 * use and every downstream value-equality lookup still matches.
 */
private const val STABLE_CONTENT_KEY_CACHE_MAX = 256

/**
 * Bounded LRU cache of computed stable content keys. encodeNavKeyForSave runs full Json
 * encoding, which is expensive to repeat for the same key value while NavEntries are
 * (re)constructed across frames. Access-ordered + size-capped so it cannot grow without bound the
 * way the previous unbounded ConcurrentHashMap did. Wrapped in a synchronized map to keep the
 * thread-safety of the original (stableContentKey runs on the compose/render thread).
 */
private val stableContentKeyCache: MutableMap<NavKey, String> =
    java.util.Collections.synchronizedMap(
        object : LinkedHashMap<NavKey, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<NavKey, String>): Boolean =
                size > STABLE_CONTENT_KEY_CACHE_MAX
        },
    )

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
