@file:Suppress("unused")

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
import com.ead.dispatch.sample.data.db.type.StoryFactType
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CharacterPhysicalProfileRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateArcRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateCharacterRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateFactRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateLocationRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.StoryStyleProfileRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.StoryUpsertRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.ToolError
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateArcRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateCharacterRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateFactRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateLocationRequest
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import kotlinx.datetime.Clock
import java.util.UUID

class ChatCrudTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {
    @Tool
    @LLMDescription("Upsert story metadata (title, genre, setting, plot outline, style).")
    suspend fun upsertStory(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Story metadata to upsert")
        request: StoryUpsertRequest = StoryUpsertRequest(),
    ): ToolResult<OperationOutcome> {
        return onStoryUpdate(storyId, request)
    }

    @Tool
    @LLMDescription("Get the full chat context (story, characters, locations, arcs, facts).")
    suspend fun getChatContext(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<StoryChatContext>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val context = repository.getChatContext(storyId)
        return querySuccess(
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = context.story?.id,
            summary = "Loaded full chat context.",
            payload = context,
        )
    }

    @Tool
    @LLMDescription("Get story metadata for the current session.")
    suspend fun getStory(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<StoryRecord>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        return querySuccess(
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded story metadata.",
            payload = story,
        )
    }

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
    @LLMDescription("List all locations in the story.")
    suspend fun listLocations(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryLocationRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val locations = repository.getLocationsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.LOCATION,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${locations.size} location(s).",
            payload = locations,
        )
    }

    @Tool
    @LLMDescription("Get a location by id.")
    suspend fun getLocation(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryLocationRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val location = repository.getLocationsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Location with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.LOCATION,
            storyId = storyId,
            entityId = location.id,
            summary = "Loaded location '${location.profile.name}'.",
            payload = location,
        )
    }

    @Tool
    @LLMDescription("Search locations by name and/or tag. Returns a shortlist for disambiguation.")
    suspend fun searchLocations(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Case-insensitive substring match for location name")
        nameContains: String? = null,
        @LLMDescription("Filter by a tag")
        tag: String? = null,
        @LLMDescription("Max results to return")
        limit: Int = 5,
    ): ToolResult<QueryOutcome<List<StoryLocationRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val normalizedName = nameContains?.trim()?.lowercase()
        val normalizedTag = tag?.trim()?.lowercase()
        val matches = repository.getLocationsByStory(storyId)
            .asSequence()
            .filter { location ->
                val nameMatch = normalizedName?.let {
                    location.profile.name.lowercase().contains(it)
                } ?: true
                val tagMatch = normalizedTag?.let { tagFilter ->
                    location.tags.any { it.lowercase() == tagFilter }
                } ?: true
                nameMatch && tagMatch
            }
            .take(limit.coerceAtLeast(1))
            .toList()
        return querySuccess(
            entity = OperationEntity.LOCATION,
            storyId = storyId,
            entityId = story.id,
            summary = "Found ${matches.size} matching location(s).",
            payload = matches,
        )
    }

    @Tool
    @LLMDescription("List all arcs in the story.")
    suspend fun listArcs(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryArcRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val arcs = repository.getArcsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.ARC,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${arcs.size} arc(s).",
            payload = arcs,
        )
    }

    @Tool
    @LLMDescription("Get an arc by id.")
    suspend fun getArc(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Arc id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryArcRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val arc = repository.getArcsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Arc with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.ARC,
            storyId = storyId,
            entityId = arc.id,
            summary = "Loaded arc '${arc.title}'.",
            payload = arc,
        )
    }

    @Tool
    @LLMDescription("List all facts in the story.")
    suspend fun listFacts(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryFactRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val facts = repository.getFactsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.FACT,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${facts.size} fact(s).",
            payload = facts,
        )
    }

    @Tool
    @LLMDescription("Get a fact by id.")
    suspend fun getFact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Fact id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryFactRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val fact = repository.getFactsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Fact with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.FACT,
            storyId = storyId,
            entityId = fact.id,
            summary = "Loaded fact '${fact.factType.name}'.",
            payload = fact,
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
        return onCharacterCreate(storyId, request)
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
        return onCharacterUpdate(storyId, entityId, request)
    }

    @Tool
    @LLMDescription("Delete a character from the story.")
    suspend fun deleteCharacter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Character id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> =
        onCharacterDelete(storyId, entityId)

    @Tool
    @LLMDescription("Create a location in the story.")
    suspend fun createLocation(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location fields to create")
        request: CreateLocationRequest,
    ): ToolResult<OperationOutcome> {
        return onLocationCreate(storyId, request)
    }

    @Tool
    @LLMDescription("Update a location in the story.")
    suspend fun updateLocation(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location id to update")
        entityId: String,
        @LLMDescription("Location fields to update")
        request: UpdateLocationRequest = UpdateLocationRequest(),
    ): ToolResult<OperationOutcome> {
        return onLocationUpdate(storyId, entityId, request)
    }

    @Tool
    @LLMDescription("Delete a location from the story.")
    suspend fun deleteLocation(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> =
        onLocationDelete(storyId, entityId)

    @Tool
    @LLMDescription("Create an arc in the story.")
    suspend fun createArc(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Arc fields to create")
        request: CreateArcRequest,
    ): ToolResult<OperationOutcome> {
        return onArcCreate(storyId, request)
    }

    @Tool
    @LLMDescription("Update an arc in the story.")
    suspend fun updateArc(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Arc id to update")
        entityId: String,
        @LLMDescription("Arc fields to update")
        request: UpdateArcRequest = UpdateArcRequest(),
    ): ToolResult<OperationOutcome> {
        return onArcUpdate(storyId, entityId, request)
    }

    @Tool
    @LLMDescription("Delete an arc from the story.")
    suspend fun deleteArc(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Arc id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> =
        onArcDelete(storyId, entityId)

    @Tool
    @LLMDescription("Create a fact in the story.")
    suspend fun createFact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Fact fields to create")
        request: CreateFactRequest,
    ): ToolResult<OperationOutcome> {
        return onFactCreate(storyId, request)
    }

    @Tool
    @LLMDescription("Update a fact in the story.")
    suspend fun updateFact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Fact id to update")
        entityId: String,
        @LLMDescription("Fact fields to update")
        request: UpdateFactRequest = UpdateFactRequest(),
    ): ToolResult<OperationOutcome> {
        return onFactUpdate(storyId, entityId, request)
    }

    @Tool
    @LLMDescription("Delete a fact from the story.")
    suspend fun deleteFact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Fact id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> =
        onFactDelete(storyId, entityId)

    private suspend fun onStoryUpdate(
        storyId: String,
        request: StoryUpsertRequest,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")

        val updated = existing.copy(
            title = request.title ?: existing.title,
            genre = request.genre ?: existing.genre,
            setting = request.setting ?: existing.setting,
            plotOutline = request.plotOutline ?: existing.plotOutline,
            status = request.status ?: existing.status,
            styleProfile = mergeStyleProfile(existing.styleProfile, request.styleProfile),
            styleRefs = request.styleRefs ?: existing.styleRefs,
            emotionalBeats = request.emotionalBeats ?: existing.emotionalBeats,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )

        repository.upsertStory(updated)

        return success(
            action = "update",
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = null,
            summary = "Updated story metadata.",
        )
    }

    private suspend fun onCharacterCreate(
        storyId: String,
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

    private suspend fun onCharacterUpdate(
        storyId: String,
        entityId: String,
        request: UpdateCharacterRequest,
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

    private suspend fun onCharacterDelete(
        storyId: String,
        entityId: String,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getStoryCharacters(storyId).firstOrNull { it.id == entityId }
        if (existing == null) {
            return failure("NOT_FOUND", "Character with id '$entityId' not found.")
        }

        repository.deleteStoryCharacter(entityId)

        return success(
            action = "delete",
            entity = OperationEntity.CHARACTER,
            storyId = storyId,
            entityId = entityId,
            summary = "Deleted character '$entityId'.",
        )
    }

    private suspend fun onLocationCreate(
        storyId: String,
        request: CreateLocationRequest,
    ): ToolResult<OperationOutcome> {
        val now = Clock.System.now().toEpochMilliseconds()
        val name = request.name.trim()

        if (name.isBlank()) {
            return failure("MISSING_FIELD", "Location name is required.")
        }

        val record = StoryLocationRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            profile = StoryLocationRecord.LocationProfile(
                name = name,
                description = request.description,
            ),
            tags = request.tags ?: emptyList(),
            createdAt = now,
        )
        repository.upsertLocation(record)

        return success(
            action = "create",
            entity = OperationEntity.LOCATION,
            storyId = storyId,
            entityId = record.id,
            summary = "Created location '${record.profile.name}'.",
        )
    }

    private suspend fun onLocationUpdate(
        storyId: String,
        entityId: String,
        request: UpdateLocationRequest,
    ): ToolResult<OperationOutcome> {
        val current = repository.getLocationsByStory(storyId).firstOrNull { it.id == entityId }
        if (current == null) {
            return failure("NOT_FOUND", "Location with id '$entityId' not found.")
        }

        if (request.name != null && request.name.isBlank()) {
            return failure("MISSING_FIELD", "Location name cannot be blank.")
        }

        val updated = current.copy(
            profile = StoryLocationRecord.LocationProfile(
                name = request.name ?: current.profile.name,
                description = request.description ?: current.profile.description,
            ),
            tags = request.tags ?: current.tags,
        )

        repository.upsertLocation(updated)

        return success(
            action = "update",
            entity = OperationEntity.LOCATION,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated location '${updated.profile.name}'.",
        )
    }

    private suspend fun onLocationDelete(
        storyId: String,
        entityId: String,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getLocationsByStory(storyId).firstOrNull { it.id == entityId }
        if (existing == null) {
            return failure("NOT_FOUND", "Location with id '$entityId' not found.")
        }

        repository.deleteStoryLocation(entityId)

        return success(
            action = "delete",
            entity = OperationEntity.LOCATION,
            storyId = storyId,
            entityId = entityId,
            summary = "Deleted location '$entityId'.",
        )
    }

    private suspend fun onArcCreate(
        storyId: String,
        request: CreateArcRequest,
    ): ToolResult<OperationOutcome> {
        val now = Clock.System.now().toEpochMilliseconds()
        val title = request.title.trim()

        if (title.isBlank()) {
            return failure("MISSING_FIELD", "Arc title is required.")
        }

        val record = StoryArcRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            scopeType = request.scopeType ?: ArcScope.STORY,
            scopeId = request.scopeId,
            title = title,
            summary = request.summary,
            status = request.status,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertArc(record)

        return success(
            action = "create",
            entity = OperationEntity.ARC,
            storyId = storyId,
            entityId = record.id,
            summary = "Created arc '${record.title}'.",
        )
    }

    private suspend fun onArcUpdate(
        storyId: String,
        entityId: String,
        request: UpdateArcRequest,
    ): ToolResult<OperationOutcome> {
        val arcs = repository.getArcsByStory(storyId)
        val now = Clock.System.now().toEpochMilliseconds()
        val current = arcs.firstOrNull { it.id == entityId }
        if (current == null) {
            return failure("NOT_FOUND", "Arc with id '$entityId' not found.")
        }
        val updated = current.copy(
            scopeType = request.scopeType ?: current.scopeType,
            scopeId = request.scopeId ?: current.scopeId,
            title = request.title ?: current.title,
            summary = request.summary ?: current.summary,
            status = request.status ?: current.status,
            updatedAt = now,
        )
        repository.upsertArc(updated)
        return success(
            action = "update",
            entity = OperationEntity.ARC,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated arc '${updated.title}'.",
        )
    }

    private suspend fun onArcDelete(
        storyId: String,
        entityId: String,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getArcsByStory(storyId).firstOrNull { it.id == entityId }
        if (existing == null) {
            return failure("NOT_FOUND", "Arc with id '$entityId' not found.")
        }

        repository.deleteStoryArc(entityId)

        return success(
            action = "delete",
            entity = OperationEntity.ARC,
            storyId = storyId,
            entityId = entityId,
            summary = "Deleted arc '$entityId'.",
        )
    }

    private suspend fun onFactCreate(
        storyId: String,
        request: CreateFactRequest,
    ): ToolResult<OperationOutcome> {
        val now = Clock.System.now().toEpochMilliseconds()
        val content = request.content.trim()

        if (content.isBlank()) {
            return failure("MISSING_FIELD", "Fact content is required.")
        }

        val record = StoryFactRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            factType = request.factType ?: StoryFactType.CUSTOM,
            content = content,
            createdAt = now,
        )

        repository.insertStoryFact(record)

        return success(
            action = "create",
            entity = OperationEntity.FACT,
            storyId = storyId,
            entityId = record.id,
            summary = "Added fact '${record.factType.name}'.",
        )
    }

    private suspend fun onFactUpdate(
        storyId: String,
        entityId: String,
        request: UpdateFactRequest,
    ): ToolResult<OperationOutcome> {
        val current = repository.getFactsByStory(storyId).firstOrNull { it.id == entityId }
        if (current == null) {
            return failure("NOT_FOUND", "Fact with id '$entityId' not found.")
        }

        if (request.content != null && request.content.isBlank()) {
            return failure("MISSING_FIELD", "Fact content cannot be blank.")
        }

        val updated = current.copy(
            factType = request.factType ?: current.factType,
            content = request.content ?: current.content,
        )

        repository.updateStoryFact(updated)

        return success(
            action = "update",
            entity = OperationEntity.FACT,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated fact '$entityId'.",
        )
    }

    private suspend fun onFactDelete(
        storyId: String,
        entityId: String,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getFactsByStory(storyId).firstOrNull { it.id == entityId }
        if (existing == null) {
            return failure("NOT_FOUND", "Fact with id '$entityId' not found.")
        }
        repository.deleteStoryFact(entityId)
        return success(
            action = "delete",
            entity = OperationEntity.FACT,
            storyId = storyId,
            entityId = entityId,
            summary = "Deleted fact '$entityId'.",
        )
    }

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
    private fun mergeStyleProfile(
        current: StoryRecord.StoryStyleProfile?,
        request: StoryStyleProfileRequest?,
    ): StoryRecord.StoryStyleProfile? {
        val base = current ?: StoryRecord.StoryStyleProfile()
        val merged = request?.let {
            base.copy(
                logline = it.logline ?: base.logline,
                theme = it.theme ?: base.theme,
                tone = it.tone ?: base.tone,
                stakes = it.stakes ?: base.stakes,
                pov = it.pov ?: base.pov,
                tense = it.tense ?: base.tense,
                targetAudience = it.targetAudience ?: base.targetAudience,
                pacing = it.pacing ?: base.pacing,
            )
        } ?: base
        return if (merged == StoryRecord.StoryStyleProfile()) null else merged
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

    private fun <T> querySuccess(
        entity: OperationEntity,
        storyId: String,
        entityId: String?,
        summary: String,
        payload: T,
        warnings: List<String> = emptyList(),
    ): ToolResult<QueryOutcome<T>> = ToolResult.Success(
        data = QueryOutcome(
            entity = entity,
            storyId = storyId,
            entityId = entityId,
            summary = summary,
            payload = payload,
        ),
        message = summary,
        warnings = warnings,
    )

    private fun success(
        action: String,
        entity: OperationEntity,
        storyId: String,
        entityId: String?,
        summary: String,
        warnings: List<String> = emptyList(),
    ): ToolResult<OperationOutcome> = ToolResult.Success(
        data = OperationOutcome(
            action = action,
            entity = entity,
            storyId = storyId,
            entityId = entityId,
            summary = summary,
        ),
        message = summary,
        warnings = warnings,
    )

    private fun <T> failure(
        code: String,
        message: String,
        details: String? = null,
    ): ToolResult<T> = ToolResult.Failure(
        error = ToolError(code = code, details = details),
        message = message,
    )
}
