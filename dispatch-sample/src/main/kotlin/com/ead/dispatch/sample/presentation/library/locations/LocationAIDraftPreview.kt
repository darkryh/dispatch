package com.ead.dispatch.sample.presentation.library.locations

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.internal.location_agent.LocationAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun LocationAIDraftPreview(
    draft: LocationAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Tags", draft.tags.joinToString(", ")))
        add(LabeledValue("Atmosphere", draft.atmosphere))
        add(LabeledValue("Function", draft.function))
        add(LabeledValue("Access", draft.access))
        add(LabeledValue("Risks", draft.risks.joinToString(", ")))
        add(LabeledValue("Story Use", draft.storyUse))
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
