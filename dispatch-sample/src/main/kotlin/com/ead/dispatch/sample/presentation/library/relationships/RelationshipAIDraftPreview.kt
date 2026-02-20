package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.RelationshipAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun RelationshipAIDraftPreview(
    draft: RelationshipAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Subject", draft.subjectName))
        add(LabeledValue("Subject Type", draft.subjectType))
        add(LabeledValue("Object", draft.objectName))
        add(LabeledValue("Object Type", draft.objectType))
        add(LabeledValue("Relation", draft.relation))
        add(LabeledValue("History", draft.history))
        add(LabeledValue("Tension", draft.tension))
        add(LabeledValue("Status", draft.currentStatus))
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
