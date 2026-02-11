package com.ead.dispatch.constraints

/**
 * Immutable constraints for measuring and laying out UI elements.
 *
 * All values are in terminal units:
 * - Width: characters (columns)
 * - Height: lines (rows)
 *
 * Constraints ensure components never overflow their allocated space.
 * This is critical for terminal UIs where overflow causes visual corruption.
 *
 * Example:
 * ```kotlin
 * val constraints = Constraints(
 *     minWidth = 10,
 *     maxWidth = 80,
 *     minHeight = 1,
 *     maxHeight = 24
 * )
 *
 * // Constrain a measured size
 * val actualWidth = constraints.constrainWidth(100)  // Returns 80
 * val actualHeight = constraints.constrainHeight(50) // Returns 24
 * ```
 */
data class Constraints(
    /**
     * Minimum width in characters.
     */
    val minWidth: Int = 0,

    /**
     * Maximum width in characters.
     */
    val maxWidth: Int = Int.MAX_VALUE,

    /**
     * Minimum height in lines.
     */
    val minHeight: Int = 0,

    /**
     * Maximum height in lines.
     */
    val maxHeight: Int = Int.MAX_VALUE,
) {
    init {
        require(minWidth >= 0) { "minWidth must be >= 0, was $minWidth" }
        require(maxWidth >= minWidth) { "maxWidth ($maxWidth) must be >= minWidth ($minWidth)" }
        require(minHeight >= 0) { "minHeight must be >= 0, was $minHeight" }
        require(maxHeight >= minHeight) { "maxHeight ($maxHeight) must be >= minHeight ($minHeight)" }
    }

    /**
     * Whether the width has an upper bound.
     */
    val hasBoundedWidth: Boolean get() = maxWidth != Int.MAX_VALUE

    /**
     * Whether the height has an upper bound.
     */
    val hasBoundedHeight: Boolean get() = maxHeight != Int.MAX_VALUE

    /**
     * Whether the width is fixed (min equals max).
     */
    val hasFixedWidth: Boolean get() = minWidth == maxWidth

    /**
     * Whether the height is fixed (min equals max).
     */
    val hasFixedHeight: Boolean get() = minHeight == maxHeight

    /**
     * Whether both dimensions are fixed.
     */
    val isFixed: Boolean get() = hasFixedWidth && hasFixedHeight

    /**
     * Whether constraints have zero size.
     */
    val isZero: Boolean get() = maxWidth == 0 || maxHeight == 0

    /**
     * Constrain a width value to these constraints.
     *
     * @param width The desired width.
     * @return Width clamped to [minWidth, maxWidth].
     */
    fun constrainWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    /**
     * Constrain a height value to these constraints.
     *
     * @param height The desired height.
     * @return Height clamped to [minHeight, maxHeight].
     */
    fun constrainHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    /**
     * Constrain both dimensions.
     *
     * @param width The desired width.
     * @param height The desired height.
     * @return A pair of (constrainedWidth, constrainedHeight).
     */
    fun constrain(width: Int, height: Int): Pair<Int, Int> =
        constrainWidth(width) to constrainHeight(height)

    /**
     * Create a copy with specified modifications, automatically coercing values to valid ranges.
     *
     * Unlike the auto-generated copy(), this ensures constraints remain valid
     * by coercing values (e.g., maxWidth is always >= minWidth).
     */
    fun copyCoerced(
        minWidth: Int = this.minWidth,
        maxWidth: Int = this.maxWidth,
        minHeight: Int = this.minHeight,
        maxHeight: Int = this.maxHeight,
    ): Constraints = Constraints(
        minWidth = minWidth.coerceAtLeast(0),
        maxWidth = maxWidth.coerceAtLeast(minWidth.coerceAtLeast(0)),
        minHeight = minHeight.coerceAtLeast(0),
        maxHeight = maxHeight.coerceAtLeast(minHeight.coerceAtLeast(0)),
    )

    /**
     * Create constraints with an offset applied.
     *
     * Useful for accounting for padding/borders.
     */
    fun offset(horizontal: Int = 0, vertical: Int = 0): Constraints =
        Constraints(
            minWidth = (minWidth - horizontal).coerceAtLeast(0),
            maxWidth = (maxWidth - horizontal).coerceAtLeast(0),
            minHeight = (minHeight - vertical).coerceAtLeast(0),
            maxHeight = (maxHeight - vertical).coerceAtLeast(0),
        )

    companion object {
        /**
         * Unbounded constraints (no limits).
         */
        val Unbounded = Constraints()

        /**
         * Zero-size constraints.
         */
        val Zero = Constraints(0, 0, 0, 0)

        /**
         * Fixed size constraints.
         *
         * @param width Exact width.
         * @param height Exact height.
         */
        fun fixed(width: Int, height: Int): Constraints =
            Constraints(width, width, height, height)

        /**
         * Fixed width, flexible height.
         *
         * @param width Exact width.
         */
        fun fixedWidth(width: Int): Constraints =
            Constraints(width, width, 0, Int.MAX_VALUE)

        /**
         * Fixed height, flexible width.
         *
         * @param height Exact height.
         */
        fun fixedHeight(height: Int): Constraints =
            Constraints(0, Int.MAX_VALUE, height, height)

        /**
         * Maximum size constraints (0 to max for both dimensions).
         */
        fun maxSize(width: Int, height: Int): Constraints =
            Constraints(0, width, 0, height)

        /**
         * Minimum size constraints.
         */
        fun minSize(width: Int, height: Int): Constraints =
            Constraints(width, Int.MAX_VALUE, height, Int.MAX_VALUE)
    }
}

/**
 * Exception thrown when a layout violates its constraints.
 */
class ConstraintViolationException(
    message: String,
    val componentName: String? = null,
    val constraints: Constraints? = null,
    val actualWidth: Int? = null,
    val actualHeight: Int? = null,
) : RuntimeException(message) {

    override fun toString(): String {
        val details = buildString {
            append("ConstraintViolationException: $message")
            componentName?.let { append("\n  Component: $it") }
            constraints?.let { append("\n  Constraints: $it") }
            if (actualWidth != null || actualHeight != null) {
                append("\n  Actual size: ${actualWidth ?: "?"} x ${actualHeight ?: "?"}")
            }
        }
        return details
    }
}
