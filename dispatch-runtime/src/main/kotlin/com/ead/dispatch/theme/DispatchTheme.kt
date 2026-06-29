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
            muted = TextColors.gray,
            accent = TextColors.brightCyan,
            success = TextColors.green,
            warning = TextColors.yellow,
            error = TextColors.red,
            info = TextColors.blue,
            code = TextColors.brightYellow,
            link = TextColors.brightBlue + TextStyles.underline,
            border = TextColors.gray,
            cursor = TextStyle(inverse = true),
            selection = TextColors.black on TextColors.white,
        )

        /**
         * Light theme.
         */
        val Light = DispatchTheme(
            primary = TextColors.black,
            secondary = TextColors.blue,
            muted = TextColors.gray,
            accent = TextColors.cyan,
            success = TextColors.green,
            warning = TextColors.yellow,
            error = TextColors.red,
            info = TextColors.blue,
            code = TextColors.magenta,
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
