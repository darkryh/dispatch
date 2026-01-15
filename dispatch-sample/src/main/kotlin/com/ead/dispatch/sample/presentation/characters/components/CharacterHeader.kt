package com.ead.dispatch.sample.presentation.characters.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.Background
import com.ead.dispatch.widget.BackgroundStyle
import com.ead.dispatch.widget.Text

@Dispatchable
fun CharacterHeader(
    title: String,
    styles: CharacterScreenStyles,
) {
    Background(
        modifier = Modifier.fillMaxWidth(),
        style = BackgroundStyle.Fill(
            fill = styles.headerBackground,
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            Text(
                text = title,
                style = styles.headerText,
            )
        }
    }
}
