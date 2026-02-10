package com.ead.dispatch.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavEntryDecoratorSnapshotTest {
    @Test
    fun `snapshotBackStack retries when list mutates during read`() {
        val source = MutatingList(mutableListOf(1, 2, 3))

        val snapshot = snapshotBackStack(source, maxRetries = 4)

        assertEquals(listOf(1, 2), snapshot)
    }

    @Test
    fun `snapshotBackStack uses fallback when retries are exhausted`() {
        val source = AlwaysFailingList(listOf(1, 2, 3))
        val fallback = listOf(7, 8)

        val snapshot = snapshotBackStack(source, fallback = fallback, maxRetries = 3)

        assertEquals(fallback, snapshot)
    }

    private class MutatingList<T>(
        private val delegate: MutableList<T>,
    ) : AbstractList<T>() {
        private var firstRead = true

        override val size: Int
            get() = delegate.size

        override fun get(index: Int): T {
            if (firstRead) {
                firstRead = false
                if (delegate.isNotEmpty()) {
                    delegate.removeAt(delegate.lastIndex)
                }
                throw IndexOutOfBoundsException("Simulated concurrent mutation during iteration")
            }
            return delegate[index]
        }
    }

    private class AlwaysFailingList<T>(
        private val delegate: List<T>,
    ) : AbstractList<T>() {
        override val size: Int
            get() = delegate.size

        override fun get(index: Int): T = throw ConcurrentModificationException("Simulated concurrent modification")
    }
}
