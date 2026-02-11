package com.ead.dispatch.modifier

import com.ead.dispatch.constraints.Constraints

/**
 * Modifier that adds padding around content.
 */
interface PaddingModifier : Modifier.Element {
    val start: Int
    val end: Int
    val top: Int
    val bottom: Int

    /**
     * Total horizontal padding (start + end).
     */
    val horizontal: Int get() = start + end

    /**
     * Total vertical padding (top + bottom).
     */
    val vertical: Int get() = top + bottom
}

// ============================================================================
// Padding Functions
// ============================================================================

/**
 * Add padding on all sides.
 *
 * @param all Padding in characters/lines for all sides.
 */
fun Modifier.padding(all: Int): Modifier {
    require(all >= 0) { "padding must be >= 0" }
    return then(PaddingModifierImpl(all, all, all, all))
}

/**
 * Add padding with different horizontal and vertical values.
 *
 * @param horizontal Padding for start and end.
 * @param vertical Padding for top and bottom.
 */
fun Modifier.padding(horizontal: Int = 0, vertical: Int = 0): Modifier {
    require(horizontal >= 0) { "horizontal padding must be >= 0" }
    require(vertical >= 0) { "vertical padding must be >= 0" }
    if (horizontal == 0 && vertical == 0) return this
    return then(PaddingModifierImpl(horizontal, horizontal, vertical, vertical))
}

/**
 * Add padding with individual values for each side.
 *
 * @param start Left padding (in LTR layouts).
 * @param end Right padding (in LTR layouts).
 * @param top Top padding.
 * @param bottom Bottom padding.
 */
fun Modifier.padding(
    start: Int = 0,
    end: Int = 0,
    top: Int = 0,
    bottom: Int = 0,
): Modifier {
    require(start >= 0) { "start padding must be >= 0" }
    require(end >= 0) { "end padding must be >= 0" }
    require(top >= 0) { "top padding must be >= 0" }
    require(bottom >= 0) { "bottom padding must be >= 0" }
    val combinedPadding = start or end or top or bottom
    if (combinedPadding == 0) return this
    return then(PaddingModifierImpl(start, end, top, bottom))
}

/**
 * Add horizontal padding only.
 *
 * @param chars Padding on start and end.
 */
fun Modifier.horizontalPadding(chars: Int): Modifier = padding(horizontal = chars)

/**
 * Add vertical padding only.
 *
 * @param lines Padding on top and bottom.
 */
fun Modifier.verticalPadding(lines: Int): Modifier = padding(vertical = lines)

private data class PaddingModifierImpl(
    override val start: Int,
    override val end: Int,
    override val top: Int,
    override val bottom: Int,
) : PaddingModifier {
    override fun toString(): String = "Padding(start=$start, end=$end, top=$top, bottom=$bottom)"
}

// ============================================================================
// Helper to calculate total padding from modifier chain
// ============================================================================

/**
 * Calculate total padding from all padding modifiers in the chain.
 */
fun Modifier.totalPadding(): PaddingValues {
    var start = 0
    var end = 0
    var top = 0
    var bottom = 0

    foldIn(Unit) { _, element ->
        if (element is PaddingModifier) {
            start += element.start
            end += element.end
            top += element.top
            bottom += element.bottom
        }
    }

    return PaddingValues(start, end, top, bottom)
}

/**
 * Holder for padding values.
 */
data class PaddingValues(
    val start: Int,
    val end: Int,
    val top: Int,
    val bottom: Int,
) {
    val horizontal: Int get() = start + end
    val vertical: Int get() = top + bottom
    val isEmpty: Boolean get() = start == 0 && end == 0 && top == 0 && bottom == 0

    companion object {
        val Zero = PaddingValues(0, 0, 0, 0)
    }
}

/**
 * Apply padding offset to constraints.
 */
fun Constraints.offsetByPadding(padding: PaddingValues): Constraints =
    offset(horizontal = padding.horizontal, vertical = padding.vertical)

/**
 * Get padding from the modifier chain (returns null if no padding).
 */
fun Modifier.getPadding(): PaddingValues? {
    val total = totalPadding()
    return if (total.isEmpty) null else total
}
