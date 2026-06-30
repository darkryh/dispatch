package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.Composable

class Composer : AutoCloseable {
    private val composition = DispatchComposition()

    fun startComposition() = Unit

    fun endComposition() = Unit

    internal fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
        composition.awaitIdle()
    }

    override fun close() = composition.close()
}

fun withComposer(
    composer: Composer,
    block: @Composable () -> Unit,
) = composer.setContent(block)
