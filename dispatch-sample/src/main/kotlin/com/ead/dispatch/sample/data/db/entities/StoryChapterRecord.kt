package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.ContentType
import kotlinx.serialization.Serializable

@Serializable
data class StoryChapterRecord(
    /** Stable identifier for the chapter record. */
    val id: String,
    /** Parent volume id for ordering and grouping. */
    val volumeId: String,
    /** Chapter number within the volume. */
    val number: Long,
    /** Display title used in the UI and outlines. */
    val title: String,
    /** Short human summary; keep small, not the full prose. */
    val summary: String? = null,
    /** Content metadata grouped for file operations. */
    val content: ChapterContent? = null,
    /** High-level beats to guide planning and retrieval (normalized list). */
    val keyEvents: List<String> = emptyList(),
    /** Optional target word count for planning. */
    val targetWordCount: Long? = null,
    /** Draft state for workflow gating (draft, final, etc.). */
    val status: ContentStatus? = null,
    /** Creation time for ordering and audits. */
    val createdAt: Long,
    /** Last update time for metadata changes. */
    val updatedAt: Long,
) {
    @Serializable
    data class ChapterContent(
        /** File reference to the chapter markdown (source of truth). */
        val ref: String? = null,
        /** Content format for tool routing (e.g., markdown). */
        val type: ContentType? = null,
        /** Content checksum used to detect stale indexes. */
        val checksum: String? = null,
        /** Last known content update time from file operations. */
        val updatedAt: Long? = null,
        /** Optional range hint within the file for AI edits. */
        val range: String? = null,
        /** Cached word count derived from the referenced file. */
        val wordCount: Long? = null,
    )
}
