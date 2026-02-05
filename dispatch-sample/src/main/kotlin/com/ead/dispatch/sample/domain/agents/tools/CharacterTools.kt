@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryRelationshipRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.*
import kotlinx.datetime.Clock
import java.util.*

class CharacterTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

    @Tool
    @LLMDescription("List all characters in the story.")
    suspend fun listCharacters(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryCharacterRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val characters = repository.getStoryCharacters(storyId)
        return querySuccess(
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${characters.size} character(s).",
            payload = characters,
        )
    }

    @Tool
    @LLMDescription("Get a character by id.")
    suspend fun getCharacter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Character id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryCharacterRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val character = repository.getStoryCharacters(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Character with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = character.id,
            summary = "Loaded character '${character.name}'.",
            payload = character,
        )
    }

    @Tool
    @LLMDescription("Search characters by name and/or role. Returns a shortlist for disambiguation.")
    suspend fun searchCharacters(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Case-insensitive substring match for character name")
        nameContains: String? = null,
        @LLMDescription("Filter by a role label")
        role: String? = null,
        @LLMDescription("Max results to return")
        limit: Int = 5,
    ): ToolResult<QueryOutcome<List<StoryCharacterRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val normalizedName = nameContains?.trim()?.lowercase()
        val normalizedRole = role?.trim()?.lowercase()
        val matches = repository.getStoryCharacters(storyId)
            .asSequence()
            .filter { character ->
                val nameMatch = normalizedName?.let { character.name.lowercase().contains(it) } ?: true
                val roleMatch = normalizedRole?.let { roleFilter ->
                    character.roles.any { it.lowercase() == roleFilter }
                } ?: true
                nameMatch && roleMatch
            }
            .take(limit.coerceAtLeast(1))
            .toList()
        return querySuccess(
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = story.id,
            summary = "Found ${matches.size} matching character(s).",
            payload = matches,
        )
    }

    @Tool
    @LLMDescription("Create a character in the story.")
    suspend fun createCharacter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Character fields to create")
        request: CreateCharacterRequest,
    ): ToolResult<OperationOutcome> {
        val now = Clock.System.now().toEpochMilliseconds()
        val name = request.name.trim()
        if (name.isBlank()) {
            return failure("MISSING_FIELD", "Character name is required.")
        }

        val record = StoryCharacterRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            name = name,
            description = request.description,
            traits = request.traits ?: emptyList(),
            roles = request.roles ?: emptyList(),
            goal = request.goal,
            motivation = request.motivation,
            flaw = request.flaw,
            temperament = request.temperament,
            age = request.age,
            pronouns = request.pronouns,
            occupation = request.occupation,
            backstory = request.backstory,
            voice = request.voice,
            internalConflict = request.internalConflict,
            quirks = request.quirks ?: emptyList(),
            physical = request.physical.toProfileOrNull(),
            createdAt = now,
        )
        repository.insertStoryCharacter(record)

        return success(
            action = "create",
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = record.id,
            summary = "Created character '${record.name}'.",
        )
    }

    @Tool
    @LLMDescription("Update a character in the story.")
    suspend fun updateCharacter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Character id to update")
        entityId: String,
        @LLMDescription("Character fields to update")
        request: UpdateCharacterRequest = UpdateCharacterRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getStoryCharacters(storyId).firstOrNull { it.id == entityId }
        if (current == null) {
            return failure("NOT_FOUND", "Character with id '$entityId' not found.")
        }

        if (request.name != null && request.name.isBlank()) {
            return failure("MISSING_FIELD", "Character name cannot be blank.")
        }

        val updated = current.copy(
            name = request.name ?: current.name,
            description = request.description ?: current.description,
            traits = request.traits ?: current.traits,
            roles = request.roles ?: current.roles,
            goal = request.goal ?: current.goal,
            motivation = request.motivation ?: current.motivation,
            flaw = request.flaw ?: current.flaw,
            temperament = request.temperament ?: current.temperament,
            age = request.age ?: current.age,
            pronouns = request.pronouns ?: current.pronouns,
            occupation = request.occupation ?: current.occupation,
            backstory = request.backstory ?: current.backstory,
            voice = request.voice ?: current.voice,
            internalConflict = request.internalConflict ?: current.internalConflict,
            quirks = request.quirks ?: current.quirks,
            physical = mergePhysical(current.physical, request.physical),
        )

        repository.updateStoryCharacter(updated)
        return success(
            action = "update",
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated character '${updated.name}'.",
        )
    }

    @Tool
    @LLMDescription("Delete a character from the story.")
    suspend fun deleteCharacter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Character id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.getStoryCharacters(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Character with id '$entityId' not found.")

        repository.deleteStoryCharacter(entityId)

        return success(
            action = "delete",
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = entityId,
            summary = "Deleted character '$entityId'.",
        )
    }

    @Tool
    @LLMDescription("List all relationships in the story.")
    suspend fun listRelationships(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryRelationshipRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val relationships = repository.getRelationshipsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.RELATIONSHIP,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${relationships.size} relationship(s).",
            payload = relationships,
        )
    }

    @Tool
    @LLMDescription("Get a relationship by id.")
    suspend fun getRelationship(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Relationship id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryRelationshipRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val rel = repository.getRelationshipsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Relationship with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.RELATIONSHIP,
            storyId = storyId,
            entityId = rel.id,
            summary = "Loaded relationship '${rel.relation}'.",
            payload = rel,
        )
    }

    @Tool
    @LLMDescription("Create a relationship in the story.")
    suspend fun createRelationship(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Relationship fields to create")
        request: CreateRelationshipRequest,
    ): ToolResult<OperationOutcome> {
        val relation = request.relation.trim()
        if (relation.isBlank()) return failure("MISSING_FIELD", "Relationship label is required.")
        val record = StoryRelationshipRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            subjectId = request.subjectId,
            subjectType = request.subjectType,
            objectId = request.objectId,
            objectType = request.objectType,
            relation = relation,
            notes = request.notes,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryRelationship(record)
        return success("create", OperationEntity.RELATIONSHIP, storyId, record.id, "Added relationship '${record.relation}'.")
    }

    @Tool
    @LLMDescription("Update a relationship in the story.")
    suspend fun updateRelationship(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Relationship id to update")
        entityId: String,
        @LLMDescription("Relationship fields to update")
        request: UpdateRelationshipRequest = UpdateRelationshipRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getRelationshipsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Relationship with id '$entityId' not found.")
        val updated = current.copy(
            subjectId = request.subjectId ?: current.subjectId,
            subjectType = request.subjectType ?: current.subjectType,
            objectId = request.objectId ?: current.objectId,
            objectType = request.objectType ?: current.objectType,
            relation = request.relation ?: current.relation,
            notes = request.notes ?: current.notes,
        )
        repository.updateStoryRelationship(updated)
        return success("update", OperationEntity.RELATIONSHIP, storyId, updated.id, "Updated relationship '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete a relationship from the story.")
    suspend fun deleteRelationship(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Relationship id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryRelationship(entityId)
        return success("delete", OperationEntity.RELATIONSHIP, storyId, entityId, "Deleted relationship '$entityId'.")
    }

    // --- Helpers ---

    private fun mergePhysical(
        current: StoryCharacterRecord.PhysicalProfile?,
        request: CharacterPhysicalProfileRequest?,
    ): StoryCharacterRecord.PhysicalProfile? {
        val base = current ?: StoryCharacterRecord.PhysicalProfile()
        val merged = request?.let {
            base.copy(
                appearance = it.appearance ?: base.appearance,
                height = it.height ?: base.height,
                build = it.build ?: base.build,
                hair = it.hair ?: base.hair,
                eyes = it.eyes ?: base.eyes,
                skinTone = it.skinTone ?: base.skinTone,
                distinguishingMarks = it.distinguishingMarks ?: base.distinguishingMarks,
                styleNotes = it.styleNotes ?: base.styleNotes,
            )
        } ?: base
        return if (merged == StoryCharacterRecord.PhysicalProfile()) null else merged
    }

    private fun CharacterPhysicalProfileRequest?.toProfileOrNull(): StoryCharacterRecord.PhysicalProfile? {
        if (this == null) return null
        val profile = StoryCharacterRecord.PhysicalProfile(
            appearance = appearance,
            height = height,
            build = build,
            hair = hair,
            eyes = eyes,
            skinTone = skinTone,
            distinguishingMarks = distinguishingMarks,
            styleNotes = styleNotes,
        )
        return if (profile == StoryCharacterRecord.PhysicalProfile()) null else profile
    }
}
