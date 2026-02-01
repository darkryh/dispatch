package com.ead.dispatch.sample.domain.embedding

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.JVMFileDocumentEmbeddingStorage
import ai.koog.rag.vector.JVMTextDocumentEmbedder
import com.ead.dispatch.sample.domain.Pathing
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class EmbeddingIndexService(
    embedderProvider: ChatAgentEmbedder,
    root: Path = Pathing.applicationDirectory,
) {
    private val enabled = true

    private val chatEmbedder = embedderProvider.chatModeLlmEmbedder
    private val storyEmbedder = embedderProvider.storyModeLlmEmbedder

    private val chatRoot = root.resolve("embeddings/chat")
    private val storyRoot = root.resolve("embeddings/story")

    private val storageCache = mutableMapOf<String, EmbeddingBasedDocumentStorage<Path>>()

    suspend fun store(mode: EmbeddingMode, sessionId: String, text: String): String? {
        if (!enabled) return null
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        val storage = storageFor(mode, sessionId)
        val tempFile = writeTempDocument(storageRoot(mode, sessionId), normalized)
        return try {
            storage.store(tempFile, Unit)
        } finally {
            try {
                withContext(Dispatchers.IO) {
                    Files.deleteIfExists(tempFile)
                }
            } catch (_: Exception) {
                // Best-effort cleanup; stored copy lives in storage root.
            }
        }
    }

    suspend fun delete(mode: EmbeddingMode, sessionId: String, docId: String): Boolean {
        if (!enabled) return false
        return storageFor(mode, sessionId).delete(docId)
    }

    suspend fun query(
        mode: EmbeddingMode,
        sessionId: String,
        query: String,
        limit: Int = 8,
        minScore: Double = 0.0,
    ): List<String> {
        if (!enabled) return emptyList()
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        val storage = storageFor(mode, sessionId)
        val results = storage.mostRelevantDocuments(normalized, limit, minScore)
        return withContext(Dispatchers.IO) {
            results.mapNotNull { path ->
                try {
                    Files.readString(path, StandardCharsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun checksum(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalize(text: String): String = text.trim()

    private fun storageFor(mode: EmbeddingMode, sessionId: String): EmbeddingBasedDocumentStorage<Path> {
        val key = "${mode.name}:$sessionId"
        return storageCache.getOrPut(key) {
            val baseRoot = storageRoot(mode, sessionId)
            val embedder = when (mode) {
                EmbeddingMode.CHAT -> chatEmbedder
                EmbeddingMode.STORY -> storyEmbedder
            }
            buildStorage(embedder, baseRoot)
        }
    }

    private fun buildStorage(embedder: Embedder, root: Path): EmbeddingBasedDocumentStorage<Path> {
        Files.createDirectories(root)
        val documentEmbedder = JVMTextDocumentEmbedder(embedder)
        return JVMFileDocumentEmbeddingStorage(documentEmbedder, root)
    }

    private fun storageRoot(mode: EmbeddingMode, sessionId: String): Path {
        val baseRoot = when (mode) {
            EmbeddingMode.CHAT -> chatRoot
            EmbeddingMode.STORY -> storyRoot
        }
        return baseRoot.resolve(sessionId)
    }

    private suspend fun writeTempDocument(root: Path, text: String): Path =
        withContext(Dispatchers.IO) {
            val tempDir = root.resolve("incoming")
            Files.createDirectories(tempDir)
            val tempFile = Files.createTempFile(tempDir, "doc-", ".txt")
            Files.writeString(tempFile, text, StandardCharsets.UTF_8)
            tempFile
        }
}
