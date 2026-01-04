package com.ead.dispatch.koin

import com.ead.dispatch.navigation.ROUTE_PAYLOAD_KEY
import com.ead.dispatch.runtime.DispatchConfig
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.viewmodel.ViewModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DispatchKoinTest {
    private class TestViewModel : ViewModel()

    private class RouteViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        init {
            if (!savedStateHandle.contains(ROUTE_PAYLOAD_KEY)) {
                throw IllegalArgumentException("Missing route payload for demo")
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
    fun `koin viewmodel factory resolves viewmodels`() {
        DispatchKoin.start {
            modules(dispatchModule { viewModel { TestViewModel() } })
        }

        val factory = KoinViewModelFactory()
        val viewModel = factory.create(TestViewModel::class)

        assertEquals(TestViewModel::class, viewModel::class)
    }

    @Test
    fun `dispatch config koin validates viewmodels`() {
        val config = DispatchConfig()

        config.koin {
            modules(dispatchModule { viewModel { TestViewModel() } })
        }
    }

    @Test
    fun `validation skips viewmodels missing route payload`() {
        val config = DispatchConfig()

        config.koin {
            modules(dispatchModule {
                viewModel { (savedStateHandle: SavedStateHandle) -> RouteViewModel(savedStateHandle) }
            })
        }
    }

    @Test
    fun `validation uses saved state handle provider when supplied`() {
        val config = DispatchConfig()
        val used = AtomicBoolean(false)

        config.koin {
            modules(dispatchModule {
                viewModel(savedStateHandleProvider = {
                    used.set(true)
                    SavedStateHandle().apply { this[ROUTE_PAYLOAD_KEY] = "{}" }
                }) { (savedStateHandle: SavedStateHandle) ->
                    RouteViewModel(savedStateHandle)
                }
            })
        }

        assertTrue(used.get())
    }

    @Test
    fun `validate viewmodels throws when missing`() {
        DispatchKoin.start { }

        assertFailsWith<IllegalStateException> {
            DispatchKoin.validateViewModels(TestViewModel::class)
        }
    }
}
