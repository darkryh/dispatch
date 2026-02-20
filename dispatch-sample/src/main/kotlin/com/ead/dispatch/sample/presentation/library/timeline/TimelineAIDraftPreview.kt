package com.ead.dispatch.sample.presentation.library.timeline

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.TimelineAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun TimelineAIDraftPreview(
    draft: TimelineAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Title", draft.title))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Time", draft.time))
        add(LabeledValue("Order", draft.orderIndex?.toString()))
        add(LabeledValue("Impact", draft.impact.joinToString(", ")))
        add(LabeledValue("Certainty", draft.certainty))
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
