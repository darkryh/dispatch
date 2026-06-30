package io.github.darkryh.dispatch.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlin.reflect.KClass

/**
 * Provider for ViewModels that handles lifecycle and caching.
 *
 * ViewModels are scoped to their owner (screen/navigation destination)
 * and survive configuration changes like terminal resize.
 */
class ViewModelProvider(
    private val factory: ViewModelFactory = DefaultViewModelFactory(),
    private val savedStateHandle: SavedStateHandle? = null,
) {
    /**
     * Cache of ViewModels by type.
     */
    private val viewModels = mutableMapOf<String, ViewModel>()

    /**
     * Get or create a ViewModel of the given type.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> get(modelClass: KClass<T>, key: String = modelClass.qualifiedName ?: modelClass.simpleName ?: "ViewModel"): T {
        val existing = viewModels[key]
        if (existing != null && modelClass.isInstance(existing)) {
            return existing as T
        }
        // Type mismatch on an existing key: the old instance is about to be
        // displaced, so clear it first to cancel its scope and release resources.
        if (existing != null) {
            existing.clear()
        }

        val viewModel = if (factory is SavedStateViewModelFactory && savedStateHandle != null) {
            factory.create(modelClass, savedStateHandle)
        } else {
            factory.create(modelClass)
        }
        viewModels[key] = viewModel
        return viewModel
    }

    /**
     * Clear all ViewModels.
     */
    fun clear() {
        viewModels.values.forEach { it.clear() }
        viewModels.clear()
    }

    /**
     * Clear a specific ViewModel.
     */
    fun clear(key: String) {
        viewModels.remove(key)?.clear()
    }
}

/**
 * Factory for creating ViewModels.
 */
interface ViewModelFactory {
    /**
     * Create a ViewModel of the given type.
     */
    fun <T : ViewModel> create(modelClass: KClass<T>): T
}

/**
 * Factory for creating ViewModels with access to [SavedStateHandle].
 */
interface SavedStateViewModelFactory : ViewModelFactory {
    /**
     * Create a ViewModel of the given type with a saved state handle.
     */
    fun <T : ViewModel> create(modelClass: KClass<T>, savedStateHandle: SavedStateHandle): T
}

/**
 * Default factory that uses reflection to create ViewModels.
 */
class DefaultViewModelFactory : ViewModelFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: KClass<T>): T {
        return try {
            modelClass.java.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Cannot create ViewModel ${modelClass.simpleName}. " +
                "Make sure it has a no-arg constructor or provide a custom ViewModelFactory.",
                e
            )
        }
    }
}

/**
 * Factory that uses a creator function.
 */
class LambdaViewModelFactory(
    private val creators: Map<KClass<out ViewModel>, () -> ViewModel>,
) : ViewModelFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: KClass<T>): T {
        val creator = creators[modelClass]
            ?: throw IllegalArgumentException("No creator registered for ${modelClass.simpleName}")
        return creator() as T
    }

    class Builder {
        @PublishedApi
        internal val creators = mutableMapOf<KClass<out ViewModel>, () -> ViewModel>()

        inline fun <reified T : ViewModel> add(noinline creator: () -> T): Builder {
            creators[T::class] = creator
            return this
        }

        fun build(): LambdaViewModelFactory = LambdaViewModelFactory(creators.toMap())
    }
}

/**
 * Build a lambda factory.
 */
fun viewModelFactory(builder: LambdaViewModelFactory.Builder.() -> Unit): LambdaViewModelFactory {
    return LambdaViewModelFactory.Builder().apply(builder).build()
}

/**
 * CompositionLocal for the current ViewModelProvider.
 */
val LocalViewModelProvider = compositionLocalOf<ViewModelProvider?> { null }

/**
 * Scope that provides a ViewModelProvider.
 */
@Composable
fun ViewModelProviderScope(
    factory: ViewModelFactory = DefaultViewModelFactory(),
    savedStateHandle: SavedStateHandle? = null,
    content: @Composable () -> Unit,
) {
    val provider = remember(factory, savedStateHandle) { ViewModelProvider(factory, savedStateHandle) }
    CompositionLocalProvider(LocalViewModelProvider provides provider) {
        content()
    }
}

/**
 * Remember a ViewModel with automatic cleanup.
 */
@Composable
inline fun <reified T : ViewModel> rememberViewModel(
    key: String = T::class.qualifiedName ?: T::class.simpleName ?: "ViewModel",
    noinline factory: () -> T,
): T {
    return remember(key) { factory() }
}
