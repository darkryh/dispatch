package com.ead.dispatch.modifier

/**
 * Marker modifier that shifts a child by a fixed number of cells after its normal
 * position has been computed by the parent layout.
 *
 * The shift is applied in integer terminal cells: [x] columns to the right and [y]
 * lines down (negative values move left/up). Clipping and out-of-bounds handling are
 * performed by the rasterizer, so an offset that pushes a child past an edge is simply
 * clipped rather than erroring.
 *
 * @property x Horizontal shift in cells (columns).
 * @property y Vertical shift in cells (lines).
 */
data class OffsetModifier(val x: Int, val y: Int) : Modifier.Element {
    override fun toString(): String = "Offset($x, $y)"
}

/**
 * Offset the content by a fixed number of cells relative to its placed position.
 *
 * The offset is applied after the parent layout computes the child's position, so it
 * does not affect measurement or the positions of siblings.
 *
 * @param x Horizontal shift in cells (columns); positive moves right.
 * @param y Vertical shift in cells (lines); positive moves down.
 * @return A modifier chain that includes the offset.
 */
fun Modifier.offset(x: Int = 0, y: Int = 0): Modifier = then(OffsetModifier(x, y))

/**
 * Read the [OffsetModifier] from this modifier chain, or `null` if none is present.
 *
 * @return The first [OffsetModifier] in the chain, or `null`.
 */
fun Modifier.getOffset(): OffsetModifier? = firstOrNull(OffsetModifier::class.java)
