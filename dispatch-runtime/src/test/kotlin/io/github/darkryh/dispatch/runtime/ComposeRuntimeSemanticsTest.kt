package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ComposeRuntimeSemanticsTest {
    @Test
    fun `state writes invalidate readers and recompose`() {
        val state = mutableStateOf(1)
        var observed = 0
        DispatchComposition().use { composition ->
            composition.setContent { observed = state.value }
            assertEquals(1, observed)
            state.value = 2
            composition.awaitIdle()
            assertEquals(2, observed)
        }
    }

    @Test
    fun `keyed groups preserve remembered identity when siblings move`() {
        val items = mutableStateListOf("a", "b", "c")
        val identities = mutableMapOf<String, Any>()
        DispatchComposition().use { composition ->
            composition.setContent {
                items.forEach { item ->
                    key(item) { identities[item] = remember { Any() } }
                }
            }
            val original = identities.toMap()
            items.add(0, items.removeAt(2))
            composition.awaitIdle()
            original.forEach { (item, identity) -> assertSame(identity, identities[item]) }
        }
    }

    @Test
    fun `effects dispose when keyed content leaves composition`() {
        val visible = mutableStateOf(true)
        var disposed = 0
        DispatchComposition().use { composition ->
            composition.setContent {
                if (visible.value) {
                    DisposableEffect(Unit) { onDispose { disposed++ } }
                }
            }
            visible.value = false
            composition.awaitIdle()
            assertEquals(1, disposed)
        }
    }
}
