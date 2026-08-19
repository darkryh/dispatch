package io.github.darkryh.dispatch.vt

/**
 * Visual attributes of a single screen cell.
 *
 * Colours are kept as the raw SGR parameter list that produced them rather than resolved RGB, so
 * that a style-only change (a recolour with identical text) is still detectable as a change, which
 * is exactly what the damage invariants need to measure.
 *
 * [background] is the discriminator for "blank": a space over the terminal's default background is
 * invisible, but a space over a painted background is not.
 */
data class CellStyle(
    val foreground: String? = null,
    val background: String? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val strikethrough: Boolean = false,
) {
    /** True when this style paints nothing — the terminal's own ground shows through. */
    val isDefault: Boolean
        get() = this == DEFAULT

    companion object {
        val DEFAULT: CellStyle = CellStyle()
    }
}

/**
 * One character cell of the screen.
 *
 * @property codePoint the Unicode code point occupying the cell; [BLANK_CODE_POINT] for an erased cell.
 * @property style the visual attributes in force when the cell was written.
 * @property continuation true for the right-hand half of a double-width character. A continuation
 *   cell carries the same [codePoint] as its lead cell so that reading a row yields the glyph once
 *   per lead cell and never mid-glyph.
 */
data class Cell(
    val codePoint: Int = BLANK_CODE_POINT,
    val style: CellStyle = CellStyle.DEFAULT,
    val continuation: Boolean = false,
) {
    /**
     * True when the cell shows nothing at all: no glyph and no painted background.
     *
     * This is the predicate the blank-presentation invariant is built on. A cell holding a space
     * with a background colour is *not* blank — it is a painted surface — and treating it as blank
     * would make every themed panel look like a flicker.
     */
    val isBlank: Boolean
        get() = (codePoint == BLANK_CODE_POINT || codePoint == SPACE_CODE_POINT) &&
            style.background == null &&
            !style.inverse

    /** The cell rendered as text, using a space for an erased cell. */
    fun toChar(): String =
        when (codePoint) {
            BLANK_CODE_POINT -> " "
            else -> String(Character.toChars(codePoint))
        }

    companion object {
        /** An erased cell — never written since the last erase. */
        const val BLANK_CODE_POINT: Int = 0
        const val SPACE_CODE_POINT: Int = 32

        val BLANK: Cell = Cell()
    }
}
