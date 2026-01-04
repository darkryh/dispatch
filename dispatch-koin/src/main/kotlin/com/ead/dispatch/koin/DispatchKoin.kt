package com.ead.dispatch.koin

import com.ead.dispatch.navigation.ROUTE_PAYLOAD_KEY
import com.ead.dispatch.runtime.DispatchConfig
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.viewmodel.ViewModel
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.core.parameter.parametersOf
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

private val shutdownHookInstalled = AtomicBoolean(false)

/**
 * Configure Koin from a DispatchApplication config block.
 *
 * When no explicit ViewModel list is provided, ViewModels registered via dispatchModule
 * are resolved and closed to validate lifecycle cleanup.
 */
fun DispatchConfig.koin(
    vararg validateViewModels: KClass<out ViewModel>,
    stopOnExit: Boolean = true,
    appDeclaration: KoinAppDeclaration,
): KoinApplication {
    val application = DispatchKoin.start(appDeclaration)
    if (stopOnExit) {
        DispatchKoin.installShutdownHook()
    }
    val viewModels = if (validateViewModels.isNotEmpty()) {
        validateViewModels.map { modelClass ->
            RegisteredViewModel(
                modelClass = modelClass,
                closeAfterValidation = false,
                savedStateHandleProvider = null,
            )
        }
    } else {
        DispatchKoinRegistry.registeredViewModels()
    }
    if (viewModels.isNotEmpty()) {
        DispatchKoin.validateRegisteredViewModels(viewModels, koin = application.koin)
    }
    return application
}

/**
 * Helpers for integrating Koin with Dispatch.
 */
object DispatchKoin {
    fun start(appDeclaration: KoinAppDeclaration): KoinApplication = startKoin(appDeclaration)

    fun stop() {
        stopKoin()
        DispatchKoinRegistry.clear()
    }

    fun koin(): Koin {
        return runCatching { GlobalContext.get() }.getOrElse { error ->
            throw IllegalStateException(
                "Koin has not been started. Call DispatchConfig.koin { ... } or startKoin { ... } before using Dispatch Koin integration.",
                error
            )
        }
    }

    fun installShutdownHook() {
        if (shutdownHookInstalled.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread { stopKoin() })
        }
    }

    internal fun validateRegisteredViewModels(
        viewModels: Iterable<RegisteredViewModel>,
        koin: Koin = koin(),
    ) {
        viewModels.forEach { viewModel ->
            validateViewModel(
                modelClass = viewModel.modelClass,
                koin = koin,
                closeAfterValidation = viewModel.closeAfterValidation,
                savedStateHandleProvider = viewModel.savedStateHandleProvider,
            )
        }
    }

    /**
     * Validate that the given ViewModels can be resolved from Koin.
     *
     * This resolves instances to confirm definitions are present. Set closeAfterValidation
     * to true if it's safe to close the resolved instance after validation.
     */
    fun validateViewModels(
        vararg modelClasses: KClass<out ViewModel>,
        koin: Koin = koin(),
        closeAfterValidation: Boolean = false,
    ) {
        modelClasses.forEach { modelClass ->
            validateViewModel(
                modelClass = modelClass,
                koin = koin,
                closeAfterValidation = closeAfterValidation,
            )
        }
    }

    private fun validateViewModel(
        modelClass: KClass<out ViewModel>,
        koin: Koin,
        closeAfterValidation: Boolean,
        savedStateHandleProvider: (() -> SavedStateHandle)? = null,
    ) {
        val instance: ViewModel = try {
            koin.get<ViewModel>(clazz = modelClass)
        } catch (primary: Exception) {
            try {
                val savedStateHandle = savedStateHandleProvider?.invoke()
                    ?: SavedStateHandle().apply {
                        this[ROUTE_PAYLOAD_KEY] = "{}"
                    }
                koin.get<ViewModel>(
                    clazz = modelClass,
                    parameters = { parametersOf(savedStateHandle) },
                )
            } catch (secondary: Exception) {
                if (savedStateHandleProvider == null && isRoutePayloadFailure(secondary)) {
                    return
                }
                val name = modelClass.qualifiedName ?: modelClass.simpleName ?: "Unknown"
                val error = IllegalStateException("Koin could not resolve ViewModel: $name", primary)
                error.addSuppressed(secondary)
                throw error
            }
        }

        if (closeAfterValidation) {
            val name = modelClass.qualifiedName ?: modelClass.simpleName ?: "Unknown"
            try {
                instance.close()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Koin ViewModel failed lifecycle validation (close): $name",
                    e
                )
            }
            if (!instance.isCleared) {
                throw IllegalStateException(
                    "Koin ViewModel failed lifecycle validation (clear): $name"
                )
            }
        }
    }
}

private fun isRoutePayloadFailure(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }.any { cause ->
        when (cause) {
            is IllegalArgumentException -> cause.message?.startsWith("Missing route payload for") == true
            else -> cause::class.qualifiedName == "kotlinx.serialization.SerializationException"
        }
    }
}
