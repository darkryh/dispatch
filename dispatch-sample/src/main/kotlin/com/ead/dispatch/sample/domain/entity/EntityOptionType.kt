package com.ead.dispatch.sample.domain.entity

import com.ead.dispatch.sample.domain.model.story.WriterMode

enum class EntityOptionType(
    val id: String,
    val title: String,
    val description: String,
    val modes: Set<WriterMode>,
    val supportsCreate: Boolean = false,
) {
    CHARACTERS(
        id = "characters",
        title = "Characters",
        description = "List character profiles gathered in CHAT mode",
        modes = setOf(WriterMode.CHAT),
        supportsCreate = true,
    ),
    LOCATIONS(
        id = "locations",
        title = "Locations",
        description = "List world locations gathered in CHAT mode",
        modes = setOf(WriterMode.CHAT),
    ),
    ARCS(
        id = "arcs",
        title = "Arcs",
        description = "List narrative arcs gathered in CHAT mode",
        modes = setOf(WriterMode.CHAT),
    ),
    WORLD_RULES(
        id = "world-rules",
        title = "World Rules",
        description = "List world rules and constraints",
        modes = setOf(WriterMode.CHAT),
    ),
    CULTURES(
        id = "cultures",
        title = "Cultures",
        description = "List cultures and social groups",
        modes = setOf(WriterMode.CHAT),
    ),
    EVENTS(
        id = "events",
        title = "Events",
        description = "List historical events",
        modes = setOf(WriterMode.CHAT),
    ),
    ORGANIZATIONS(
        id = "organizations",
        title = "Organizations",
        description = "List organizations and factions",
        modes = setOf(WriterMode.CHAT),
    ),
    RELATIONSHIPS(
        id = "relationships",
        title = "Relationships",
        description = "List relationships between entities",
        modes = setOf(WriterMode.CHAT),
    ),
    LOCATION_FEATURES(
        id = "location-features",
        title = "Location Features",
        description = "List location features and landmarks",
        modes = setOf(WriterMode.CHAT),
    ),
    ARTIFACTS(
        id = "artifacts",
        title = "Artifacts",
        description = "List artifacts and important items",
        modes = setOf(WriterMode.CHAT),
    ),
    TIMELINE(
        id = "timeline",
        title = "Timeline Entries",
        description = "List timeline entries",
        modes = setOf(WriterMode.CHAT),
    ),
    VOLUMES(
        id = "volumes",
        title = "Volumes",
        description = "List volumes used in STORY mode",
        modes = setOf(WriterMode.CHAT_STORY),
    ),
    CHAPTERS(
        id = "chapters",
        title = "Chapters",
        description = "List chapters used in STORY mode",
        modes = setOf(WriterMode.CHAT_STORY),
    ),
    SCENES(
        id = "scenes",
        title = "Scenes",
        description = "List scenes used in STORY mode",
        modes = setOf(WriterMode.CHAT_STORY),
    );

    companion object {
        val all: List<EntityOptionType> = entries

        val chatQuickJumpTypes: List<EntityOptionType> = listOf(
            CHARACTERS,
            LOCATIONS,
            ARCS,
            WORLD_RULES,
            CULTURES,
            EVENTS,
        )

        val storyChatShortcutTypes: List<EntityOptionType> = listOf(
            CHARACTERS,
            LOCATIONS,
            ARCS,
            WORLD_RULES,
        )

        fun fromId(id: String?): EntityOptionType? {
            if (id.isNullOrBlank()) return null
            val normalized = id.trim().lowercase()
            return entries.firstOrNull { it.id == normalized }
        }
    }
}
