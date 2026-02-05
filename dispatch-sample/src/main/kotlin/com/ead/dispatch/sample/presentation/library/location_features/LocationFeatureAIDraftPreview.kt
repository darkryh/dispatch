package com.ead.dispatch.sample.presentation.library.location_features

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.location_feature_agent.LocationFeatureAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun LocationFeatureAIDraftPreview(
    draft: LocationFeatureAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Name", draft.name))
        add(LabeledValue("Description", draft.description))
        add(LabeledValue("Feature Type", draft.featureType))
        add(LabeledValue("Function", draft.function))
        add(LabeledValue("Risks", draft.risks.joinToString(", ")))
        add(LabeledValue("Location", draft.relatedLocation))
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
