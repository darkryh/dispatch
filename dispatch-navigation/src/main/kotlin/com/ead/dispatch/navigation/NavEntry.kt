package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.lifecycle.LifecycleOwner
import com.ead.dispatch.lifecycle.LifecycleRegistry
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.viewmodel.ViewModelProvider

/**
 * Static definition of a navigation entry.
 */
data class NavEntry<T : NavKey>(
    val key: T,
    val contentKey: Any = defaultContentKey(key),
    val metadata: Map<String, Any> = emptyMap(),
    val content: @Dispatchable (T) -> Unit,
)

@PublishedApi
internal fun defaultContentKey(key: Any): Any = key.toString()

/**
 * A stateful back stack entry used during rendering.
 */
class NavBackStackEntry<T : NavKey>(
    val key: T,
    val contentKey: Any,
    val metadata: Map<String, Any>,
    private val content: @Dispatchable () -> Unit,
    val savedStateHandle: SavedStateHandle,
    val viewModelProvider: ViewModelProvider,
    val lifecycleRegistry: LifecycleRegistry,
) : LifecycleOwner {
    @Dispatchable
    fun Content() {
        content()
    }

    override val lifecycle: LifecycleRegistry
        get() = lifecycleRegistry
}

@PublishedApi
internal fun <T : NavKey> NavBackStackEntry<T>.wrap(
    content: @Dispatchable () -> Unit,
): NavBackStackEntry<T> {
    return NavBackStackEntry(
        key = key,
        contentKey = contentKey,
        metadata = metadata,
        content = content,
        savedStateHandle = savedStateHandle,
        viewModelProvider = viewModelProvider,
        lifecycleRegistry = lifecycleRegistry,
    )
}
