package com.ead.dispatch.layout

/**
 * Alignment options for positioning content within a container.
 */
object Alignment {

    /**
     * Vertical alignment options.
     */
    sealed interface Vertical {
        /**
         * Calculate Y offset for content.
         *
         * @param containerHeight Available height.
         * @param contentHeight Content height.
         * @return Y offset.
         */
        fun align(containerHeight: Int, contentHeight: Int): Int
    }

    /**
     * Horizontal alignment options.
     */
    sealed interface Horizontal {
        /**
         * Calculate X offset for content.
         *
         * @param containerWidth Available width.
         * @param contentWidth Content width.
         * @return X offset.
         */
        fun align(containerWidth: Int, contentWidth: Int): Int
    }

    /**
     * 2D alignment options.
     */
    data class Alignment2D(
        val horizontal: Horizontal,
        val vertical: Vertical,
    ) {
        fun align(
            containerWidth: Int,
            containerHeight: Int,
            contentWidth: Int,
            contentHeight: Int,
        ): Pair<Int, Int> {
            return horizontal.align(containerWidth, contentWidth) to
                   vertical.align(containerHeight, contentHeight)
        }
    }

    // ========================================================================
    // Vertical Alignments
    // ========================================================================

    /**
     * Align to the top.
     */
    object Top : Vertical {
        override fun align(containerHeight: Int, contentHeight: Int): Int = 0
    }

    /**
     * Align to the center vertically.
     */
    object CenterVertically : Vertical {
        override fun align(containerHeight: Int, contentHeight: Int): Int =
            ((containerHeight - contentHeight) / 2).coerceAtLeast(0)
    }

    /**
     * Align to the bottom.
     */
    object Bottom : Vertical {
        override fun align(containerHeight: Int, contentHeight: Int): Int =
            (containerHeight - contentHeight).coerceAtLeast(0)
    }

    // ========================================================================
    // Horizontal Alignments
    // ========================================================================

    /**
     * Align to the start (left in LTR).
     */
    object Start : Horizontal {
        override fun align(containerWidth: Int, contentWidth: Int): Int = 0
    }

    /**
     * Align to the center horizontally.
     */
    object CenterHorizontally : Horizontal {
        override fun align(containerWidth: Int, contentWidth: Int): Int =
            ((containerWidth - contentWidth) / 2).coerceAtLeast(0)
    }

    /**
     * Align to the end (right in LTR).
     */
    object End : Horizontal {
        override fun align(containerWidth: Int, contentWidth: Int): Int =
            (containerWidth - contentWidth).coerceAtLeast(0)
    }

    // ========================================================================
    // Combined Alignments
    // ========================================================================

    /**
     * Top-left corner.
     */
    val TopStart = Alignment2D(Start, Top)

    /**
     * Top-center.
     */
    val TopCenter = Alignment2D(CenterHorizontally, Top)

    /**
     * Top-right corner.
     */
    val TopEnd = Alignment2D(End, Top)

    /**
     * Center-left.
     */
    val CenterStart = Alignment2D(Start, CenterVertically)

    /**
     * Dead center.
     */
    val Center = Alignment2D(CenterHorizontally, CenterVertically)

    /**
     * Center-right.
     */
    val CenterEnd = Alignment2D(End, CenterVertically)

    /**
     * Bottom-left corner.
     */
    val BottomStart = Alignment2D(Start, Bottom)

    /**
     * Bottom-center.
     */
    val BottomCenter = Alignment2D(CenterHorizontally, Bottom)

    /**
     * Bottom-right corner.
     */
    val BottomEnd = Alignment2D(End, Bottom)
}
