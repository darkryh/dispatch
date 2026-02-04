package com.ead.dispatch.sample.presentation.entity_editor.util

import com.ead.dispatch.sample.domain.entity.EntityOptionType

object EntityEditorFields {
    val automatic: List<EntityFieldDefinition> = listOf(
        EntityFieldDefinition(
            key = EntityFieldKey.PROMPT,
            label = "Prompt",
            placeholder = "Describe what to generate",
            maxLines = 4,
            helper = "AI generation UI only (logic coming later).",
        ),
        EntityFieldDefinition(
            key = EntityFieldKey.CONSTRAINTS,
            label = "Constraints",
            placeholder = "Any constraints or limitations",
            maxLines = 3,
        ),
    )

    fun manualFieldsFor(type: EntityOptionType): List<EntityFieldDefinition> = when (type) {
        EntityOptionType.LOCATIONS -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.NAME, label = "Name"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.TAGS,
                label = "Tags",
                placeholder = "comma, separated, tags",
                helper = "Comma-separated list",
            ),
        )
        EntityOptionType.ARCS -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.TITLE, label = "Title"),
            EntityFieldDefinition(
                key = EntityFieldKey.SUMMARY,
                label = "Summary",
                placeholder = "Short arc summary",
                maxLines = 3,
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.SCOPE_TYPE,
                label = "Scope Type",
                placeholder = "STORY",
                helper = "STORY, VOLUME, CHAPTER, SCENE, CHARACTER",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.SCOPE_ID,
                label = "Scope Id",
                placeholder = "Optional id (required if not STORY)",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.STATUS,
                label = "Status",
                placeholder = "DRAFT",
                helper = "DRAFT, IN_PROGRESS, FINAL, ARCHIVED",
            ),
        )
        EntityOptionType.WORLD_RULES -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.TITLE, label = "Title"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
        )
        EntityOptionType.CULTURES -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.NAME, label = "Name"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
        )
        EntityOptionType.EVENTS -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.NAME, label = "Name"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
        )
        EntityOptionType.ORGANIZATIONS -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.NAME, label = "Name"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
        )
        EntityOptionType.RELATIONSHIPS -> listOf(
            EntityFieldDefinition(
                key = EntityFieldKey.SUBJECT_TYPE,
                label = "Subject Type",
                placeholder = "character, organization, etc",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.SUBJECT_ID,
                label = "Subject Id",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.OBJECT_TYPE,
                label = "Object Type",
                placeholder = "character, organization, etc",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.OBJECT_ID,
                label = "Object Id",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.RELATION,
                label = "Relation",
                placeholder = "mentor, rival, sibling",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.NOTES,
                label = "Notes",
                placeholder = "Optional notes",
                maxLines = 3,
            ),
        )
        EntityOptionType.LOCATION_FEATURES -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.NAME, label = "Name"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.LOCATION_ID,
                label = "Location Id",
                placeholder = "Optional location id",
            ),
        )
        EntityOptionType.ARTIFACTS -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.NAME, label = "Name"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.OWNER_TYPE,
                label = "Owner Type",
                placeholder = "character, organization, etc",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.OWNER_ID,
                label = "Owner Id",
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.LOCATION_ID,
                label = "Location Id",
                placeholder = "Optional location id",
            ),
        )
        EntityOptionType.TIMELINE -> listOf(
            EntityFieldDefinition(key = EntityFieldKey.TITLE, label = "Title"),
            EntityFieldDefinition(
                key = EntityFieldKey.DESCRIPTION,
                label = "Description",
                placeholder = "Short description",
                maxLines = 3,
            ),
            EntityFieldDefinition(
                key = EntityFieldKey.ORDER_INDEX,
                label = "Order Index",
                placeholder = "1",
                helper = "Numeric ordering in the timeline",
            ),
        )
        else -> emptyList()
    }
}
