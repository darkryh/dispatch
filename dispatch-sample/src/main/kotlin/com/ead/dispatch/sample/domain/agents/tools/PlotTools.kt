@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryEventRecord
import com.ead.dispatch.sample.data.db.entities.StoryTimelineEntryRecord
import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.CreateArcRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateEventRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateTimelineEntryRequest
import com.ead.dispatch.sample.domain.agents.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.ToolError
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateArcRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateEventRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateTimelineEntryRequest
import kotlinx.datetime.Clock
import java.util.UUID

class PlotTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

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
    @LLMDescription("Create an arc in the story.")
    suspend fun createArc(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Arc fields to create")
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

    @Tool
    @LLMDescription("Delete an arc from the story.")
    suspend fun deleteArc(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Arc id to delete")
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
}
