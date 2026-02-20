package com.ead.dispatch.sample.presentation.library.world_rules

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.sample.domain.agents.internal.world_rule_agent.WorldRuleAIDraft
import com.ead.dispatch.sample.presentation.editor.components.EditorScreenStyles
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList

@Dispatchable
fun WorldRuleAIDraftPreview(
    draft: WorldRuleAIDraft,
    styles: EditorScreenStyles,
) {
    val summary = draft.summary.trim()
    val items = buildList {
        if (summary.isNotEmpty()) add(LabeledValue("Summary", summary))
        add(LabeledValue("Title", draft.title))
        add(LabeledValue("Rule", draft.rule))
        add(LabeledValue("Scope", draft.scope))
        add(LabeledValue("Implications", draft.implications.joinToString(", ")))
        add(LabeledValue("Exceptions", draft.exceptions.joinToString(", ")))
        add(LabeledValue("Story Impact", draft.storyImpact))
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
