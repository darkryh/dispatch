package com.ead.dispatch.modifier

import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Border style options.
 */
enum class BorderStyle {
    /**
     * No border.
     */
    None,

    /**
     * ASCII border (+--+)
     */
    Ascii,

    /**
     * Light rounded border (╭──╮)
     */
    Rounded,

    /**
     * Light square border (┌──┐)
     */
    Square,

    /**
     * Heavy border (┏━━┓)
     */
    Heavy,

    /**
     * Double line border (╔══╗)
     */
    Double,

    /**
     * Dashed border (┄┄┄)
     */
    Dashed,
}

/**
 * Modifier that adds a border around content.
 */
interface BorderModifierElement : Modifier.Element {
    val style: BorderStyle
    val textStyle: TextStyle?
    val title: String?

    /**
     * Width consumed by the border (1 on each side for most styles).
     */
    val horizontalBorderWidth: Int get() = if (style == BorderStyle.None) 0 else 2

    /**
     * Height consumed by the border (1 on top and bottom for most styles).
     */
    val verticalBorderHeight: Int get() = if (style == BorderStyle.None) 0 else 2
}

/**
 * Add a border around the content.
 *
 * @param style The border style.
 * @param textStyle Optional text style for the border characters.
 */
fun Modifier.border(
    style: BorderStyle = BorderStyle.Rounded,
    textStyle: TextStyle? = null,
): Modifier {
    if (style == BorderStyle.None) return this
    return then(BorderModifierImpl(style, textStyle, null))
}

/**
 * Add a border with a title.
 *
 * @param style The border style.
 * @param title Title to display in the top border.
 * @param textStyle Optional text style for the border characters.
 */
fun Modifier.border(
    style: BorderStyle = BorderStyle.Rounded,
    title: String,
    textStyle: TextStyle? = null,
): Modifier {
    if (style == BorderStyle.None) return this
    return then(BorderModifierImpl(style, textStyle, title))
}

private data class BorderModifierImpl(
    override val style: BorderStyle,
    override val textStyle: TextStyle?,
    override val title: String?,
) : BorderModifierElement {
    override fun toString(): String = "Border($style${if (title != null) ", title=$title" else ""})"
}

/**
 * Get the border modifier from the chain, if any.
 */
fun Modifier.getBorder(): BorderModifierElement? = firstOrNull<BorderModifierElement>()

/**
 * Border characters for each style.
 */
object BorderCharacters {
    data class BorderChars(
        val topLeft: Char,
        val topRight: Char,
        val bottomLeft: Char,
        val bottomRight: Char,
        val horizontal: Char,
        val vertical: Char,
    )

    val Ascii = BorderChars('+', '+', '+', '+', '-', '|')
    val Rounded = BorderChars('╭', '╮', '╰', '╯', '─', '│')
    val Square = BorderChars('┌', '┐', '└', '┘', '─', '│')
    val Heavy = BorderChars('┏', '┓', '┗', '┛', '━', '┃')
    val Double = BorderChars('╔', '╗', '╚', '╝', '═', '║')
    val Dashed = BorderChars('┌', '┐', '└', '┘', '┄', '┆')

    fun forStyle(style: BorderStyle): BorderChars = when (style) {
        BorderStyle.None -> Ascii // Won't be used
        BorderStyle.Ascii -> Ascii
        BorderStyle.Rounded -> Rounded
        BorderStyle.Square -> Square
        BorderStyle.Heavy -> Heavy
        BorderStyle.Double -> Double
        BorderStyle.Dashed -> Dashed
    }
}
