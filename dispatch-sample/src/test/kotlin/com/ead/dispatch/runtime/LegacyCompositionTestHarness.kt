package com.ead.dispatch.runtime

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.LayoutNode

/** Transitional test syntax backed by the real Compose runtime. */
class Composer : AutoCloseable {
    private val composition = DispatchComposition()

    fun startComposition() = Unit

    fun endComposition() = Unit

    fun getRootNode(): LayoutNode? = composition.root.children.singleOrNull() ?: composition.root.takeIf { it.children.isNotEmpty() }

    internal fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
        composition.awaitIdle()
    }

    override fun close() = composition.close()
}

fun withComposer(
    composer: Composer,
    block: @Composable () -> Unit,
) {
    composer.setContent(block)
}
