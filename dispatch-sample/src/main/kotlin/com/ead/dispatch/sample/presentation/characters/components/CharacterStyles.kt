package com.ead.dispatch.sample.presentation.characters.components

import com.ead.dispatch.annotation.Dispatchable
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

data class CharacterScreenStyles(
    val headerBackground: TextStyle,
    val headerText: TextStyle,
    val sectionTitle: TextStyle,
    val hintText: TextStyle,
    val fieldFill: TextStyle,
    val fieldText: TextStyle,
    val fieldPlaceholder: TextStyle,
    val labelStylePrimary: TextStyle,
    val labelStyleSecondary: TextStyle,
    val labelStyle: TextStyle,
    val footerText: TextStyle,
)

@Dispatchable
fun rememberCharacterScreenStyles(): CharacterScreenStyles {
    return CharacterScreenStyles(
        headerBackground = rgb("#2B313A"),
        headerText = rgb("#6BE3FF") + TextStyle(bold = true),
        sectionTitle = rgb("#E6EAF0") + TextStyle(bold = true),
        hintText = rgb("#A7B2BF"),
        fieldFill = rgb("#363C46"),
        fieldText = rgb("#FFFFFF"),
        fieldPlaceholder = rgb("#82858A"),
        labelStylePrimary = rgb("#7ADBFD"),
        labelStyleSecondary = rgb("#7ADBFD"),
        labelStyle = rgb("#1FA9DF"),
        footerText = rgb("#8A95A5"),
    )
}
