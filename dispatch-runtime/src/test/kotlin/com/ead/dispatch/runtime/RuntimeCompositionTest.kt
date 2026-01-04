package com.ead.dispatch.runtime

import com.ead.dispatch.state.MutableState
import com.ead.dispatch.state.Saver
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.rememberSaveable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class RuntimeCompositionTest {
    @Test
    fun `remember caches values until key changes`() {
        val composer = Composer()
        var counter = 0

        fun compose(key: Any?): Int {
            return withComposer(composer) {
                composer.startComposition()
                val value = remember(key) { counter++ }
                composer.endComposition()
                value
            }
        }

        assertEquals(0, compose("alpha"))
        assertEquals(0, compose("alpha"))
        assertEquals(1, counter)

        assertEquals(1, compose("beta"))
        assertEquals(2, counter)
    }

    @Test
    fun `rememberSaveable restores from saved state`() {
        val registry = SavedStateRegistry()
        val saver = object : Saver<MutableState<String>, Any> {
            override fun save(value: MutableState<String>): Any = value.value
            override fun restore(value: Any): MutableState<String> = mutableStateOf(value as String)
        }

        fun compose(composer: Composer): MutableState<String> {
            return withComposer(composer) {
                composer.startComposition()
                val state = rememberSaveable(saver) { mutableStateOf("initial") }
                composer.endComposition()
                state
            }
        }

        val firstComposer = Composer(registry)
        val original = compose(firstComposer)
        original.value = "updated"
        val saved = registry.performSave()

        val restoredRegistry = SavedStateRegistry().apply { performRestore(saved) }
        val restoredComposer = Composer(restoredRegistry)
        val restored = compose(restoredComposer)

        assertEquals("updated", restored.value)
        assertNotSame(original, restored)
    }

    @Test
    fun `disposable effect disposes when key changes`() {
        val composer = Composer()
        var started = 0
        var disposed = 0

        fun compose(key: Any?) {
            withComposer(composer) {
                composer.startComposition()
                DisposableEffect(key) {
                    started++
                    onDispose { disposed++ }
                }
                composer.endComposition()
            }
        }

        compose("A")
        assertEquals(1, started)
        assertEquals(0, disposed)

        compose("A")
        assertEquals(1, started)
        assertEquals(0, disposed)

        compose("B")
        assertEquals(2, started)
        assertEquals(1, disposed)
    }

    @Test
    fun `disposable effect disposes when content removed`() {
        val composer = Composer()
        var disposed = 0
        var show = true

        fun compose() {
            withComposer(composer) {
                composer.startComposition()
                if (show) {
                    DisposableEffect(Unit) {
                        onDispose { disposed++ }
                    }
                }
                composer.endComposition()
            }
        }

        compose()
        assertEquals(0, disposed)

        show = false
        compose()
        assertEquals(1, disposed)
    }

    @Test
    fun `saved state handle caches values`() {
        val handle = SavedStateHandle()
        val first = handle.getOrPut("key") { "value" }
        val second = handle.getOrPut("key") { "other" }

        assertEquals("value", first)
        assertEquals("value", second)
        assertTrue(handle.contains("key"))
    }

    @Test
    fun `clear slots in range disposes only targeted slots`() {
        val composer = Composer()
        var disposed = 0

        fun compose() {
            withComposer(composer) {
                composer.startComposition()
                DisposableEffect("A") { onDispose { disposed += 1 } }
                DisposableEffect("B") { onDispose { disposed += 10 } }
                DisposableEffect("C") { onDispose { disposed += 100 } }
                composer.endComposition()
            }
        }

        compose()
        composer.clearSlotsInRange(0, 2)
        assertEquals(11, disposed)

        composer.clearSlotsInRange(2, 3)
        assertEquals(111, disposed)
    }
}
