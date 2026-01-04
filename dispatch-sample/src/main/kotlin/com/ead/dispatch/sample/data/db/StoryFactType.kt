package com.ead.dispatch.sample.data.db

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
    LOCATION,
    CHARACTER_FACT,
    RELATIONSHIP,
    ITEM,
    LORE,
    CONFLICT,
    STAKES,
    THEME,
    TONE,
    LANGUAGE,
    CUSTOM;

    companion object {
        fun fromDb(value: String): StoryFactType =
            entries.firstOrNull { it.name == value } ?: CUSTOM
    }
}
