package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.RagSourceType

data class RagDocumentRecord(
    /** Stable identifier for the indexed chunk. */
    val docId: String,
    /** Parent session id used for retrieval scoping. */
    val sessionId: String,
    /** Optional story id for story-scoped search. */
    val storyId: String?,
    /** Optional volume id for volume-scoped search. */
    val volumeId: String?,
    /** Optional chapter id for chapter-scoped search. */
    val chapterId: String?,
    /** Chunk order within the source document. */
    val chunkIndex: Long,
    /** Source type (file, note, chat, etc.). */
    val sourceType: RagSourceType,
    /** Source reference, usually a file path or URI. */
    val sourceRef: String?,
    /** Content checksum to detect stale embeddings. */
    val checksum: String?,
    /** Creation time for indexing audits. */
    val createdAt: Long,
)
