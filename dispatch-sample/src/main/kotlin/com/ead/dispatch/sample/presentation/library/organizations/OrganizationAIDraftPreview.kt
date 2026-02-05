package com.ead.dispatch.sample.presentation.library.organizations

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.organization_agent.OrganizationAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun OrganizationAIDraftPreview(
    draft: OrganizationAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Purpose", draft.purpose))
        add(LabeledValue("Structure", draft.structure))
        add(LabeledValue("Resources", draft.resources.joinToString(", ")))
        add(LabeledValue("Public Image", draft.publicImage))
        add(LabeledValue("Secrets", draft.secrets))
        add(LabeledValue("Rivals", draft.rivals.joinToString(", ")))
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
