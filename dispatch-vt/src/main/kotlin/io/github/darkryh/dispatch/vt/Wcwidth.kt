package io.github.darkryh.dispatch.vt

/**
 * Unicode display-width ("wcwidth") model for the VT test harness.
 *
 * A terminal is addressed in *columns*, not in characters: a CJK ideograph or an emoji claims two
 * columns, a combining mark claims none, and a control character is never drawn at all. [Wcwidth]
 * answers "how many columns does this code point occupy?" so the screen model can place, diff and
 * compare cells the way a real terminal does.
 *
 * The classification is table-driven. The wide and zero-width ranges are stored as sorted
 * `(start, end)` pairs in a flat [IntArray] and probed with a binary search, so a lookup is
 * `O(log n)` with no allocation — cheap enough to call once per cell per frame. Only what the JDK
 * cannot answer directly is tabulated; the combining-mark and format categories come from
 * [Character.getType].
 *
 * This model is strictly **per code point**. Grapheme clustering is the caller's responsibility:
 * - a base character followed by U+FE0F (VARIATION SELECTOR-16) renders as a single *two-column*
 *   cluster, even though `width(base) + width(0xFE0F)` may total only 1 here;
 * - a ZWJ sequence (family emoji, professions) renders as one two-column cluster;
 * - a regional-indicator pair renders as one two-column flag, not two wide cells.
 *
 * A caller that segments text into grapheme clusters should therefore take the width of the
 * cluster's base code point and treat any emoji-presentation cluster as 2.
 */
object Wcwidth {
    /**
     * Zero-width ranges that are not (or not reliably) covered by [Character.getType].
     *
     * Sorted, non-overlapping, flattened `(start, end)` inclusive pairs.
     */
    private val ZERO_WIDTH_RANGES: IntArray =
        intArrayOf(
            0x200B, 0x200F, // ZWSP, ZWNJ, ZWJ, LRM, RLM
            0xFE00, 0xFE0F, // variation selectors 1..16 (U+FE0F is the emoji-presentation selector)
            0xFEFF, 0xFEFF, // zero-width no-break space / BOM
        )

