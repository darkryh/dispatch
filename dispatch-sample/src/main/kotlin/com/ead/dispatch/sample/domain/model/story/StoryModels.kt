package com.ead.dispatch.sample.domain.model.story

/**
 * Minimal story structures for the sample UI.
 */
data class Story(
    val title: String,
    val volumes: List<Volume> = emptyList(),
)

data class Volume(
    val number: Int,
    val title: String,
    val chapters: List<Chapter> = emptyList(),
) {
    fun totalWordCount(): Int = chapters.sumOf { it.wordCount ?: 0 }
}

data class Chapter(
    val number: Int,
    val title: String,
    val contentRef: String? = null,
    val wordCount: Int? = null,
) {
    fun isComplete(): Boolean = !contentRef.isNullOrBlank()
}

data class Scene(
    val number: Int,
    val title: String? = null,
    val contentRange: String? = null,
)

/**
 * Minimal position info for story progress display.
 */
data class StoryPosition(
    val volumeNumber: Int = 1,
    val chapterNumber: Int = 1,
)
