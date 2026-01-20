package com.ead.dispatch.sample.data.db.type

import kotlinx.serialization.Serializable

@Serializable
enum class StoryFactType {
    WORLD_RULE,
    MAGIC_RULE,
    TECH_RULE,
    POLITICS,
    GEOGRAPHY,
    TIMELINE,
    HISTORY,
    CULTURE,
    RELIGION,
    ECONOMY,
    FACTION,
    ORGANIZATION,
    CHARACTER_FACT,
    RELATIONSHIP,
    ITEM,
    LORE,
    CONFLICT,
    LANGUAGE,
    CUSTOM;

    companion object {
        fun fromDb(value: String): StoryFactType =
            entries.firstOrNull { it.name == value } ?: CUSTOM
    }
}
