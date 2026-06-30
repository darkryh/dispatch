package io.github.darkryh.dispatch.koin

import io.github.darkryh.dispatch.navigation.ROUTE_PAYLOAD_KEY
import io.github.darkryh.dispatch.runtime.DispatchConfig
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import io.github.darkryh.dispatch.viewmodel.ViewModel
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.KoinAppDeclaration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

private val shutdownHookInstalled = AtomicBoolean(false)

@Volatile
private var shutdownHookThread: Thread? = null

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
        onExit { DispatchKoin.stop() }
        DispatchKoin.installShutdownHook()
    }
    val viewModels =
        if (validateViewModels.isNotEmpty()) {
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
        removeShutdownHook()
    }

    fun koin(): Koin =
        runCatching { GlobalContext.get() }.getOrElse { error ->
            throw IllegalStateException(
                "Koin has not been started. Call DispatchConfig.koin { ... } " +
                    "or startKoin { ... } before using Dispatch Koin integration.",
                error,
            )
        }

    fun installShutdownHook() {
        if (shutdownHookInstalled.compareAndSet(false, true)) {
            val thread = Thread { stop() }
            shutdownHookThread = thread
            Runtime.getRuntime().addShutdownHook(thread)
        }
    }

    private fun removeShutdownHook() {
        val thread = shutdownHookThread ?: return
        // When stop() runs from within the hook itself (JVM shutting down), removal is illegal.
        // Reset the flag regardless so an explicit stop allows a later reinstall.
        runCatching { Runtime.getRuntime().removeShutdownHook(thread) }
        shutdownHookThread = null
        shutdownHookInstalled.set(false)
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
        val instance =
            resolveViewModel(
                modelClass = modelClass,
                koin = koin,
                savedStateHandleProvider = savedStateHandleProvider,
            ) ?: return

        if (!closeAfterValidation) return

        val name = modelClass.qualifiedName ?: modelClass.simpleName ?: "Unknown"
        val closeFailure = runCatching { instance.close() }.exceptionOrNull()
        if (closeFailure != null) {
            throw IllegalStateException(
                "Koin ViewModel failed lifecycle validation (close): $name",
                closeFailure,
            )
        }
        check(instance.isCleared) {
            "Koin ViewModel failed lifecycle validation (clear): $name"
        }
    }
}

private fun resolveViewModel(
    modelClass: KClass<out ViewModel>,
    koin: Koin,
    savedStateHandleProvider: (() -> SavedStateHandle)?,
): ViewModel? {
    val primaryResolution = runCatching { koin.get<ViewModel>(clazz = modelClass) }
    val primaryFailure = primaryResolution.exceptionOrNull()
    if (primaryFailure == null) return primaryResolution.getOrThrow()

    val savedStateHandle =
        savedStateHandleProvider?.invoke() ?: SavedStateHandle().apply {
            this[ROUTE_PAYLOAD_KEY] = "{}"
        }
    val secondaryResolution =
        runCatching {
            koin.get<ViewModel>(
                clazz = modelClass,
                parameters = { parametersOf(savedStateHandle) },
            )
        }

    val secondaryFailure = secondaryResolution.exceptionOrNull()
    if (secondaryFailure == null) return secondaryResolution.getOrThrow()

    if (savedStateHandleProvider == null && isRoutePayloadFailure(secondaryFailure)) {
        return null
    }

    val name = modelClass.qualifiedName ?: modelClass.simpleName ?: "Unknown"
    val error = IllegalStateException("Koin could not resolve ViewModel: $name", primaryFailure)
    error.addSuppressed(secondaryFailure)
    throw error
}

private fun isRoutePayloadFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { cause ->
        if (cause is IllegalArgumentException) {
            cause.message?.startsWith("Missing route payload for") == true
        } else {
            cause::class.qualifiedName == "kotlinx.serialization.SerializationException"
        }
    }
