package com.ead.dispatch.sample.presentation.entity_editor.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.Text

@Dispatchable
fun EntityFooter(
    text: String,
    styles: EntityScreenStyles,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Text(
            text = text,
            style = styles.footerText,
        )
    }
}
