package com.ead.dispatch.sample.presentation.library.events

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.event_agent.EventAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun EventAIDraftPreview(
    draft: EventAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Timeframe", draft.timeframe))
        add(LabeledValue("Location", draft.location))
        add(LabeledValue("Causes", draft.causes.joinToString(", ")))
        add(LabeledValue("Consequences", draft.consequences.joinToString(", ")))
        add(LabeledValue("Participants", draft.participants.joinToString(", ")))
        add(LabeledValue("Status", draft.status))
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
