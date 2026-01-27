package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryArtifactRecord
import com.ead.dispatch.sample.data.db.entities.StoryCultureRecord
import com.ead.dispatch.sample.data.db.entities.StoryOrganizationRecord
import com.ead.dispatch.sample.data.db.entities.StoryWorldRuleRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.CreateArtifactRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateCultureRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateOrganizationRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateWorldRuleRequest
import com.ead.dispatch.sample.domain.agents.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.ToolError
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateArtifactRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateCultureRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateOrganizationRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateWorldRuleRequest
import kotlinx.datetime.Clock
import java.util.UUID

class WorldTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

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

    // --- Helpers ---

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
