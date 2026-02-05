package com.ead.dispatch.sample.presentation.library.cultures

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.culture_agent.CultureAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun CultureAIDraftPreview(
    draft: CultureAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Values", draft.values.joinToString(", ")))
        add(LabeledValue("Rituals", draft.rituals.joinToString(", ")))
        add(LabeledValue("Taboos", draft.taboos.joinToString(", ")))
        add(LabeledValue("Symbols", draft.symbols.joinToString(", ")))
        add(LabeledValue("Social Structure", draft.socialStructure))
        add(LabeledValue("Story Role", draft.storyRole))
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
