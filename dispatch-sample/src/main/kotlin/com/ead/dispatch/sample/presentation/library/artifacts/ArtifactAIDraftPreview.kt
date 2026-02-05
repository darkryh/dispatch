package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun ArtifactAIDraftPreview(
    draft: ArtifactAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Origin", draft.origin))
        add(LabeledValue("Powers", draft.powers.joinToString(", ")))
        add(LabeledValue("Costs", draft.costs.joinToString(", ")))
        add(LabeledValue("Limitations", draft.limitations.joinToString(", ")))
        add(LabeledValue("Owner", draft.owner))
        add(LabeledValue("Owner Type", draft.ownerType))
        add(LabeledValue("Location", draft.location))
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
