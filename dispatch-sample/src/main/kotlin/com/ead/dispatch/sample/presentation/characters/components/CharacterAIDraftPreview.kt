package com.ead.dispatch.sample.presentation.characters.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterAIDraft
import com.ead.dispatch.widget.Text

@Dispatchable
fun CharacterAIDraftPreview(
    draft: CharacterAIDraft,
    styles: CharacterScreenStyles,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val summary = draft.summary.trim()
        if (summary.isNotEmpty()) {
            PreviewRow(label = "Summary", value = summary, styles = styles)
        }

        PreviewRow(label = "Name", value = draft.name, styles = styles)
        PreviewRow(label = "Description", value = draft.description, styles = styles)
        PreviewRow(label = "Roles", value = draft.roles.joinToString(", "), styles = styles)
        PreviewRow(label = "Goal", value = draft.goal, styles = styles)
        PreviewRow(label = "Motivation", value = draft.motivation, styles = styles)
        PreviewRow(label = "Flaw", value = draft.flaw, styles = styles)
        PreviewRow(label = "Internal Conflict", value = draft.internalConflict, styles = styles)
        PreviewRow(label = "Temperament", value = draft.temperament, styles = styles)
        PreviewRow(label = "Age", value = draft.age, styles = styles)
        PreviewRow(label = "Pronouns", value = draft.pronouns, styles = styles)
        PreviewRow(label = "Occupation", value = draft.occupation, styles = styles)
        PreviewRow(label = "Backstory", value = draft.backstory, styles = styles)
        PreviewRow(label = "Voice", value = draft.voice, styles = styles)
        PreviewRow(label = "Traits", value = draft.traits.joinToString(", "), styles = styles)
        PreviewRow(label = "Quirks", value = draft.quirks.joinToString(", "), styles = styles)

        val physical = draft.physical
        if (physical != null) {
            PreviewRow(label = "Appearance", value = physical.appearance, styles = styles)
            PreviewRow(label = "Height", value = physical.height, styles = styles)
            PreviewRow(label = "Build", value = physical.build, styles = styles)
            PreviewRow(label = "Hair", value = physical.hair, styles = styles)
            PreviewRow(label = "Eyes", value = physical.eyes, styles = styles)
            PreviewRow(label = "Skin Tone", value = physical.skinTone, styles = styles)
            PreviewRow(label = "Marks", value = physical.distinguishingMarks, styles = styles)
            PreviewRow(label = "Style Notes", value = physical.styleNotes, styles = styles)
        }

        if (draft.missingFields.isNotEmpty()) {
            PreviewRow(
                label = "Missing",
                value = draft.missingFields.joinToString(", "),
                styles = styles,
                secondary = true,
            )
        }
    }
}

@Dispatchable
private fun PreviewRow(
    label: String,
    value: String?,
    styles: CharacterScreenStyles,
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
