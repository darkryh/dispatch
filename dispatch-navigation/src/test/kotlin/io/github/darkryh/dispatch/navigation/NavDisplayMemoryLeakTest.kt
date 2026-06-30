package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.viewmodel.LocalViewModelProvider
import io.github.darkryh.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class LeakRoute(
    val id: Int,
) : NavKey

// Public + no-arg ctor so the default reflective ViewModelFactory can instantiate it.
class ProbeViewModel : ViewModel() {
    val job: Job = viewModelScope.launch { delay(10.seconds) }
}

class NavDisplayMemoryLeakTest {
    @Composable
    private fun captureViewModel(into: (ProbeViewModel) -> Unit) {
        val provider = LocalViewModelProvider.current!!
        into(provider.get(ProbeViewModel::class, "probe"))
    }

    @Test
    fun `popped entry viewmodel is cleared and its scope cancelled`() =
        runBlocking {
            val backStack = NavBackStack<LeakRoute>(LeakRoute(1))
            var poppedVm: ProbeViewModel? = null

            NavCompositionHarness().use { harness ->
                harness.render {
                    NavDisplay(
                        backStack = backStack,
                        entryProvider = { key ->
                            NavEntry(key = key) {
                                captureViewModel { poppedVm = it }
                            }
                        },
                    )
                }

                // Navigate to a second route, capturing its VM, then pop it.
                backStack.navigate(LeakRoute(2))
                var secondVm: ProbeViewModel? = null
                harness.render {
                    NavDisplay(
                        backStack = backStack,
                        entryProvider = { key ->
                            NavEntry(key = key) {
                                val provider = LocalViewModelProvider.current!!
                                secondVm = provider.get(ProbeViewModel::class, "probe")
                            }
                        },
                    )
                }
                val captured = secondVm!!
                assertFalse(captured.isCleared)

                backStack.popBackStack()
                // Re-render after pop.
                harness.render {
                    NavDisplay(
                        backStack = backStack,
                        entryProvider = { key ->
                            NavEntry(key = key) {
                                captureViewModel { poppedVm = it }
                            }
                        },
                    )
                }

                assertTrue(captured.isCleared, "popped entry ViewModel should be cleared")
                withTimeout(1.seconds) { captured.job.join() }
                assertTrue(captured.job.isCancelled, "popped entry scope should be cancelled")
            }
        }

    @Test
    fun `back stack does not retain entry state after NavDisplay leaves composition`() =
        runBlocking {
            val backStack = NavBackStack<LeakRoute>(LeakRoute(1))
            var vm: ProbeViewModel? = null

            val harness = NavCompositionHarness()
            harness.render {
                NavDisplay(
                    backStack = backStack,
                    entryProvider = { key ->
                        NavEntry(key = key) {
                            val provider = LocalViewModelProvider.current!!
                            vm = provider.get(ProbeViewModel::class, "probe")
                        }
                    },
                )
            }
            val captured = vm!!
            assertFalse(captured.isCleared)

            // Render a frame WITHOUT NavDisplay -> NavDisplay leaves composition.
            harness.render { /* nothing */ }

            assertTrue(captured.isCleared, "ViewModel must be cleared when NavDisplay leaves composition")
            withTimeout(1.seconds) { captured.job.join() }
            assertTrue(captured.job.isCancelled, "scope must be cancelled on teardown")

            harness.close()
        }

    @Test
    fun `entry is not strongly retained after NavDisplay leaves composition`() {
        val backStack = NavBackStack<LeakRoute>(LeakRoute(7))
        var ref: WeakReference<NavBackStackEntry<LeakRoute>>? = null

        val harness = NavCompositionHarness()
        harness.render {
            NavDisplay(
                backStack = backStack,
                entryProvider = { key ->
                    NavEntry(key = key) {
                        @Suppress("UNCHECKED_CAST")
                        ref = WeakReference(LocalNavBackStackEntry.current as NavBackStackEntry<LeakRoute>)
                    }
                },
            )
        }
        assertNotNull(ref?.get())

        harness.render { /* drop NavDisplay */ }
        harness.close()

        // Encourage collection; the store should no longer hold a strong ref.
        repeat(5) {
            System.gc()
            Thread.sleep(20)
            if (ref?.get() == null) return
        }
        assertNull(ref?.get(), "entry should not be strongly retained after teardown")
    }

    @Test
    fun `re-pushing same route after pop creates fresh un-cleared state`() =
        runBlocking {
            val backStack = NavBackStack<LeakRoute>(LeakRoute(1))
            var current: ProbeViewModel? = null

            NavCompositionHarness().use { harness ->
                val content: @Composable () -> Unit = {
                    NavDisplay(
                        backStack = backStack,
                        entryProvider = { key ->
                            NavEntry(key = key) {
                                val provider = LocalViewModelProvider.current!!
                                current = provider.get(ProbeViewModel::class, "probe")
                            }
                        },
                    )
                }

                backStack.navigate(LeakRoute(2))
                harness.render(content)
                val first = requireNotNull(current)

                backStack.popBackStack()
                harness.render(content)
                assertTrue(first.isCleared)

                backStack.navigate(LeakRoute(2))
                harness.render(content)
                val second = requireNotNull(current)

                assertNotSame(first, second, "re-pushed route must get a fresh ViewModel")
                assertFalse(second.isCleared, "fresh route state must not be cleared")
            }
        }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)
}
