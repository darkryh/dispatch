package com.ead.dispatch.viewmodel

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.state.remember

/**
 * Storage for ViewModels scoped to the application.
 *
 * Exposed so hosting layers (e.g., DispatchApplication) can clear scoped ViewModels
 * when shutting down.
 */
object ViewModelStore {
    private val viewModels = mutableMapOf<String, ViewModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> getOrCreate(key: String, factory: () -> T): T {
        return viewModels.getOrPut(key) { factory() } as T
    }

    fun clear() {
        viewModels.values.forEach { it.clear() }
        viewModels.clear()
    }
}

/**
 * Returns an existing [ViewModel] or creates a new one.
 *
 * The ViewModel is scoped to the application and survives recomposition.
 */
@Dispatchable
inline fun <reified T : ViewModel> viewModel(
    key: String = T::class.java.name,
    noinline factory: () -> T
): T {
    val provider = runCatching { LocalViewModelProvider.current }.getOrNull()
    return provider?.get(T::class, key) ?: remember(key) {
        ViewModelStore.getOrCreate(key, factory)
    }
}

/**
 * Returns an existing [ViewModel] or creates a new one using the default constructor.
 */
@Dispatchable
inline fun <reified T : ViewModel> viewModel(): T {
    val key = T::class.java.name
    val provider = runCatching { LocalViewModelProvider.current }.getOrNull()
    return provider?.get(T::class, key)
        ?: remember(key) {
            ViewModelStore.getOrCreate(key) {
                T::class.java.getDeclaredConstructor().newInstance()
            }
        }
}
