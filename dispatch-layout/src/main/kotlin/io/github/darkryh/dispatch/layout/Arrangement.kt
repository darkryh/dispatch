package io.github.darkryh.dispatch.layout

/**
 * Arrangement for distributing children in a layout.
 */
object Arrangement {
    /**
     * Vertical arrangement options for Column.
     */
    sealed interface Vertical {
        /**
         * The fixed gap this arrangement inserts between adjacent children, in cells.
         *
         * Layouts must reserve `(childCount - 1) * spacing` in their measured size so the
         * reported bounds cover the positions produced by [arrange]. Arrangements that only
         * distribute leftover space (Top, Bottom, Center, SpaceBetween, ...) report `0`.
         */
        val spacing: Int get() = 0

        /**
         * Calculate positions for children.
         *
         * @param totalSize Total available height.
         * @param sizes Sizes of each child.
         * @return List of Y positions for each child.
         */
        fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int>
    }

    /**
     * Horizontal arrangement options for Row.
     */
    sealed interface Horizontal {
        /**
         * The fixed gap this arrangement inserts between adjacent children, in cells.
         *
         * Layouts must reserve `(childCount - 1) * spacing` in their measured size so the
         * reported bounds cover the positions produced by [arrange]. Arrangements that only
         * distribute leftover space (Start, End, Center, SpaceBetween, ...) report `0`.
         */
        val spacing: Int get() = 0

        /**
         * Calculate positions for children.
         *
         * @param totalSize Total available width.
         * @param sizes Sizes of each child.
         * @return List of X positions for each child.
         */
        fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int>
    }

    /**
     * Arrangement that can be used for both directions.
     */
    sealed interface HorizontalOrVertical :
        Vertical,
        Horizontal {
        override val spacing: Int get() = 0
    }

    // ========================================================================
    // Vertical Arrangements
    // ========================================================================

    /**
     * Place children at the top/start.
     */
    object Top : Vertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            val positions = mutableListOf<Int>()
            var offset = 0
            for (size in sizes) {
                positions.add(offset)
                offset += size
            }
            return positions
        }
    }

    /**
     * Place children at the bottom/end.
     */
    object Bottom : Vertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            val contentSize = sizes.sum()
            val startOffset = (totalSize - contentSize).coerceAtLeast(0)
            val positions = mutableListOf<Int>()
            var offset = startOffset
            for (size in sizes) {
                positions.add(offset)
                offset += size
            }
            return positions
        }
    }

    // ========================================================================
    // Horizontal Arrangements
    // ========================================================================

    /**
     * Place children at the start (left in LTR).
     */
    object Start : Horizontal {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            val positions = mutableListOf<Int>()
            var offset = 0
            for (size in sizes) {
                positions.add(offset)
                offset += size
            }
            return positions
        }
    }

    /**
     * Place children at the end (right in LTR).
     */
    object End : Horizontal {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            val contentSize = sizes.sum()
            val startOffset = (totalSize - contentSize).coerceAtLeast(0)
            val positions = mutableListOf<Int>()
            var offset = startOffset
            for (size in sizes) {
                positions.add(offset)
                offset += size
            }
            return positions
        }
    }

    // ========================================================================
    // Shared Arrangements
    // ========================================================================

    /**
     * Center children in the available space.
     */
    object Center : HorizontalOrVertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            val contentSize = sizes.sum()
            val startOffset = ((totalSize - contentSize) / 2).coerceAtLeast(0)
            val positions = mutableListOf<Int>()
            var offset = startOffset
            for (size in sizes) {
                positions.add(offset)
                offset += size
            }
            return positions
        }
    }

    /**
     * Distribute space evenly between children.
     */
    object SpaceBetween : HorizontalOrVertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            if (sizes.isEmpty()) return emptyList()
            if (sizes.size == 1) return listOf(0)

            val contentSize = sizes.sum()
            val remainingSpace = (totalSize - contentSize).coerceAtLeast(0)
            val spacing = remainingSpace / (sizes.size - 1)

            val positions = mutableListOf<Int>()
            var offset = 0
            for ((index, size) in sizes.withIndex()) {
                positions.add(offset)
                offset += size
                if (index < sizes.size - 1) {
                    offset += spacing
                }
            }
            return positions
        }
    }

    /**
     * Distribute space evenly around children.
     */
    object SpaceAround : HorizontalOrVertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            if (sizes.isEmpty()) return emptyList()

            val contentSize = sizes.sum()
            val remainingSpace = (totalSize - contentSize).coerceAtLeast(0)
            val spacing = remainingSpace / (sizes.size * 2)

            val positions = mutableListOf<Int>()
            var offset = spacing
            for (size in sizes) {
                positions.add(offset)
                offset += size + spacing * 2
            }
            return positions
        }
    }

    /**
     * Distribute space evenly, with equal space before, between, and after children.
     */
    object SpaceEvenly : HorizontalOrVertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            if (sizes.isEmpty()) return emptyList()

            val contentSize = sizes.sum()
            val remainingSpace = (totalSize - contentSize).coerceAtLeast(0)
            val spacing = remainingSpace / (sizes.size + 1)

            val positions = mutableListOf<Int>()
            var offset = spacing
            for (size in sizes) {
                positions.add(offset)
                offset += size + spacing
            }
            return positions
        }
    }

    /**
     * Place children with fixed spacing between them.
     *
     * @param spacing Space between children.
     */
    class SpacedBy(
        override val spacing: Int,
    ) : HorizontalOrVertical {
        override fun arrange(
            totalSize: Int,
            sizes: List<Int>,
        ): List<Int> {
            val positions = mutableListOf<Int>()
            var offset = 0
            for ((index, size) in sizes.withIndex()) {
                positions.add(offset)
                offset += size
                if (index < sizes.size - 1) {
                    offset += spacing
                }
            }
            return positions
        }
    }

    /**
     * Create arrangement with fixed spacing.
     */
    fun spacedBy(spacing: Int): HorizontalOrVertical = SpacedBy(spacing)
}
