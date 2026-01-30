package com.ead.dispatch.sample.domain.embedding

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.FileVectorStorage
import ai.koog.rag.vector.TextDocumentEmbedder
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

    private val storageCache = mutableMapOf<String, EmbeddingBasedDocumentStorage<String>>()

    suspend fun store(mode: EmbeddingMode, sessionId: String, text: String): String? {
        if (!enabled) return null
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        return storageFor(mode, sessionId).store(normalized, Unit)
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
        return results.toList()
    }

    fun checksum(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalize(text: String): String = text.trim()

    private fun storageFor(mode: EmbeddingMode, sessionId: String): EmbeddingBasedDocumentStorage<String> {
        val key = "${mode.name}:$sessionId"
        return storageCache.getOrPut(key) {
            val baseRoot = when (mode) {
                EmbeddingMode.CHAT -> chatRoot
                EmbeddingMode.STORY -> storyRoot
            }
            val embedder = when (mode) {
                EmbeddingMode.CHAT -> chatEmbedder
                EmbeddingMode.STORY -> storyEmbedder
            }
            buildStorage(embedder, baseRoot.resolve(sessionId))
        }
    }

    private fun buildStorage(embedder: Embedder, root: Path): EmbeddingBasedDocumentStorage<String> {
        Files.createDirectories(root)
        val documentProvider = StringDocumentProvider
        val documentEmbedder = TextDocumentEmbedder(documentProvider, embedder)
        val vectorStorage = FileVectorStorage(
            documentProvider,
            JVMFileSystemProvider.ReadWrite,
            root,
        )
        return EmbeddingBasedDocumentStorage(documentEmbedder, vectorStorage)
    }

    private object StringDocumentProvider : DocumentProvider<Path, String> {
        override suspend fun document(path: Path): String =
            withContext(Dispatchers.IO) {
                Files.readString(path, StandardCharsets.UTF_8)
            }

        override suspend fun text(document: String): CharSequence = document
    }
}
