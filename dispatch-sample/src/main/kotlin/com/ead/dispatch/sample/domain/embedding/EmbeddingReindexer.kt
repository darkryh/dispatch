package com.ead.dispatch.sample.domain.embedding

import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class EmbeddingReindexer(
    private val repository: StructuredIndexRepository,
) {
    private val enabled = System.getenv("DISPATCH_EMBEDDING_REINDEX_ENABLED")
        ?.equals("false", ignoreCase = true) != true
    private val intervalSeconds = System.getenv("DISPATCH_EMBEDDING_REINDEX_INTERVAL_SECONDS")
        ?.toLongOrNull()
        ?: 600L

    suspend fun startPeriodic() {
        if (!enabled) return
        if (intervalSeconds <= 0) return
        while (true) {
            try {
                repository.reindexChatEmbeddings()
            } catch (e: Exception) {
                System.err.println("Embedding reindex failed: ${e.message}")
            }
            delay(intervalSeconds.seconds)
        }
    }
}
