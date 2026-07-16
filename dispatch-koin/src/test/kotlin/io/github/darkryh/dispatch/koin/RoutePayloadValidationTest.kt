package io.github.darkryh.dispatch.koin

import io.github.darkryh.dispatch.navigation.ROUTE_PAYLOAD_KEY
import io.github.darkryh.dispatch.runtime.DispatchConfig
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import io.github.darkryh.dispatch.viewmodel.ViewModel
import kotlinx.serialization.MissingFieldException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Regression tests for startup validation against routes with required (no-default) fields.
 *
 * Validation resolves every registered ViewModel with a synthetic SavedStateHandle whose route
 * payload is the literal "{}". Decoding that payload into a route with a required field throws
 * kotlinx.serialization's MissingFieldException — a SUBCLASS of SerializationException. The
 * previous filter compared the exception class name exactly, missed the subclass, and crashed
 * startup for any app whose routes carry required arguments (including the official tutorial).
 */
class RoutePayloadValidationTest {
    /**
     * Mimics a ViewModel doing `savedStateHandle.toRoute<DetailRoute>()` in its init block where
     * DetailRoute has a required field: decoding the synthetic "{}" payload throws
     * MissingFieldException (the real exception type kotlinx.serialization throws for it).
     */
    private class RequiredFieldRouteViewModel(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        init {
            val payload =
                savedStateHandle.get<String>(ROUTE_PAYLOAD_KEY)
                    ?: throw IllegalArgumentException("Missing route payload for RequiredFieldRoute")
            if (payload == "{}") {
                throw MissingFieldException("noteId", "detail")
            }
        }
    }

    @BeforeTest
    fun resetKoin() {
        DispatchKoin.stop()
    }

    @AfterTest
    fun stopKoin() {
        DispatchKoin.stop()
    }

    @Test
    fun `startup validation tolerates viewmodels whose routes have required fields`() {
        val config = DispatchConfig()

        // Must not throw: the MissingFieldException raised while decoding the synthetic "{}"
        // payload is an expected validation-time condition, not a wiring error.
        config.koin {
            modules(
                dispatchModule {
                    viewModel { RequiredFieldRouteViewModel(get()) }
                },
            )
        }
    }

    @Test
    fun `startup validation constructs the viewmodel when a seeded handle is provided`() {
        val config = DispatchConfig()

        config.koin {
            modules(
                dispatchModule {
                    viewModel(
                        savedStateHandleProvider = {
                            SavedStateHandle().apply {
                                this[ROUTE_PAYLOAD_KEY] = """{"noteId":"seed"}"""
                            }
                        },
                    ) { RequiredFieldRouteViewModel(get()) }
                },
            )
        }
    }
}
