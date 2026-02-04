package com.ead.dispatch.sample.presentation.entity_editor

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.*
import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.navigation.EntityEditorRoute
import com.ead.dispatch.sample.presentation.entity_editor.event.EntityEditorEvent
import com.ead.dispatch.sample.presentation.entity_editor.state.EntityEditorState
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityEditorMode
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class EntityEditorViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntityEditorRoute>()

    private val _uiState = MutableStateFlow(
        EntityEditorState(
            storyId = route.storyId,
            entityId = route.entityId,
            type = EntityOptionType.fromId(route.type),
            isLoading = true,
        )
    )
    val uiState: StateFlow<EntityEditorState> = _uiState.asStateFlow()

    private val editableTypes = setOf(
        EntityOptionType.LOCATIONS,
        EntityOptionType.ARCS,
        EntityOptionType.WORLD_RULES,
        EntityOptionType.CULTURES,
        EntityOptionType.EVENTS,
        EntityOptionType.ORGANIZATIONS,
        EntityOptionType.RELATIONSHIPS,
        EntityOptionType.LOCATION_FEATURES,
        EntityOptionType.ARTIFACTS,
        EntityOptionType.TIMELINE,
    )

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        val type = EntityOptionType.fromId(route.type)
        when {
            storyId == null -> {
                _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
            }
            type == null -> {
                _uiState.update { it.copy(isLoading = false, error = "Unknown entity type: ${route.type}") }
            }
            !editableTypes.contains(type) -> {
                _uiState.update { it.copy(isLoading = false, error = "Editing not supported for ${type.title}.") }
            }
            else -> {
                val entityId = route.entityId?.trim().takeIf { !it.isNullOrEmpty() }
                if (entityId == null) {
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching {
                            loadEntity(storyId, type, entityId)
                        }.onSuccess { loaded ->
                            if (loaded == null) {
                                _uiState.update {
                                    it.copy(isLoading = false, error = "${type.title} not found.")
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        values = loaded.values,
                                        createdAt = loaded.createdAt,
                                    )
                                }
                            }
                        }.onFailure { error ->
                            _uiState.update {
                                it.copy(isLoading = false, error = error.message ?: "Failed to load entity.")
                            }
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: EntityEditorEvent) {
        when (event) {
            is EntityEditorEvent.OnToggleMode -> toggleMode()
            is EntityEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is EntityEditorEvent.OnSave -> saveEntity()
            is EntityEditorEvent.OnRequestDelete -> requestDelete()
            is EntityEditorEvent.OnConfirmDelete -> confirmDelete()
            is EntityEditorEvent.OnCancelDelete -> cancelDelete()
        }
    }

    private fun toggleMode() {
        _uiState.update { state ->
            state.copy(
                mode = if (state.mode == EntityEditorMode.MANUAL) EntityEditorMode.AUTOMATIC else EntityEditorMode.MANUAL,
            )
        }
    }

    private fun updateField(key: EntityFieldKey, text: String) {
        _uiState.update { state ->
            val current = state.values[key] ?: FieldValue()
            state.copy(values = state.values + (key to current.copy(text = text)))
        }
    }

    private fun requestDelete() {
        val currentId = _uiState.value.entityId?.trim().takeIf { !it.isNullOrEmpty() }
        if (currentId == null) {
            _uiState.update { it.copy(status = "Save before deleting.") }
            return
        }
        _uiState.update { it.copy(confirmDelete = true, status = null) }
    }

    private fun cancelDelete() {
        _uiState.update { it.copy(confirmDelete = false) }
    }

    private fun confirmDelete() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        val entityId = state.entityId?.trim().takeIf { !it.isNullOrEmpty() }
        val type = state.type
        if (storyId == null || entityId == null || type == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when (type) {
                    EntityOptionType.LOCATIONS -> repository.deleteStoryLocation(entityId)
                    EntityOptionType.ARCS -> repository.deleteStoryArc(entityId)
                    EntityOptionType.WORLD_RULES -> repository.deleteStoryWorldRule(entityId)
                    EntityOptionType.CULTURES -> repository.deleteStoryCulture(entityId)
                    EntityOptionType.EVENTS -> repository.deleteStoryEvent(entityId)
                    EntityOptionType.ORGANIZATIONS -> repository.deleteStoryOrganization(entityId)
                    EntityOptionType.RELATIONSHIPS -> repository.deleteStoryRelationship(entityId)
                    EntityOptionType.LOCATION_FEATURES -> repository.deleteStoryLocationFeature(entityId)
                    EntityOptionType.ARTIFACTS -> repository.deleteStoryArtifact(entityId)
                    EntityOptionType.TIMELINE -> repository.deleteStoryTimelineEntry(entityId)
                    else -> return@runCatching
                }
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "${type.title} deleted.") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(confirmDelete = false, status = error.message ?: "Delete failed.")
                }
            }
        }
    }

    private fun saveEntity() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        val type = state.type
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }
        if (type == null || !editableTypes.contains(type)) {
            _uiState.update { it.copy(status = "Unsupported entity type.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                saveEntityInternal(state, storyId, type)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        entityId = result.entityId,
                        createdAt = result.createdAt,
                        status = result.message,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private suspend fun loadEntity(
        storyId: String,
        type: EntityOptionType,
        entityId: String,
    ): LoadedEntity? {
        return when (type) {
            EntityOptionType.LOCATIONS -> {
                repository.getLocationsByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromLocation(record), record.createdAt)
                    }
            }
            EntityOptionType.ARCS -> {
                repository.getArcsByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromArc(record), record.createdAt)
                    }
            }
            EntityOptionType.WORLD_RULES -> {
                repository.getWorldRulesByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromWorldRule(record), record.createdAt)
                    }
            }
            EntityOptionType.CULTURES -> {
                repository.getCulturesByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromCulture(record), record.createdAt)
                    }
            }
            EntityOptionType.EVENTS -> {
                repository.getEventsByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromEvent(record), record.createdAt)
                    }
            }
            EntityOptionType.ORGANIZATIONS -> {
                repository.getOrganizationsByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromOrganization(record), record.createdAt)
                    }
            }
            EntityOptionType.RELATIONSHIPS -> {
                repository.getRelationshipsByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromRelationship(record), record.createdAt)
                    }
            }
            EntityOptionType.LOCATION_FEATURES -> {
                repository.getLocationFeaturesByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromLocationFeature(record), record.createdAt)
                    }
            }
            EntityOptionType.ARTIFACTS -> {
                repository.getArtifactsByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromArtifact(record), record.createdAt)
                    }
            }
            EntityOptionType.TIMELINE -> {
                repository.getTimelineEntriesByStory(storyId)
                    .firstOrNull { it.id == entityId }
                    ?.let { record ->
                        LoadedEntity(valuesFromTimelineEntry(record), record.createdAt)
                    }
            }
            else -> null
        }
    }

    private suspend fun saveEntityInternal(
        state: EntityEditorState,
        storyId: String,
        type: EntityOptionType,
    ): SaveResult {
        val values = state.values
        val now = System.currentTimeMillis()
        val existingId = state.entityId?.trim().takeIf { !it.isNullOrEmpty() }
        val createdAt = state.createdAt ?: now

        return when (type) {
            EntityOptionType.LOCATIONS -> {
                val name = values.read(EntityFieldKey.NAME)
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val tags = parseTags(values.read(EntityFieldKey.TAGS))
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryLocationRecord(
                    id = id,
                    storyId = storyId,
                    profile = StoryLocationRecord.LocationProfile(
                        name = name,
                        description = description,
                    ),
                    tags = tags,
                    createdAt = createdAt,
                )
                repository.upsertLocation(record)
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.ARCS -> {
                val title = values.read(EntityFieldKey.TITLE)
                if (title.isBlank()) throw IllegalArgumentException("Title is required.")
                val summary = values.read(EntityFieldKey.SUMMARY).ifBlank { null }
                val scopeTypeValue = values.read(EntityFieldKey.SCOPE_TYPE).ifBlank { "STORY" }
                val scopeType = parseArcScope(scopeTypeValue)
                    ?: throw IllegalArgumentException("Invalid scope type.")
                val scopeId = values.read(EntityFieldKey.SCOPE_ID).ifBlank { null }
                if (scopeType != ArcScope.STORY && scopeId == null) {
                    throw IllegalArgumentException("Scope id is required for ${scopeType.name}.")
                }
                val statusValue = values.read(EntityFieldKey.STATUS).ifBlank { null }
                val status = statusValue?.let { parseStatus(it) }
                if (statusValue != null && status == null) {
                    throw IllegalArgumentException("Invalid status value.")
                }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryArcRecord(
                    id = id,
                    storyId = storyId,
                    scopeType = scopeType,
                    scopeId = scopeId,
                    title = title,
                    summary = summary,
                    status = status,
                    createdAt = createdAt,
                    updatedAt = now,
                )
                repository.upsertArc(record)
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.WORLD_RULES -> {
                val title = values.read(EntityFieldKey.TITLE)
                if (title.isBlank()) throw IllegalArgumentException("Title is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryWorldRuleRecord(
                    id = id,
                    storyId = storyId,
                    title = title,
                    description = description,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryWorldRule(record)
                } else {
                    repository.updateStoryWorldRule(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.CULTURES -> {
                val name = values.read(EntityFieldKey.NAME)
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryCultureRecord(
                    id = id,
                    storyId = storyId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryCulture(record)
                } else {
                    repository.updateStoryCulture(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.EVENTS -> {
                val name = values.read(EntityFieldKey.NAME)
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryEventRecord(
                    id = id,
                    storyId = storyId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryEvent(record)
                } else {
                    repository.updateStoryEvent(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.ORGANIZATIONS -> {
                val name = values.read(EntityFieldKey.NAME)
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryOrganizationRecord(
                    id = id,
                    storyId = storyId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryOrganization(record)
                } else {
                    repository.updateStoryOrganization(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.RELATIONSHIPS -> {
                val subjectType = values.read(EntityFieldKey.SUBJECT_TYPE)
                val subjectId = values.read(EntityFieldKey.SUBJECT_ID)
                val objectType = values.read(EntityFieldKey.OBJECT_TYPE)
                val objectId = values.read(EntityFieldKey.OBJECT_ID)
                val relation = values.read(EntityFieldKey.RELATION)
                if (subjectType.isBlank() || subjectId.isBlank() || objectType.isBlank() || objectId.isBlank() || relation.isBlank()) {
                    throw IllegalArgumentException("Subject, object, and relation are required.")
                }
                val notes = values.read(EntityFieldKey.NOTES).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryRelationshipRecord(
                    id = id,
                    storyId = storyId,
                    subjectId = subjectId,
                    subjectType = subjectType,
                    objectId = objectId,
                    objectType = objectType,
                    relation = relation,
                    notes = notes,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryRelationship(record)
                } else {
                    repository.updateStoryRelationship(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.LOCATION_FEATURES -> {
                val name = values.read(EntityFieldKey.NAME)
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val locationId = values.read(EntityFieldKey.LOCATION_ID).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryLocationFeatureRecord(
                    id = id,
                    storyId = storyId,
                    locationId = locationId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryLocationFeature(record)
                } else {
                    repository.updateStoryLocationFeature(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.ARTIFACTS -> {
                val name = values.read(EntityFieldKey.NAME)
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val ownerType = values.read(EntityFieldKey.OWNER_TYPE).ifBlank { null }
                val ownerId = values.read(EntityFieldKey.OWNER_ID).ifBlank { null }
                val locationId = values.read(EntityFieldKey.LOCATION_ID).ifBlank { null }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryArtifactRecord(
                    id = id,
                    storyId = storyId,
                    name = name,
                    description = description,
                    ownerId = ownerId,
                    ownerType = ownerType,
                    locationId = locationId,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryArtifact(record)
                } else {
                    repository.updateStoryArtifact(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            EntityOptionType.TIMELINE -> {
                val title = values.read(EntityFieldKey.TITLE)
                if (title.isBlank()) throw IllegalArgumentException("Title is required.")
                val description = values.read(EntityFieldKey.DESCRIPTION).ifBlank { null }
                val orderInput = values.read(EntityFieldKey.ORDER_INDEX)
                val orderIndex = if (orderInput.isBlank()) {
                    if (existingId == null) {
                        nextTimelineOrderIndex(storyId)
                    } else {
                        throw IllegalArgumentException("Order index is required.")
                    }
                } else {
                    orderInput.toLongOrNull()
                        ?: throw IllegalArgumentException("Order index must be a number.")
                }
                val id = existingId ?: UUID.randomUUID().toString()
                val record = StoryTimelineEntryRecord(
                    id = id,
                    storyId = storyId,
                    title = title,
                    description = description,
                    orderIndex = orderIndex,
                    createdAt = createdAt,
                )
                if (existingId == null) {
                    repository.insertStoryTimelineEntry(record)
                } else {
                    repository.updateStoryTimelineEntry(record)
                }
                SaveResult(id, createdAt, messageFor(type, existingId == null))
            }
            else -> throw IllegalArgumentException("Unsupported entity type.")
        }
    }

    private fun valuesFromLocation(record: StoryLocationRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.NAME to FieldValue(record.profile.name),
        EntityFieldKey.DESCRIPTION to FieldValue(record.profile.description.orEmpty()),
        EntityFieldKey.TAGS to FieldValue(record.tags.joinToString(", ")),
    )

    private fun valuesFromArc(record: StoryArcRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.TITLE to FieldValue(record.title),
        EntityFieldKey.SUMMARY to FieldValue(record.summary.orEmpty()),
        EntityFieldKey.SCOPE_TYPE to FieldValue(record.scopeType.name),
        EntityFieldKey.SCOPE_ID to FieldValue(record.scopeId.orEmpty()),
        EntityFieldKey.STATUS to FieldValue(record.status?.name.orEmpty()),
    )

    private fun valuesFromWorldRule(record: StoryWorldRuleRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.TITLE to FieldValue(record.title),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun valuesFromCulture(record: StoryCultureRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.NAME to FieldValue(record.name),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun valuesFromEvent(record: StoryEventRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.NAME to FieldValue(record.name),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun valuesFromOrganization(record: StoryOrganizationRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.NAME to FieldValue(record.name),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun valuesFromRelationship(record: StoryRelationshipRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.SUBJECT_TYPE to FieldValue(record.subjectType),
        EntityFieldKey.SUBJECT_ID to FieldValue(record.subjectId),
        EntityFieldKey.OBJECT_TYPE to FieldValue(record.objectType),
        EntityFieldKey.OBJECT_ID to FieldValue(record.objectId),
        EntityFieldKey.RELATION to FieldValue(record.relation),
        EntityFieldKey.NOTES to FieldValue(record.notes.orEmpty()),
    )

    private fun valuesFromLocationFeature(record: StoryLocationFeatureRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.NAME to FieldValue(record.name),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
        EntityFieldKey.LOCATION_ID to FieldValue(record.locationId.orEmpty()),
    )

    private fun valuesFromArtifact(record: StoryArtifactRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.NAME to FieldValue(record.name),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
        EntityFieldKey.OWNER_TYPE to FieldValue(record.ownerType.orEmpty()),
        EntityFieldKey.OWNER_ID to FieldValue(record.ownerId.orEmpty()),
        EntityFieldKey.LOCATION_ID to FieldValue(record.locationId.orEmpty()),
    )

    private fun valuesFromTimelineEntry(record: StoryTimelineEntryRecord): Map<EntityFieldKey, FieldValue> = mapOf(
        EntityFieldKey.TITLE to FieldValue(record.title),
        EntityFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
        EntityFieldKey.ORDER_INDEX to FieldValue(record.orderIndex.toString()),
    )

    private fun parseTags(raw: String): List<String> =
        raw.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun parseArcScope(value: String): ArcScope? =
        ArcScope.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    private fun parseStatus(value: String): ContentStatus? =
        ContentStatus.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    private suspend fun nextTimelineOrderIndex(storyId: String): Long {
        val entries = repository.getTimelineEntriesByStory(storyId)
        val max = entries.maxOfOrNull { it.orderIndex } ?: 0L
        return max + 1
    }

    private fun Map<EntityFieldKey, FieldValue>.read(key: EntityFieldKey): String =
        this[key]?.text?.trim().orEmpty()

    private fun messageFor(type: EntityOptionType, isNew: Boolean): String {
        val verb = if (isNew) "created" else "updated"
        return "${type.title} $verb."
    }

    private data class LoadedEntity(
        val values: Map<EntityFieldKey, FieldValue>,
        val createdAt: Long,
    )

    private data class SaveResult(
        val entityId: String,
        val createdAt: Long,
        val message: String,
    )
}
