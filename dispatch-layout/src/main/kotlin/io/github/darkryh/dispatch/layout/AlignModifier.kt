package io.github.darkryh.dispatch.layout

import io.github.darkryh.dispatch.modifier.Modifier

// These align modifiers live in the layout module (not dispatch-runtime) because they reference
// the layout-only `Alignment` types. The runtime module only owns the generic `Modifier.Element`
// contract; layout depends on runtime, not the other way around, so the typed elements, the scoped
// `align` extensions, and the readers are all kept here together.

/**
 * Per-child alignment override for [Box] children.
 *
 * When present on a child's modifier, this 2D alignment is used instead of the Box's
 * `contentAlignment` when positioning that child.
 *
 * @property alignment The 2D alignment to apply to the child.
 */
data class BoxAlignModifier(
    val alignment: Alignment.Alignment2D,
) : Modifier.Element {
    override fun toString(): String = "BoxAlign($alignment)"
}

/**
 * Per-child horizontal alignment override for [Column] children.
 *
 * When present on a child's modifier, this horizontal alignment is used instead of the
 * Column's `horizontalAlignment` when positioning that child.
 *
 * @property alignment The horizontal alignment to apply to the child.
 */
data class ColumnAlignModifier(
    val alignment: Alignment.Horizontal,
) : Modifier.Element {
    override fun toString(): String = "ColumnAlign($alignment)"
}

/**
 * Per-child vertical alignment override for [Row] children.
 *
 * When present on a child's modifier, this vertical alignment is used instead of the
 * Row's `verticalAlignment` when positioning that child.
 *
 * @property alignment The vertical alignment to apply to the child.
 */
data class RowAlignModifier(
    val alignment: Alignment.Vertical,
) : Modifier.Element {
    override fun toString(): String = "RowAlign($alignment)"
}

// ============================================================================
// Scoped align extensions
// ============================================================================

/**
 * Align this child within its [Box] using the given 2D alignment, overriding the Box's
 * `contentAlignment` for this child only.
 *
 * Only available inside a [Box] content lambda.
 *
 * @param alignment The 2D alignment to apply to this child.
 * @return A modifier carrying the per-child alignment.
 */
fun BoxScope.align(alignment: Alignment.Alignment2D): Modifier = BoxAlignModifier(alignment)

/**
 * Align this child within its [Column] using the given horizontal alignment, overriding the
 * Column's `horizontalAlignment` for this child only.
 *
 * Only available inside a [Column] content lambda.
 *
 * @param alignment The horizontal alignment to apply to this child.
 * @return A modifier carrying the per-child alignment.
 */
fun ColumnScope.align(alignment: Alignment.Horizontal): Modifier = ColumnAlignModifier(alignment)

/**
 * Align this child within its [Row] using the given vertical alignment, overriding the
 * Row's `verticalAlignment` for this child only.
 *
 * Only available inside a [Row] content lambda.
 *
 * @param alignment The vertical alignment to apply to this child.
 * @return A modifier carrying the per-child alignment.
 */
fun RowScope.align(alignment: Alignment.Vertical): Modifier = RowAlignModifier(alignment)

// ============================================================================
// Readers
// ============================================================================

/**
 * Read the per-child [Box] alignment from this modifier chain, or `null` if none is present.
 *
 * @return The overriding 2D alignment, or `null` to fall back to the container alignment.
 */
fun Modifier.boxAlign(): Alignment.Alignment2D? = firstOrNull(BoxAlignModifier::class.java)?.alignment

/**
 * Read the per-child [Column] horizontal alignment from this modifier chain, or `null` if none.
 *
 * @return The overriding horizontal alignment, or `null` to fall back to the container alignment.
 */
fun Modifier.columnAlign(): Alignment.Horizontal? = firstOrNull(ColumnAlignModifier::class.java)?.alignment

/**
 * Read the per-child [Row] vertical alignment from this modifier chain, or `null` if none.
 *
 * @return The overriding vertical alignment, or `null` to fall back to the container alignment.
 */
fun Modifier.rowAlign(): Alignment.Vertical? = firstOrNull(RowAlignModifier::class.java)?.alignment
