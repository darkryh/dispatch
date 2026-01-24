@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.chat_agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryArtifactRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryCultureRecord
import com.ead.dispatch.sample.data.db.entities.StoryEventRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationFeatureRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StoryOrganizationRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.entities.StoryRelationshipRecord
import com.ead.dispatch.sample.data.db.entities.StoryTimelineEntryRecord
import com.ead.dispatch.sample.data.db.entities.StoryWorldRuleRecord
import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CharacterPhysicalProfileRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateArcRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateArtifactRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateCharacterRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateCultureRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateEventRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateLocationFeatureRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateLocationRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateOrganizationRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateRelationshipRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateTimelineEntryRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.CreateWorldRuleRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.StoryStyleProfileRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.StoryUpsertRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.ToolError
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateArcRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateArtifactRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateCharacterRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateCultureRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateEventRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateLocationFeatureRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateLocationRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateOrganizationRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateRelationshipRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateTimelineEntryRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.model.UpdateWorldRuleRequest
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
    @LLMDescription("Get the full chat context (story, characters, locations, arcs, world data).")
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
    @LLMDescription("List all world rules in the story.")
    suspend fun listWorldRules(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryWorldRuleRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val rules = repository.getWorldRulesByStory(storyId)
        return querySuccess(
            entity = OperationEntity.WORLD_RULE,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${rules.size} world rule(s).",
            payload = rules,
        )
    }

    @Tool
    @LLMDescription("Get a world rule by id.")
    suspend fun getWorldRule(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("World rule id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryWorldRuleRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val rule = repository.getWorldRulesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "World rule with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.WORLD_RULE,
            storyId = storyId,
            entityId = rule.id,
            summary = "Loaded world rule '${rule.title}'.",
            payload = rule,
        )
    }

    @Tool
    @LLMDescription("List all cultures in the story.")
    suspend fun listCultures(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryCultureRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val cultures = repository.getCulturesByStory(storyId)
        return querySuccess(
            entity = OperationEntity.CULTURE,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${cultures.size} culture(s).",
            payload = cultures,
        )
    }

    @Tool
    @LLMDescription("Get a culture by id.")
    suspend fun getCulture(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Culture id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryCultureRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val culture = repository.getCulturesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Culture with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.CULTURE,
            storyId = storyId,
            entityId = culture.id,
            summary = "Loaded culture '${culture.name}'.",
            payload = culture,
        )
    }

    @Tool
    @LLMDescription("List all events in the story.")
    suspend fun listEvents(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryEventRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val events = repository.getEventsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.EVENT,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${events.size} event(s).",
            payload = events,
        )
    }

    @Tool
    @LLMDescription("Get an event by id.")
    suspend fun getEvent(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Event id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryEventRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val event = repository.getEventsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Event with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.EVENT,
            storyId = storyId,
            entityId = event.id,
            summary = "Loaded event '${event.name}'.",
            payload = event,
        )
    }

    @Tool
    @LLMDescription("List all organizations in the story.")
    suspend fun listOrganizations(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryOrganizationRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val orgs = repository.getOrganizationsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.ORGANIZATION,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${orgs.size} organization(s).",
            payload = orgs,
        )
    }

    @Tool
    @LLMDescription("Get an organization by id.")
    suspend fun getOrganization(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Organization id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryOrganizationRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val org = repository.getOrganizationsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Organization with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.ORGANIZATION,
            storyId = storyId,
            entityId = org.id,
            summary = "Loaded organization '${org.name}'.",
            payload = org,
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
    @LLMDescription("List all location features in the story.")
    suspend fun listLocationFeatures(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryLocationFeatureRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val features = repository.getLocationFeaturesByStory(storyId)
        return querySuccess(
            entity = OperationEntity.LOCATION_FEATURE,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${features.size} location feature(s).",
            payload = features,
        )
    }

    @Tool
    @LLMDescription("Get a location feature by id.")
    suspend fun getLocationFeature(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location feature id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryLocationFeatureRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val feature = repository.getLocationFeaturesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Location feature with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.LOCATION_FEATURE,
            storyId = storyId,
            entityId = feature.id,
            summary = "Loaded location feature '${feature.name}'.",
            payload = feature,
        )
    }

    @Tool
    @LLMDescription("List all artifacts in the story.")
    suspend fun listArtifacts(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryArtifactRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val artifacts = repository.getArtifactsByStory(storyId)
        return querySuccess(
            entity = OperationEntity.ARTIFACT,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${artifacts.size} artifact(s).",
            payload = artifacts,
        )
    }

    @Tool
    @LLMDescription("Get an artifact by id.")
    suspend fun getArtifact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Artifact id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryArtifactRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val artifact = repository.getArtifactsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Artifact with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.ARTIFACT,
            storyId = storyId,
            entityId = artifact.id,
            summary = "Loaded artifact '${artifact.name}'.",
            payload = artifact,
        )
    }

    @Tool
    @LLMDescription("List all timeline entries in the story.")
    suspend fun listTimelineEntries(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryTimelineEntryRecord>>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val entries = repository.getTimelineEntriesByStory(storyId)
        return querySuccess(
            entity = OperationEntity.TIMELINE_ENTRY,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded ${entries.size} timeline entr(y/ies).",
            payload = entries,
        )
    }

    @Tool
    @LLMDescription("Get a timeline entry by id.")
    suspend fun getTimelineEntry(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Timeline entry id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryTimelineEntryRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val entry = repository.getTimelineEntriesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Timeline entry with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.TIMELINE_ENTRY,
            storyId = storyId,
            entityId = entry.id,
            summary = "Loaded timeline entry '${entry.title}'.",
            payload = entry,
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
    @LLMDescription("Create a world rule in the story.")
    suspend fun createWorldRule(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("World rule fields to create")
        request: CreateWorldRuleRequest,
    ): ToolResult<OperationOutcome> {
        val title = request.title.trim()
        if (title.isBlank()) return failure("MISSING_FIELD", "World rule title is required.")
        val record = StoryWorldRuleRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            title = title,
            description = request.description,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryWorldRule(record)
        return success("create", OperationEntity.WORLD_RULE, storyId, record.id, "Added world rule '${record.title}'.")
    }

    @Tool
    @LLMDescription("Update a world rule in the story.")
    suspend fun updateWorldRule(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("World rule id to update")
        entityId: String,
        @LLMDescription("World rule fields to update")
        request: UpdateWorldRuleRequest = UpdateWorldRuleRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getWorldRulesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "World rule with id '$entityId' not found.")
        val updated = current.copy(
            title = request.title ?: current.title,
            description = request.description ?: current.description,
        )
        repository.updateStoryWorldRule(updated)
        return success("update", OperationEntity.WORLD_RULE, storyId, updated.id, "Updated world rule '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete a world rule from the story.")
    suspend fun deleteWorldRule(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("World rule id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryWorldRule(entityId)
        return success("delete", OperationEntity.WORLD_RULE, storyId, entityId, "Deleted world rule '$entityId'.")
    }

    @Tool
    @LLMDescription("Create a culture in the story.")
    suspend fun createCulture(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Culture fields to create")
        request: CreateCultureRequest,
    ): ToolResult<OperationOutcome> {
        val name = request.name.trim()
        if (name.isBlank()) return failure("MISSING_FIELD", "Culture name is required.")
        val record = StoryCultureRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            name = name,
            description = request.description,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryCulture(record)
        return success("create", OperationEntity.CULTURE, storyId, record.id, "Added culture '${record.name}'.")
    }

    @Tool
    @LLMDescription("Update a culture in the story.")
    suspend fun updateCulture(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Culture id to update")
        entityId: String,
        @LLMDescription("Culture fields to update")
        request: UpdateCultureRequest = UpdateCultureRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getCulturesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Culture with id '$entityId' not found.")
        val updated = current.copy(
            name = request.name ?: current.name,
            description = request.description ?: current.description,
        )
        repository.updateStoryCulture(updated)
        return success("update", OperationEntity.CULTURE, storyId, updated.id, "Updated culture '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete a culture from the story.")
    suspend fun deleteCulture(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Culture id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryCulture(entityId)
        return success("delete", OperationEntity.CULTURE, storyId, entityId, "Deleted culture '$entityId'.")
    }

    @Tool
    @LLMDescription("Create an event in the story.")
    suspend fun createEvent(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Event fields to create")
        request: CreateEventRequest,
    ): ToolResult<OperationOutcome> {
        val name = request.name.trim()
        if (name.isBlank()) return failure("MISSING_FIELD", "Event name is required.")
        val record = StoryEventRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            name = name,
            description = request.description,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryEvent(record)
        return success("create", OperationEntity.EVENT, storyId, record.id, "Added event '${record.name}'.")
    }

    @Tool
    @LLMDescription("Update an event in the story.")
    suspend fun updateEvent(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Event id to update")
        entityId: String,
        @LLMDescription("Event fields to update")
        request: UpdateEventRequest = UpdateEventRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getEventsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Event with id '$entityId' not found.")
        val updated = current.copy(
            name = request.name ?: current.name,
            description = request.description ?: current.description,
        )
        repository.updateStoryEvent(updated)
        return success("update", OperationEntity.EVENT, storyId, updated.id, "Updated event '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete an event from the story.")
    suspend fun deleteEvent(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Event id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryEvent(entityId)
        return success("delete", OperationEntity.EVENT, storyId, entityId, "Deleted event '$entityId'.")
    }

    @Tool
    @LLMDescription("Create an organization in the story.")
    suspend fun createOrganization(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Organization fields to create")
        request: CreateOrganizationRequest,
    ): ToolResult<OperationOutcome> {
        val name = request.name.trim()
        if (name.isBlank()) return failure("MISSING_FIELD", "Organization name is required.")
        val record = StoryOrganizationRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            name = name,
            description = request.description,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryOrganization(record)
        return success("create", OperationEntity.ORGANIZATION, storyId, record.id, "Added organization '${record.name}'.")
    }

    @Tool
    @LLMDescription("Update an organization in the story.")
    suspend fun updateOrganization(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Organization id to update")
        entityId: String,
        @LLMDescription("Organization fields to update")
        request: UpdateOrganizationRequest = UpdateOrganizationRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getOrganizationsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Organization with id '$entityId' not found.")
        val updated = current.copy(
            name = request.name ?: current.name,
            description = request.description ?: current.description,
        )
        repository.updateStoryOrganization(updated)
        return success("update", OperationEntity.ORGANIZATION, storyId, updated.id, "Updated organization '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete an organization from the story.")
    suspend fun deleteOrganization(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Organization id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryOrganization(entityId)
        return success("delete", OperationEntity.ORGANIZATION, storyId, entityId, "Deleted organization '$entityId'.")
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

    @Tool
    @LLMDescription("Create a location feature in the story.")
    suspend fun createLocationFeature(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location feature fields to create")
        request: CreateLocationFeatureRequest,
    ): ToolResult<OperationOutcome> {
        val name = request.name.trim()
        if (name.isBlank()) return failure("MISSING_FIELD", "Location feature name is required.")
        val record = StoryLocationFeatureRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            locationId = request.locationId,
            name = name,
            description = request.description,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryLocationFeature(record)
        return success("create", OperationEntity.LOCATION_FEATURE, storyId, record.id, "Added location feature '${record.name}'.")
    }

    @Tool
    @LLMDescription("Update a location feature in the story.")
    suspend fun updateLocationFeature(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location feature id to update")
        entityId: String,
        @LLMDescription("Location feature fields to update")
        request: UpdateLocationFeatureRequest = UpdateLocationFeatureRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getLocationFeaturesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Location feature with id '$entityId' not found.")
        val updated = current.copy(
            locationId = request.locationId ?: current.locationId,
            name = request.name ?: current.name,
            description = request.description ?: current.description,
        )
        repository.updateStoryLocationFeature(updated)
        return success("update", OperationEntity.LOCATION_FEATURE, storyId, updated.id, "Updated location feature '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete a location feature from the story.")
    suspend fun deleteLocationFeature(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location feature id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryLocationFeature(entityId)
        return success("delete", OperationEntity.LOCATION_FEATURE, storyId, entityId, "Deleted location feature '$entityId'.")
    }

    @Tool
    @LLMDescription("Create an artifact in the story.")
    suspend fun createArtifact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Artifact fields to create")
        request: CreateArtifactRequest,
    ): ToolResult<OperationOutcome> {
        val name = request.name.trim()
        if (name.isBlank()) return failure("MISSING_FIELD", "Artifact name is required.")
        val record = StoryArtifactRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            name = name,
            description = request.description,
            ownerId = request.ownerId,
            ownerType = request.ownerType,
            locationId = request.locationId,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryArtifact(record)
        return success("create", OperationEntity.ARTIFACT, storyId, record.id, "Added artifact '${record.name}'.")
    }

    @Tool
    @LLMDescription("Update an artifact in the story.")
    suspend fun updateArtifact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Artifact id to update")
        entityId: String,
        @LLMDescription("Artifact fields to update")
        request: UpdateArtifactRequest = UpdateArtifactRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getArtifactsByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Artifact with id '$entityId' not found.")
        val updated = current.copy(
            name = request.name ?: current.name,
            description = request.description ?: current.description,
            ownerId = request.ownerId ?: current.ownerId,
            ownerType = request.ownerType ?: current.ownerType,
            locationId = request.locationId ?: current.locationId,
        )
        repository.updateStoryArtifact(updated)
        return success("update", OperationEntity.ARTIFACT, storyId, updated.id, "Updated artifact '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete an artifact from the story.")
    suspend fun deleteArtifact(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Artifact id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryArtifact(entityId)
        return success("delete", OperationEntity.ARTIFACT, storyId, entityId, "Deleted artifact '$entityId'.")
    }

    @Tool
    @LLMDescription("Create a timeline entry in the story.")
    suspend fun createTimelineEntry(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Timeline entry fields to create")
        request: CreateTimelineEntryRequest,
    ): ToolResult<OperationOutcome> {
        val title = request.title.trim()
        if (title.isBlank()) return failure("MISSING_FIELD", "Timeline entry title is required.")
        val record = StoryTimelineEntryRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            title = title,
            description = request.description,
            orderIndex = request.orderIndex,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.insertStoryTimelineEntry(record)
        return success("create", OperationEntity.TIMELINE_ENTRY, storyId, record.id, "Added timeline entry '${record.title}'.")
    }

    @Tool
    @LLMDescription("Update a timeline entry in the story.")
    suspend fun updateTimelineEntry(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Timeline entry id to update")
        entityId: String,
        @LLMDescription("Timeline entry fields to update")
        request: UpdateTimelineEntryRequest = UpdateTimelineEntryRequest(),
    ): ToolResult<OperationOutcome> {
        val current = repository.getTimelineEntriesByStory(storyId).firstOrNull { it.id == entityId }
            ?: return failure("NOT_FOUND", "Timeline entry with id '$entityId' not found.")
        val updated = current.copy(
            title = request.title ?: current.title,
            description = request.description ?: current.description,
            orderIndex = request.orderIndex ?: current.orderIndex,
        )
        repository.updateStoryTimelineEntry(updated)
        return success("update", OperationEntity.TIMELINE_ENTRY, storyId, updated.id, "Updated timeline entry '$entityId'.")
    }

    @Tool
    @LLMDescription("Delete a timeline entry from the story.")
    suspend fun deleteTimelineEntry(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Timeline entry id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        repository.deleteStoryTimelineEntry(entityId)
        return success("delete", OperationEntity.TIMELINE_ENTRY, storyId, entityId, "Deleted timeline entry '$entityId'.")
    }

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
