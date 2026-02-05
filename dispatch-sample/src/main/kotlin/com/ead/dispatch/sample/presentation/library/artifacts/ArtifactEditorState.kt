package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorAIMode
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorMode
import com.ead.dispatch.sample.presentation.util.FieldValue

data class ArtifactEditorState(
    val storyId: String? = null,
    val artifactId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val values: Map<EditorFieldKey, FieldValue> = emptyMap(),
    val mode: EditorMode = EditorMode.MANUAL,
    val status: String? = null,
    val confirmDelete: Boolean = false,
    val createdAt: Long? = null,
    val isGenerating: Boolean = false,
    val aiDraft: ArtifactAIDraft? = null,
    val aiError: String? = null,
    val aiMode: EditorAIMode = EditorAIMode.CREATIVE,
)
