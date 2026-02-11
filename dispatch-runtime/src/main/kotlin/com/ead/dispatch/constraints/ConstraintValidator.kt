package com.ead.dispatch.constraints

/**
 * Validates that layout operations respect constraints.
 *
 * This prevents components from overflowing their allocated space,
 * which is critical for terminal UIs where overflow causes visual corruption.
 *
 * Example:
 * ```kotlin
 * val constraints = Constraints.fixed(80, 24)
 *
 * // This will throw - width exceeds max
 * ConstraintValidator.validate(
 *     constraints = constraints,
 *     measuredWidth = 100,
 *     measuredHeight = 10,
 *     componentName = "MyWidget"
 * )
 * ```
 */
object ConstraintValidator {

    /**
     * Validation mode for constraint checking.
     */
    enum class Mode {
        /**
         * Throw exception on violation (development mode).
         */
        STRICT,

        /**
         * Log warning and coerce values (production mode).
         */
        LENIENT,

        /**
         * Silently coerce values (no logging).
         */
        SILENT,
    }

    /**
     * Current validation mode.
     */
    @Volatile
    var mode: Mode = Mode.STRICT

    /**
     * Callback for logging warnings in lenient mode.
     */
    var warningLogger: ((String) -> Unit)? = { message ->
        System.err.println("Warning: $message")
    }

    /**
     * Validates that a measured size fits within constraints.
     *
     * @param constraints The constraints to validate against.
     * @param measuredWidth The measured width.
     * @param measuredHeight The measured height.
     * @param componentName Optional name for error messages.
     * @throws ConstraintViolationException if size exceeds constraints in STRICT mode.
     * @return A pair of (validatedWidth, validatedHeight), possibly coerced.
     */
    fun validate(
        constraints: Constraints,
        measuredWidth: Int,
        measuredHeight: Int,
        componentName: String = "Component",
    ): Pair<Int, Int> {
        val violations = mutableListOf<String>()

        // Check width violations
        if (measuredWidth > constraints.maxWidth) {
            violations.add("width ($measuredWidth) exceeds maxWidth (${constraints.maxWidth})")
        }
        if (measuredWidth < constraints.minWidth) {
            violations.add("width ($measuredWidth) is less than minWidth (${constraints.minWidth})")
        }

        // Check height violations
        if (measuredHeight > constraints.maxHeight) {
            violations.add("height ($measuredHeight) exceeds maxHeight (${constraints.maxHeight})")
        }
        if (measuredHeight < constraints.minHeight) {
            violations.add("height ($measuredHeight) is less than minHeight (${constraints.minHeight})")
        }

        if (violations.isNotEmpty()) {
            val message = "$componentName constraint violation: ${violations.joinToString("; ")}"

            when (mode) {
                Mode.STRICT -> {
                    throw ConstraintViolationException(
                        message = message,
                        componentName = componentName,
                        constraints = constraints,
                        actualWidth = measuredWidth,
                        actualHeight = measuredHeight,
                    )
                }
                Mode.LENIENT -> {
                    warningLogger?.invoke(message)
                }
                Mode.SILENT -> {
                    // Do nothing
                }
            }
        }

        // Always return coerced values
        return constraints.constrainWidth(measuredWidth) to constraints.constrainHeight(measuredHeight)
    }

    /**
     * Validates and coerces a size to fit constraints.
     *
     * Unlike [validate], this never throws - it always returns a valid size.
     *
     * @param constraints The constraints to validate against.
     * @param measuredWidth The measured width.
     * @param measuredHeight The measured height.
     * @param componentName Optional name for warning messages.
     * @return A pair of (coercedWidth, coercedHeight).
     */
    fun coerce(
        constraints: Constraints,
        measuredWidth: Int,
        measuredHeight: Int,
        componentName: String = "Component",
    ): Pair<Int, Int> {
        val coercedWidth = constraints.constrainWidth(measuredWidth)
        val coercedHeight = constraints.constrainHeight(measuredHeight)

        // Log warning if coercion was needed
        if ((coercedWidth != measuredWidth || coercedHeight != measuredHeight) && mode == Mode.LENIENT) {
            warningLogger?.invoke(
                "$componentName size ($measuredWidth x $measuredHeight) " +
                "coerced to ($coercedWidth x $coercedHeight) to fit constraints"
            )
        }

        return coercedWidth to coercedHeight
    }

    /**
     * Check if a size fits within constraints.
     *
     * @return true if size is valid, false otherwise.
     */
    fun isValid(
        constraints: Constraints,
        width: Int,
        height: Int,
    ): Boolean =
        width >= constraints.minWidth &&
            width <= constraints.maxWidth &&
            height >= constraints.minHeight &&
            height <= constraints.maxHeight

    /**
     * Create a validation scope that temporarily changes the mode.
     */
    inline fun <T> withMode(tempMode: Mode, block: () -> T): T {
        val previousMode = mode
        mode = tempMode
        try {
            return block()
        } finally {
            mode = previousMode
        }
    }
}
