package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.Text

@Dispatchable
fun ArtifactAIDraftPreview(
    draft: ArtifactAIDraft,
    styles: EditorScreenStyles,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val summary = draft.summary.trim()
        if (summary.isNotEmpty()) {
            PreviewRow("Summary", summary, styles)
        }

        PreviewRow("Name", draft.name, styles)
        PreviewRow("Description", draft.description, styles)
        PreviewRow("Origin", draft.origin, styles)
        PreviewRow("Powers", draft.powers.joinToString(", "), styles)
        PreviewRow("Costs", draft.costs.joinToString(", "), styles)
        PreviewRow("Limitations", draft.limitations.joinToString(", "), styles)
        PreviewRow("Owner", draft.owner, styles)
        PreviewRow("Owner Type", draft.ownerType, styles)
        PreviewRow("Location", draft.location, styles)

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