    /**
     * East Asian Wide (W) and Fullwidth (F) ranges, plus the emoji blocks that terminals render
     * double-width.
     *
     * Sorted, non-overlapping, flattened `(start, end)` inclusive pairs. The supplementary emoji
     * planes are folded into one broad `U+1F000..U+1FAFF` range: the individually narrow code
     * points inside it are unassigned or unused in practice, and the fold keeps the table small and
     * the search shallow. The range deliberately stops before U+1FB00 (Symbols for Legacy
     * Computing), which is narrow and heavily used by terminal art.
     */
    private val WIDE_RANGES: IntArray =
        intArrayOf(
            0x1100, 0x115F, // Hangul Jamo initial consonants
            0x231A, 0x231B, // watch, hourglass
            0x2329, 0x232A, // angle brackets
            0x23E9, 0x23EC, // media control symbols
            0x23F0, 0x23F0, // alarm clock
            0x23F3, 0x23F3, // hourglass with flowing sand
            0x25FD, 0x25FE, // small squares
            0x2614, 0x2615, // umbrella, hot beverage
            0x2648, 0x2653, // zodiac
            0x267F, 0x267F, // wheelchair symbol
            0x2693, 0x2693, // anchor
            0x26A1, 0x26A1, // high voltage
            0x26AA, 0x26AB, // circles
            0x26BD, 0x26BE, // soccer ball, baseball
            0x26C4, 0x26C5, // snowman, sun behind cloud
            0x26CE, 0x26CE, // ophiuchus
            0x26D4, 0x26D4, // no entry
            0x26EA, 0x26EA, // church
            0x26F2, 0x26F3, // fountain, golf
            0x26F5, 0x26F5, // sailboat
            0x26FA, 0x26FA, // tent
            0x26FD, 0x26FD, // fuel pump
            0x2705, 0x2705, // check mark button
            0x270A, 0x270B, // raised fist, raised hand
            0x2728, 0x2728, // sparkles
            0x274C, 0x274C, // cross mark
            0x274E, 0x274E, // cross mark button
            0x2753, 0x2755, // question / exclamation marks
            0x2757, 0x2757, // exclamation mark
            0x2795, 0x2797, // heavy plus / minus / division
            0x27B0, 0x27B0, // curly loop
            0x27BF, 0x27BF, // double curly loop
            0x2B1B, 0x2B1C, // black / white large square
            0x2B50, 0x2B50, // star
            0x2B55, 0x2B55, // heavy large circle
            0x2E80, 0x303E, // CJK radicals, Kangxi radicals, CJK symbols and punctuation
            0x3041, 0x33FF, // Hiragana .. CJK compatibility
            0x3400, 0x4DBF, // CJK unified ideographs extension A
            0x4E00, 0x9FFF, // CJK unified ideographs
            0xA000, 0xA4CF, // Yi syllables and radicals
            0xA960, 0xA97F, // Hangul Jamo extended-A
            0xAC00, 0xD7A3, // Hangul syllables
            0xF900, 0xFAFF, // CJK compatibility ideographs
            0xFE10, 0xFE19, // vertical forms
            0xFE30, 0xFE6F, // CJK compatibility forms, small form variants
            0xFF00, 0xFF60, // fullwidth forms
            0xFFE0, 0xFFE6, // fullwidth signs
            0x16FE0, 0x16FE4, // Tangut / Nushu iteration marks
            0x17000, 0x187F7, // Tangut
            0x18800, 0x18CD5, // Tangut components
            0x18D00, 0x18D08, // Tangut supplement
            0x1AFF0, 0x1AFFE, // Kana extended-B
            0x1B000, 0x1B152, // Kana supplement, Kana extended-A
            0x1B164, 0x1B167, // small Kana extension
            0x1B170, 0x1B2FB, // Nushu
            0x1F000, 0x1FAFF, // mahjong .. symbols and pictographs extended-A (emoji, folded)
            0x20000, 0x2FFFD, // CJK extensions B..F (plane 2)
            0x30000, 0x3FFFD, // CJK extension G and beyond (plane 3)
        )

    /**
     * Display columns claimed by a single Unicode code point: `0`, `1` or `2`.
     *
     * C0/C1 controls and DEL return `0`; combining marks, enclosing marks, format characters and
     * the explicit zero-width code points return `0`; East Asian Wide/Fullwidth and emoji return
     * `2`; everything else returns `1`. Negative or otherwise invalid values are treated as
     * non-printing and return `0`.
     */
    fun width(codePoint: Int): Int =
        when {
            codePoint < 0x20 -> 0
            codePoint in 0x7F..0x9F -> 0
            inRanges(ZERO_WIDTH_RANGES, codePoint) -> 0
            isZeroWidthCategory(codePoint) -> 0
            inRanges(WIDE_RANGES, codePoint) -> 2
            else -> 1
        }

    /**
     * Display columns claimed by [text], summed over its code points.
     *
     * Surrogate pairs are decoded, so a supplementary-plane emoji counts once. Because the sum is
     * per code point, combining sequences and emoji ZWJ/variation-selector clusters are *not*
     * collapsed here — see the note on [Wcwidth].
     */
    fun width(text: CharSequence): Int {
        var total = 0
        var index = 0
        val length = text.length
        while (index < length) {
            val codePoint = Character.codePointAt(text, index)
            total += width(codePoint)
            index += Character.charCount(codePoint)
        }
        return total
    }

    /** `true` when [codePoint] is a non-spacing mark, an enclosing mark or a format character. */
    private fun isZeroWidthCategory(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.FORMAT.toInt()
    }

    /**
     * Binary-searches [ranges] — a sorted, flattened array of inclusive `(start, end)` pairs — for
     * [codePoint].
     */
    private fun inRanges(ranges: IntArray, codePoint: Int): Boolean {
        var low = 0
        var high = ranges.size / 2 - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = ranges[mid * 2]
            val end = ranges[mid * 2 + 1]
            when {
                codePoint < start -> high = mid - 1
                codePoint > end -> low = mid + 1
                else -> return true
            }
        }
        return false
    }
}
