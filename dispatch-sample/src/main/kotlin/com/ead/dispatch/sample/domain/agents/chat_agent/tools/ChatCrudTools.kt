package com.ead.dispatch.sample.domain.agents.chat_agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryFactRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.StoryFactType
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class ChatCrudTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {
    private val json = Json { ignoreUnknownKeys = true }

    @Tool
    @LLMDescription("Apply a chat-mode CRUD action to story data (story, character, location, arc, fact).")
    suspend fun applyAction(
        @LLMDescription("Action: create|update|delete")
        action: String,
        @LLMDescription("Entity: story|character|location|arc|fact")
        entity: String,
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Entity id for update/delete; optional for story updates")
        entityId: String? = null,
        @LLMDescription("Fields json object as a string, e.g. {\"name\":\"Ava\",\"description\":\"...\"}")
        fieldsJson: String = "{}",
    ): String {
        val fields = parseFields(fieldsJson)
        val actionType = action.lowercase()
        val entityType = entity.lowercase()
        return when (entityType) {
            "story" -> handleStoryAction(storyId, actionType, fields)
            "character" -> handleCharacterAction(storyId, actionType, entityId, fields)
            "location" -> handleLocationAction(storyId, actionType, entityId, fields)
            "arc" -> handleArcAction(storyId, actionType, entityId, fields)
            "fact" -> handleFactAction(storyId, actionType, entityId, fields)
            else -> "Warning: Unsupported entity '$entity'."
        }
    }

    private suspend fun handleStoryAction(
        storyId: String,
        actionType: String,
        fields: Map<String, String>,
    ): String {
        if (actionType == "delete") {
            return "Warning: Story deletion is not supported in chat mode."
        }

        val existing = repository.getStoryById(storyId)
            ?: return "Warning: Story record not found for session."

        val currentStyle = existing.styleProfile
        val updatedStyle = currentStyle?.copy(
            logline = fields["logline"] ?: currentStyle.logline,
            theme = fields["theme"] ?: currentStyle.theme,
            tone = fields["tone"] ?: currentStyle.tone,
            stakes = fields["stakes"] ?: currentStyle.stakes,
            pov = fields["pov"] ?: currentStyle.pov,
            tense = fields["tense"] ?: currentStyle.tense,
            targetAudience = fields["target_audience"] ?: currentStyle.targetAudience,
            pacing = fields["pacing"] ?: currentStyle.pacing,
        ) ?: StoryRecord.StoryStyleProfile(
            logline = fields["logline"],
            theme = fields["theme"],
            tone = fields["tone"],
            stakes = fields["stakes"],
            pov = fields["pov"],
            tense = fields["tense"],
            targetAudience = fields["target_audience"],
            pacing = fields["pacing"],
        )

        val updated = existing.copy(
            title = fields["title"] ?: existing.title,
            genre = fields["genre"] ?: existing.genre,
            setting = fields["setting"] ?: existing.setting,
            plotOutline = fields["plot_outline"] ?: existing.plotOutline,
            styleProfile = updatedStyle,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )

        repository.upsertStory(updated)
        return "Updated story metadata."
    }

    private suspend fun handleCharacterAction(
        storyId: String,
        actionType: String,
        entityId: String?,
        fields: Map<String, String>,
    ): String {
        val characters = repository.getStoryCharacters(storyId).toMutableList()
        val now = Clock.System.now().toEpochMilliseconds()

        return when (actionType) {
            "create" -> {
                val name = fields["name"]?.trim().orEmpty()
                if (name.isBlank()) {
                    return "Warning: Character name is required."
                }
                val record = StoryCharacterRecord(
                    id = UUID.randomUUID().toString(),
                    storyId = storyId,
                    name = name,
                    description = fields["description"],
                    traits = splitList(fields["traits"]),
                    roles = splitList(fields["roles"]),
                    goal = fields["goal"],
                    motivation = fields["motivation"],
                    flaw = fields["flaw"],
                    temperament = fields["temperament"],
                    age = fields["age"],
                    pronouns = fields["pronouns"],
                    occupation = fields["occupation"],
                    backstory = fields["backstory"],
                    voice = fields["voice"],
                    internalConflict = fields["internal_conflict"],
                    quirks = splitList(fields["quirks"]),
                    physical = buildPhysicalProfile(fields),
                    createdAt = now,
                )
                characters.add(record)
                repository.replaceStoryCharacters(storyId, characters)
                "Created character '${record.name}'."
            }
            "update" -> {
                val targetId = entityId ?: return "Warning: Character id is required."
                val index = characters.indexOfFirst { it.id == targetId }
                if (index == -1) {
                    return "Warning: Character with id '$targetId' not found."
                }
                val current = characters[index]
                val updated = current.copy(
                    name = fields["name"] ?: current.name,
                    description = fields["description"] ?: current.description,
                    traits = updateList(current.traits, fields["traits"]),
                    roles = updateList(current.roles, fields["roles"]),
                    goal = fields["goal"] ?: current.goal,
                    motivation = fields["motivation"] ?: current.motivation,
                    flaw = fields["flaw"] ?: current.flaw,
                    temperament = fields["temperament"] ?: current.temperament,
                    age = fields["age"] ?: current.age,
                    pronouns = fields["pronouns"] ?: current.pronouns,
                    occupation = fields["occupation"] ?: current.occupation,
                    backstory = fields["backstory"] ?: current.backstory,
                    voice = fields["voice"] ?: current.voice,
                    internalConflict = fields["internal_conflict"] ?: current.internalConflict,
                    quirks = updateList(current.quirks, fields["quirks"]),
                    physical = mergePhysical(current.physical, fields),
                )
                characters[index] = updated
                repository.replaceStoryCharacters(storyId, characters)
                "Updated character '${updated.name}'."
            }
            "delete" -> {
                val targetId = entityId ?: return "Warning: Character id is required."
                val removed = characters.removeIf { it.id == targetId }
                if (!removed) {
                    return "Warning: Character with id '$targetId' not found."
                }
                repository.replaceStoryCharacters(storyId, characters)
                "Deleted character '$targetId'."
            }
            else -> "Warning: Unsupported action '$actionType' for character."
        }
    }

    private suspend fun handleLocationAction(
        storyId: String,
        actionType: String,
        entityId: String?,
        fields: Map<String, String>,
    ): String {
        val locations = repository.getLocationsByStory(storyId).toMutableList()
        val now = Clock.System.now().toEpochMilliseconds()

        return when (actionType) {
            "create" -> {
                val name = fields["name"]?.trim().orEmpty()
                if (name.isBlank()) {
                    return "Warning: Location name is required."
                }
                val record = StoryLocationRecord(
                    id = UUID.randomUUID().toString(),
                    storyId = storyId,
                    profile = StoryLocationRecord.LocationProfile(
                        name = name,
                        description = fields["description"],
                    ),
                    tags = splitList(fields["tags"]),
                    createdAt = now,
                )
                locations.add(record)
                repository.replaceLocationsByStory(storyId, locations)
                "Created location '${record.profile.name}'."
            }
            "update" -> {
                val targetId = entityId ?: return "Warning: Location id is required."
                val index = locations.indexOfFirst { it.id == targetId }
                if (index == -1) {
                    return "Warning: Location with id '$targetId' not found."
                }
                val current = locations[index]
                val updated = current.copy(
                    profile = StoryLocationRecord.LocationProfile(
                        name = fields["name"] ?: current.profile.name,
                        description = fields["description"] ?: current.profile.description,
                    ),
                    tags = updateList(current.tags, fields["tags"]),
                )
                locations[index] = updated
                repository.replaceLocationsByStory(storyId, locations)
                "Updated location '${updated.profile.name}'."
            }
            "delete" -> {
                val targetId = entityId ?: return "Warning: Location id is required."
                val removed = locations.removeIf { it.id == targetId }
                if (!removed) {
                    return "Warning: Location with id '$targetId' not found."
                }
                repository.replaceLocationsByStory(storyId, locations)
                "Deleted location '$targetId'."
            }
            else -> "Warning: Unsupported action '$actionType' for location."
        }
    }

    private suspend fun handleArcAction(
        storyId: String,
        actionType: String,
        entityId: String?,
        fields: Map<String, String>,
    ): String {
        val arcs = repository.getArcsByStory(storyId).toMutableList()
        val now = Clock.System.now().toEpochMilliseconds()

        return when (actionType) {
            "create" -> {
                val title = fields["title"]?.trim().orEmpty()
                if (title.isBlank()) {
                    return "Warning: Arc title is required."
                }
                val record = StoryArcRecord(
                    id = UUID.randomUUID().toString(),
                    storyId = storyId,
                    scopeType = parseArcScope(fields["scope_type"]) ?: ArcScope.STORY,
                    scopeId = fields["scope_id"],
                    title = title,
                    summary = fields["summary"],
                    status = parseStatus(fields["status"]),
                    createdAt = now,
                    updatedAt = now,
                )
                arcs.add(record)
                repository.replaceArcsByStory(storyId, arcs)
                "Created arc '${record.title}'."
            }
            "update" -> {
                val targetId = entityId ?: return "Warning: Arc id is required."
                val index = arcs.indexOfFirst { it.id == targetId }
                if (index == -1) {
                    return "Warning: Arc with id '$targetId' not found."
                }
                val current = arcs[index]
                val updated = current.copy(
                    scopeType = parseArcScope(fields["scope_type"]) ?: current.scopeType,
                    scopeId = fields["scope_id"] ?: current.scopeId,
                    title = fields["title"] ?: current.title,
                    summary = fields["summary"] ?: current.summary,
                    status = parseStatus(fields["status"]) ?: current.status,
                    updatedAt = now,
                )
                arcs[index] = updated
                repository.replaceArcsByStory(storyId, arcs)
                "Updated arc '${updated.title}'."
            }
            "delete" -> {
                val targetId = entityId ?: return "Warning: Arc id is required."
                val removed = arcs.removeIf { it.id == targetId }
                if (!removed) {
                    return "Warning: Arc with id '$targetId' not found."
                }
                repository.replaceArcsByStory(storyId, arcs)
                "Deleted arc '$targetId'."
            }
            else -> "Warning: Unsupported action '$actionType' for arc."
        }
    }

    private suspend fun handleFactAction(
        storyId: String,
        actionType: String,
        entityId: String?,
        fields: Map<String, String>,
    ): String {
        val facts = repository.getFactsByStory(storyId).toMutableList()
        val now = Clock.System.now().toEpochMilliseconds()

        return when (actionType) {
            "create" -> {
                val content = fields["content"]?.trim().orEmpty()
                if (content.isBlank()) {
                    return "Warning: Fact content is required."
                }
                val record = StoryFactRecord(
                    id = UUID.randomUUID().toString(),
                    storyId = storyId,
                    factType = parseFactType(fields["fact_type"]) ?: StoryFactType.CUSTOM,
                    content = content,
                    createdAt = now,
                )
                facts.add(0, record)
                repository.replaceStoryFacts(storyId, facts)
                "Added fact '${record.factType.name}'."
            }
            "update" -> {
                val targetId = entityId ?: return "Warning: Fact id is required."
                val index = facts.indexOfFirst { it.id == targetId }
                if (index == -1) {
                    return "Warning: Fact with id '$targetId' not found."
                }
                val current = facts[index]
                val updated = current.copy(
                    factType = parseFactType(fields["fact_type"]) ?: current.factType,
                    content = fields["content"] ?: current.content,
                )
                facts[index] = updated
                repository.replaceStoryFacts(storyId, facts)
                "Updated fact '$targetId'."
            }
            "delete" -> {
                val targetId = entityId ?: return "Warning: Fact id is required."
                val removed = facts.removeIf { it.id == targetId }
                if (!removed) {
                    return "Warning: Fact with id '$targetId' not found."
                }
                repository.replaceStoryFacts(storyId, facts)
                "Deleted fact '$targetId'."
            }
            else -> "Warning: Unsupported action '$actionType' for fact."
        }
    }

    private fun splitList(raw: String?): List<String> =
        raw?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun updateList(current: List<String>, raw: String?): List<String> =
        if (raw.isNullOrBlank()) current else splitList(raw)

    private fun buildPhysicalProfile(fields: Map<String, String>): StoryCharacterRecord.PhysicalProfile? {
        val physical = StoryCharacterRecord.PhysicalProfile(
            appearance = fields["appearance"],
            height = fields["height"],
            build = fields["build"],
            hair = fields["hair"],
            eyes = fields["eyes"],
            skinTone = fields["skin_tone"],
            distinguishingMarks = fields["distinguishing_marks"],
            styleNotes = fields["style_notes"],
        )
        return if (physical == StoryCharacterRecord.PhysicalProfile()) null else physical
    }

    private fun mergePhysical(
        current: StoryCharacterRecord.PhysicalProfile?,
        fields: Map<String, String>,
    ): StoryCharacterRecord.PhysicalProfile? {
        val base = current ?: StoryCharacterRecord.PhysicalProfile()
        val merged = base.copy(
            appearance = fields["appearance"] ?: base.appearance,
            height = fields["height"] ?: base.height,
            build = fields["build"] ?: base.build,
            hair = fields["hair"] ?: base.hair,
            eyes = fields["eyes"] ?: base.eyes,
            skinTone = fields["skin_tone"] ?: base.skinTone,
            distinguishingMarks = fields["distinguishing_marks"] ?: base.distinguishingMarks,
            styleNotes = fields["style_notes"] ?: base.styleNotes,
        )
        return if (merged == StoryCharacterRecord.PhysicalProfile()) null else merged
    }

    private fun parseStatus(raw: String?): ContentStatus? =
        raw?.trim()?.uppercase()?.let { value ->
            ContentStatus.entries.firstOrNull { it.name == value }
        }

    private fun parseArcScope(raw: String?): ArcScope? =
        raw?.trim()?.uppercase()?.let { value ->
            ArcScope.entries.firstOrNull { it.name == value }
        }

    private fun parseFactType(raw: String?): StoryFactType? =
        raw?.trim()?.uppercase()?.let { value ->
            StoryFactType.entries.firstOrNull { it.name == value }
        }

    private fun parseFields(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val element = json.parseToJsonElement(raw)
            val obj = when (element) {
                is JsonObject -> element
                else -> {
                    val inner = element.jsonPrimitive.contentOrNull ?: return emptyMap()
                    val parsed = json.parseToJsonElement(inner)
                    parsed as? JsonObject ?: return emptyMap()
                }
            }
            obj.entries
                .mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
