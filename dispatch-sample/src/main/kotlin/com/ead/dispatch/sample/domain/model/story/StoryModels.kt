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
    fun totalWordCount(): Int = chapters.sumOf { it.wordCount() }
}

data class Chapter(
    val number: Int,
    val title: String,
    val content: String = "",
) {
    fun wordCount(): Int = content.split(Regex("\\s+")).count { it.isNotBlank() }
    fun isComplete(): Boolean = content.isNotBlank()
}

/**
 * Minimal position info for story progress display.
 */
data class StoryPosition(
    val volumeNumber: Int = 1,
    val chapterNumber: Int = 1,
)
