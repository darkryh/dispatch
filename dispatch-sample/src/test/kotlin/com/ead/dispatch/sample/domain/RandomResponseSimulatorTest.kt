package com.ead.dispatch.sample.domain

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RandomResponseSimulatorTest {
    @Test
    fun `response is emitted as multiple irregular chunks`(): Unit =
        runBlocking {
            val chunks =
                RandomResponseSimulator(
                    random = Random(7),
                    minimumDelayMillis = 0,
                    maximumDelayMillis = 0,
                ).stream("terminal layouts").toList()

            assertTrue(chunks.size > 2)
            assertTrue(chunks.map(String::length).distinct().size > 1)
            assertContains(chunks.joinToString(""), "terminal layouts")
        }
}
