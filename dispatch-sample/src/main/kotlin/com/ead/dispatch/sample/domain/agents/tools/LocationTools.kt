@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryLocationFeatureRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.CreateLocationFeatureRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateLocationRequest
import com.ead.dispatch.sample.domain.agents.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.ToolError
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateLocationFeatureRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateLocationRequest
import kotlinx.datetime.Clock
import java.util.UUID

class LocationTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

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
    @LLMDescription("Create a location in the story.")
    suspend fun createLocation(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location fields to create")
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

    @Tool
    @LLMDescription("Delete a location from the story.")
    suspend fun deleteLocation(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Location id to delete")
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
}
