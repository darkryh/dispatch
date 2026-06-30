package com.ead.dispatch.theme

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles

/**
 * Theme for Dispatch applications.
 *
 * Defines colors and styles for UI elements.
 */
data class DispatchTheme(
    /**
     * Primary text color for headers and emphasis.
     */
    val primary: TextStyle,

    /**
     * Secondary text color for supporting text.
     */
    val secondary: TextStyle,

    /**
     * Muted text color for less important text.
     */
    val muted: TextStyle,

    /**
     * Accent color for highlights and selections.
     */
    val accent: TextStyle,

    /**
     * Success color for positive feedback.
     */
    val success: TextStyle,

    /**
     * Warning color for caution messages.
     */
    val warning: TextStyle,

    /**
     * Error color for error messages.
     */
    val error: TextStyle,

    /**
     * Info color for informational messages.
     */
    val info: TextStyle,

    /**
     * Code/monospace style.
     */
    val code: TextStyle,

    /**
     * Link style for URLs and references.
     */
    val link: TextStyle,

    /**
     * Border color.
     */
    val border: TextStyle,

    /**
     * Cursor style.
     */
    val cursor: TextStyle,

    /**
     * Selection background.
     */
    val selection: TextStyle,
) {
    /**
     * Apply primary style to text.
     */
    fun primary(text: String): String = primary(text)

    /**
     * Apply secondary style to text.
     */
    fun secondary(text: String): String = secondary(text)

    /**
     * Apply muted style to text.
     */
    fun muted(text: String): String = muted(text)

    /**
     * Apply accent style to text.
     */
    fun accent(text: String): String = accent(text)

    /**
     * Apply success style to text.
     */
    fun success(text: String): String = success(text)

    /**
     * Apply warning style to text.
     */
    fun warning(text: String): String = warning(text)

    /**
     * Apply error style to text.
     */
    fun error(text: String): String = error(text)

    /**
     * Apply info style to text.
     */
    fun info(text: String): String = info(text)

    companion object {
        /**
         * Dark theme (default).
         */
        val Dark = DispatchTheme(
            primary = TextColors.brightWhite,
            secondary = TextColors.cyan,
            // muted/border were TextColors.gray (ANSI bright-black, ~#686868) — only ~2.3:1 against a
            // dark background, so blurbs, instruction text and card borders looked washed out on the
            // native terminal. These light cool-grays read clearly on dark backgrounds (muted ~7:1,
            // border ~4.3:1) while staying visually subordinate to brightWhite primary. Mordant
            // downsamples them to the terminal's palette (e.g. macOS Terminal's 256 colors).
            muted = TextColors.rgb("#AAB2BD"),
            accent = TextColors.brightCyan,
            success = TextColors.green,
            warning = TextColors.yellow,
            // On black, pure red (3.6:1) and pure blue (2.1:1) are below AA. Brighten to keep the
            // hue while clearing the 4.5:1 text threshold (brightRed ~7.7:1, this blue ~6.7:1).
            error = TextColors.brightRed,
            info = TextColors.rgb("#5C8CFF"),
            code = TextColors.brightYellow,
            link = TextColors.brightBlue + TextStyles.underline,
            border = TextColors.rgb("#7E8794"),
            cursor = TextStyle(inverse = true),
            selection = TextColors.black on TextColors.white,
        )

        /**
         * Light theme.
         */
        val Light = DispatchTheme(
            // On a white background, muted/border gray (~5.6:1) read fine, but the standard ANSI
            // accent/success/warning/code are intrinsically too light (cyan 2.1:1, green 2.4:1,
            // yellow 1.9:1, magenta 4.4:1). Darken those — same hue family — to clear AA on white.
            primary = TextColors.black,
            secondary = TextColors.blue,
            muted = TextColors.gray,
            accent = TextColors.rgb("#007D7E"),
            success = TextColors.rgb("#007A00"),
            warning = TextColors.rgb("#767400"),
            error = TextColors.red,
            info = TextColors.blue,
            code = TextColors.rgb("#A800A6"),
            link = TextColors.blue + TextStyles.underline,
            border = TextColors.gray,
            cursor = TextStyle(inverse = true),
            selection = TextColors.white on TextColors.black,
        )

        /**
         * Minimal theme with fewer colors.
         */
        val Minimal = DispatchTheme(
            primary = TextStyle(bold = true),
            secondary = TextStyle(),
            muted = TextStyle(dim = true),
            accent = TextStyle(bold = true),
            success = TextColors.green,
            warning = TextColors.yellow,
            error = TextColors.red,
            info = TextStyle(),
            code = TextStyle(),
            link = TextStyle(underline = true),
            border = TextStyle(dim = true),
            cursor = TextStyle(inverse = true),
            selection = TextStyle(inverse = true),
        )
    }
}

/**
 * Text style presets.
 */
object DispatchTextStyle {
    val Default = TextStyle()
    val Bold = TextStyle(bold = true)
    val Dim = TextStyle(dim = true)
    val Italic = TextStyle(italic = true)
    val Underline = TextStyle(underline = true)
    val Inverse = TextStyle(inverse = true)
    val Strikethrough = TextStyle(strikethrough = true)
}
