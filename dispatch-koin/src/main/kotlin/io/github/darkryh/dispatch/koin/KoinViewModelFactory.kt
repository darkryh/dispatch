package io.github.darkryh.dispatch.koin

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import io.github.darkryh.dispatch.viewmodel.SavedStateViewModelFactory
import io.github.darkryh.dispatch.viewmodel.ViewModel
import io.github.darkryh.dispatch.viewmodel.ViewModelFactory
import io.github.darkryh.dispatch.viewmodel.ViewModelProviderScope
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass

/**
 * ViewModelFactory backed by Koin.
 */
class KoinViewModelFactory(
    private val koin: Koin = DispatchKoin.koin(),
) : ViewModelFactory,
    SavedStateViewModelFactory {
    override fun <T : ViewModel> create(modelClass: KClass<T>): T = koin.get(clazz = modelClass)

    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        savedStateHandle: SavedStateHandle,
    ): T = koin.get(clazz = modelClass, parameters = { parametersOf(savedStateHandle) })
}

/**
 * Scope that provides a Koin-backed ViewModelProvider.
 */
@Composable
fun KoinViewModelProviderScope(
    koin: Koin = DispatchKoin.koin(),
    content: @Composable () -> Unit,
) {
    ViewModelProviderScope(factory = KoinViewModelFactory(koin), content = content)
}
