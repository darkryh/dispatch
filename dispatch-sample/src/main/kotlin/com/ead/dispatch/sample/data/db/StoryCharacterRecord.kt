package com.ead.dispatch.sample.data.db

data class StoryCharacterRecord(
    val id: String,
    val storyId: String,
    val name: String,
    val description: String?,
    val traitsJson: String?,
    val roles: List<String> = emptyList(),
    val goal: String? = null,
    val motivation: String? = null,
    val flaw: String? = null,
    val arc: String? = null,
    val temperament: String? = null,
    val age: String? = null,
    val pronouns: String? = null,
    val occupation: String? = null,
    val backstory: String? = null,
    val voice: String? = null,
    val internalConflict: String? = null,
    val quirks: List<String> = emptyList(),
    val createdAt: Long,
)
