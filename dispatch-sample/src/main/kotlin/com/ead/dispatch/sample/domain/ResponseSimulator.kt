package com.ead.dispatch.sample.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

fun interface ResponseSimulator {
    fun stream(prompt: String): Flow<String>
}

class RandomResponseSimulator(
    private val random: Random = Random.Default,
    private val minimumDelayMillis: Long = 35,
    private val maximumDelayMillis: Long = 140,
) : ResponseSimulator {
    override fun stream(prompt: String): Flow<String> =
        flow {
            val response = responseFor(prompt)
            var offset = 0
            while (offset < response.length) {
                val chunkSize = random.nextInt(from = 2, until = 13)
                val end = (offset + chunkSize).coerceAtMost(response.length)
                if (maximumDelayMillis > 0) {
                    delay(random.nextLong(minimumDelayMillis, maximumDelayMillis + 1).milliseconds)
                }
                emit(response.substring(offset, end))
                offset = end
            }
        }

    private fun responseFor(prompt: String): String {
        val subject = prompt.trim().take(48).ifEmpty { "that request" }
        val responses =
            listOf(
                "I received '$subject'. This simulated reply arrives in uneven chunks so the terminal " +
                    "exercises the same recomposition path as a network stream.",
                "Here is a local response to '$subject'. You can navigate away, return, inspect the " +
                    "partial content, or cancel while chunks are still arriving.",
                "The UI is handling '$subject' without an AI dependency. Random pacing, message growth, " +
                    "scrolling, and disabled input states are all active in this sample.",
            )
        return responses[random.nextInt(responses.size)]
    }
}

internal fun responseSimulatorFromEnvironment(environment: Map<String, String> = System.getenv()): ResponseSimulator {
    val seed = environment["DISPATCH_SAMPLE_STREAM_SEED"]?.toIntOrNull()
    val minimumDelay = environment["DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS"]?.toLongOrNull() ?: 35L
    val maximumDelay = environment["DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS"]?.toLongOrNull() ?: 140L
    require(minimumDelay >= 0) { "Minimum stream delay must be non-negative" }
    require(maximumDelay >= minimumDelay) { "Maximum stream delay must not be less than minimum" }
    return RandomResponseSimulator(
        random = seed?.let(::Random) ?: Random.Default,
        minimumDelayMillis = minimumDelay,
        maximumDelayMillis = maximumDelay,
    )
}
