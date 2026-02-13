package com.ead.dispatch.koin

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.viewmodel.ViewModel
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.dsl.onClose
import org.koin.dsl.module
import kotlin.reflect.KClass

@DslMarker
annotation class DispatchKoinDsl

internal data class RegisteredViewModel(
    val modelClass: KClass<out ViewModel>,
    val closeAfterValidation: Boolean,
    val savedStateHandleProvider: (() -> SavedStateHandle)?,
)

@PublishedApi
internal object DispatchKoinRegistry {
    private val viewModels = LinkedHashMap<KClass<out ViewModel>, RegisteredViewModel>()

    @PublishedApi
    internal fun registerViewModel(
        modelClass: KClass<out ViewModel>,
        closeAfterValidation: Boolean = true,
        savedStateHandleProvider: (() -> SavedStateHandle)? = null,
    ) {
        viewModels[modelClass] =
            RegisteredViewModel(
                modelClass = modelClass,
                closeAfterValidation = closeAfterValidation,
                savedStateHandleProvider = savedStateHandleProvider,
            )
    }

    internal fun registeredViewModels(): List<RegisteredViewModel> = viewModels.values.toList()

    internal fun clear() {
        viewModels.clear()
    }
}

@DispatchKoinDsl
class DispatchKoinModuleScope internal constructor(
    @PublishedApi internal val module: Module,
) {
    inline fun <reified T : ViewModel> viewModel(
        closeAfterValidation: Boolean = true,
        noinline savedStateHandleProvider: (() -> SavedStateHandle)? = null,
        noinline definition: Definition<T>,
    ) {
        module.factory(definition = definition)
        DispatchKoinRegistry.registerViewModel(
            modelClass = T::class,
            closeAfterValidation = closeAfterValidation,
            savedStateHandleProvider = savedStateHandleProvider,
        )
    }

    inline fun <reified T> factory(noinline definition: Definition<T>) {
        module.factory(definition = definition)
    }

    inline fun <reified T> single(noinline definition: Definition<T>) {
        val koinDefinition = module.single(definition = definition)
        if (AutoCloseable::class.java.isAssignableFrom(T::class.java)) {
            koinDefinition.onClose { instance ->
                (instance as? AutoCloseable)?.close()
            }
        }
    }

    fun includes(vararg modules: Module) {
        module.includes(*modules)
    }
}

fun dispatchModule(builder: DispatchKoinModuleScope.() -> Unit): Module =
    module {
        DispatchKoinModuleScope(this).builder()
    }
