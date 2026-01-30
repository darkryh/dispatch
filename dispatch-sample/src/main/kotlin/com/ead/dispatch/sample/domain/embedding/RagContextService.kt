package com.ead.dispatch.sample.domain.embedding

import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository

class RagContextService(
    private val repository: StructuredIndexRepository,
    private val embeddingIndexService: EmbeddingIndexService,
) {
    suspend fun getRagContextChunks(
        storyId: String,
        query: String,
        limit: Int = 8,
    ): List<RagContextChunk> {
        val sessionId = repository.getStoryById(storyId)?.sessionId ?: storyId
        val results = embeddingIndexService.query(
            mode = EmbeddingMode.CHAT,
            sessionId = sessionId,
            query = query,
            limit = limit,
        )
        return results.map { parseRagChunk(it) }
    }

    private fun parseRagChunk(text: String): RagContextChunk {
        val lines = text.lines()
        val header = lines.firstOrNull()?.trim().orEmpty()
        val content = lines.drop(1).joinToString("\n").trim().ifEmpty { text.trim() }
        val match = HEADER_REGEX.find(header)
        return if (match != null) {
            RagContextChunk(
                type = match.groupValues[1].trim(),
                label = match.groupValues[2].trim(),
                content = content,
            )
        } else {
            RagContextChunk(
                type = "Context",
                label = null,
                content = text.trim(),
            )
        }
    }

    private companion object {
        val HEADER_REGEX = Regex("^\\[(.+?):\\s*(.+?)]$")
    }
}
