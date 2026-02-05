package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.domain.agents.relationship_agent.RelationshipAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.Text

@Dispatchable
fun RelationshipAIDraftPreview(
    draft: RelationshipAIDraft,
    styles: EditorScreenStyles,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val summary = draft.summary.trim()
        if (summary.isNotEmpty()) {
            PreviewRow("Summary", summary, styles)
        }

        PreviewRow("Subject", draft.subjectName, styles)
        PreviewRow("Subject Type", draft.subjectType, styles)
        PreviewRow("Object", draft.objectName, styles)
        PreviewRow("Object Type", draft.objectType, styles)
        PreviewRow("Relation", draft.relation, styles)
        PreviewRow("History", draft.history, styles)
        PreviewRow("Tension", draft.tension, styles)
        PreviewRow("Status", draft.currentStatus, styles)

        if (draft.missingFields.isNotEmpty()) {
            PreviewRow("Missing", draft.missingFields.joinToString(", "), styles, secondary = true)
        }
    }
}

@Dispatchable
private fun PreviewRow(
    label: String,
    value: String?,
    styles: EditorScreenStyles,
    secondary: Boolean = false,
) {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Text(text = "$label:", style = styles.labelStyle)
        Spacer(Modifier.width(1))
        Text(text = text, style = if (secondary) styles.hintText else styles.fieldText)
    }
}
