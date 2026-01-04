package com.ead.dispatch.sample.domain.model.story

/**
 * Minimal story context for the sample UI.
 */
data class StoryContext(
    val characters: List<String> = emptyList(),
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
) {
    fun isComplete(): Boolean =
        characters.isNotEmpty() &&
            genre != null &&
            setting != null &&
            plotOutline != null

    fun getMissingElements(): List<String> = buildList {
        if (characters.isEmpty()) add("characters")
        if (genre == null) add("genre")
        if (setting == null) add("setting")
        if (plotOutline == null) add("plot outline")
    }

    companion object {
        val EMPTY = StoryContext()
    }
}
