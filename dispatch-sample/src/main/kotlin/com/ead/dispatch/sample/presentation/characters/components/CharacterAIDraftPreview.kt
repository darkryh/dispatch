package com.ead.dispatch.sample.presentation.characters.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterAIDraft
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun CharacterAIDraftPreview(
    draft: CharacterAIDraft,
    styles: CharacterScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Roles", draft.roles.joinToString(", ")))
        add(LabeledValue("Goal", draft.goal))
        add(LabeledValue("Motivation", draft.motivation))
        add(LabeledValue("Flaw", draft.flaw))
        add(LabeledValue("Internal Conflict", draft.internalConflict))
        add(LabeledValue("Temperament", draft.temperament))
        add(LabeledValue("Age", draft.age))
        add(LabeledValue("Pronouns", draft.pronouns))
        add(LabeledValue("Occupation", draft.occupation))
        add(LabeledValue("Backstory", draft.backstory))
        add(LabeledValue("Voice", draft.voice))
        add(LabeledValue("Traits", draft.traits.joinToString(", ")))
        add(LabeledValue("Quirks", draft.quirks.joinToString(", ")))

        val physical = draft.physical

        if (physical != null) {
            add(LabeledValue("Appearance", physical.appearance))
            add(LabeledValue("Height", physical.height))
            add(LabeledValue("Build", physical.build))
            add(LabeledValue("Hair", physical.hair))
            add(LabeledValue("Eyes", physical.eyes))
            add(LabeledValue("Skin Tone", physical.skinTone))
            add(LabeledValue("Marks", physical.distinguishingMarks))
            add(LabeledValue("Style Notes", physical.styleNotes))
        }

        if (draft.missingFields.isNotEmpty()) {
            add(LabeledValue("Missing", draft.missingFields.joinToString(", "), valueStyle = styles.hintText))
        }
    }

    LabeledValueList(
        items = items,
        labelStyle = styles.labelStyle,
        valueStyle = styles.fieldText,
    )
}
