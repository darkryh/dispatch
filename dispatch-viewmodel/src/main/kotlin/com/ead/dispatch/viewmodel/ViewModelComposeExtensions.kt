package com.ead.dispatch.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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
@Composable
inline fun <reified T : ViewModel> viewModel(
    key: String = T::class.java.name,
    noinline factory: () -> T
): T {
    val provider = LocalViewModelProvider.current
    return provider?.get(T::class, key) ?: remember(key) {
        ViewModelStore.getOrCreate(key, factory)
    }
}

/**
 * Returns an existing [ViewModel] or creates a new one using the default constructor.
 */
@Composable
inline fun <reified T : ViewModel> viewModel(): T {
    val key = T::class.java.name
    val provider = LocalViewModelProvider.current
    return provider?.get(T::class, key)
        ?: remember(key) {
            ViewModelStore.getOrCreate(key) {
                T::class.java.getDeclaredConstructor().newInstance()
            }
        }
}
