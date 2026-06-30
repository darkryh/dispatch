package io.github.darkryh.dispatch.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember

/**
 * Storage for ViewModels scoped to the application.
 *
 * Exposed so hosting layers (e.g., DispatchApplication) can clear scoped ViewModels
 * when shutting down.
 *
 * IMPORTANT: This is the fallback store used by [viewModel] when no
 * [LocalViewModelProvider] is present. ViewModels stored here live for the entire
 * application lifetime (until [clear] is invoked, e.g. by DispatchApplication on
 * shutdown). For screen-scoped lifecycles, provide a [LocalViewModelProvider]
 * (NavDisplay does this automatically) so ViewModels are cleared with their screen.
 */
object ViewModelStore {
    private val viewModels = mutableMapOf<String, ViewModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> getOrCreate(
        key: String,
        factory: () -> T,
    ): T = viewModels.getOrPut(key) { factory() } as T

    fun clear() {
        viewModels.values.forEach { it.clear() }
        viewModels.clear()
    }
}

/**
 * Returns an existing [ViewModel] or creates a new one.
 *
 * Prefer providing a [LocalViewModelProvider] (NavDisplay does this) so the
 * ViewModel is scoped to its screen. When no provider is present this falls back
 * to the app-scoped [ViewModelStore]: the key is derived from the call-site
 * composite key hash to avoid cross-screen collisions on a bare class name, but
 * the instance still lives for the application lifetime.
 */
@Composable
inline fun <reified T : ViewModel> viewModel(
    key: String = "${T::class.java.name}#$currentCompositeKeyHashCode",
    noinline factory: () -> T,
): T {
    val provider = LocalViewModelProvider.current
    return provider?.get(T::class, key) ?: remember(key) {
        ViewModelStore.getOrCreate(key, factory)
    }
}

/**
 * Returns an existing [ViewModel] or creates a new one using the default constructor.
 *
 * See [viewModel] with a factory for scoping caveats; the provider path is preferred.
 */
@Composable
inline fun <reified T : ViewModel> viewModel(): T {
    val key = "${T::class.java.name}#$currentCompositeKeyHashCode"
    val provider = LocalViewModelProvider.current
    return provider?.get(T::class, key)
        ?: remember(key) {
            ViewModelStore.getOrCreate(key) {
                T::class.java.getDeclaredConstructor().newInstance()
            }
        }
}
