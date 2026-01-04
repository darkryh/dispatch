package com.ead.dispatch.modifier

import com.ead.dispatch.constraints.Constraints

/**
 * Modifier that affects the size constraints of a component.
 */
interface SizeModifier : Modifier.Element {
    /**
     * Modify constraints before measuring a child.
     *
     * @param constraints The incoming constraints.
     * @return Modified constraints for the child.
     */
    fun modifyConstraints(constraints: Constraints): Constraints
}

// ============================================================================
// Fill Modifiers
// ============================================================================

/**
 * Have the content fill the maximum available width.
 *
 * @param fraction The fraction of available width to fill (0.0 to 1.0).
 */
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier {
    require(fraction in 0f..1f) { "fraction must be between 0 and 1, was $fraction" }
    return then(FillMaxWidthModifier(fraction))
}

/**
 * Have the content fill the maximum available height.
 *
 * @param fraction The fraction of available height to fill (0.0 to 1.0).
 */
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier {
    require(fraction in 0f..1f) { "fraction must be between 0 and 1, was $fraction" }
    return then(FillMaxHeightModifier(fraction))
}

/**
 * Have the content fill both maximum width and height.
 *
 * @param fraction The fraction of available size to fill (0.0 to 1.0).
 */
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier =
    fillMaxWidth(fraction).fillMaxHeight(fraction)

private class FillMaxWidthModifier(private val fraction: Float) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        if (!constraints.hasBoundedWidth) return constraints
        val targetWidth = (constraints.maxWidth * fraction).toInt()
        return constraints.copy(minWidth = targetWidth, maxWidth = targetWidth)
    }

    override fun equals(other: Any?): Boolean = other is FillMaxWidthModifier && other.fraction == fraction
    override fun hashCode(): Int = fraction.hashCode()
    override fun toString(): String = "FillMaxWidth($fraction)"
}

private class FillMaxHeightModifier(private val fraction: Float) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        if (!constraints.hasBoundedHeight) return constraints
        val targetHeight = (constraints.maxHeight * fraction).toInt()
        return constraints.copy(minHeight = targetHeight, maxHeight = targetHeight)
    }

    override fun equals(other: Any?): Boolean = other is FillMaxHeightModifier && other.fraction == fraction
    override fun hashCode(): Int = fraction.hashCode()
    override fun toString(): String = "FillMaxHeight($fraction)"
}

// ============================================================================
// Fixed Size Modifiers
// ============================================================================

/**
 * Set an exact width in characters.
 *
 * @param chars The exact width.
 */
fun Modifier.width(chars: Int): Modifier {
    require(chars >= 0) { "width must be >= 0, was $chars" }
    return then(WidthModifier(chars))
}

/**
 * Set an exact height in lines.
 *
 * @param lines The exact height.
 */
fun Modifier.height(lines: Int): Modifier {
    require(lines >= 0) { "height must be >= 0, was $lines" }
    return then(HeightModifier(lines))
}

/**
 * Set an exact size.
 *
 * @param width The exact width in characters.
 * @param height The exact height in lines.
 */
fun Modifier.size(width: Int, height: Int): Modifier = width(width).height(height)

private class WidthModifier(private val chars: Int) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        val coerced = constraints.constrainWidth(chars)
        return constraints.copy(minWidth = coerced, maxWidth = coerced)
    }

    override fun equals(other: Any?): Boolean = other is WidthModifier && other.chars == chars
    override fun hashCode(): Int = chars
    override fun toString(): String = "Width($chars)"
}

private class HeightModifier(private val lines: Int) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        val coerced = constraints.constrainHeight(lines)
        return constraints.copy(minHeight = coerced, maxHeight = coerced)
    }

    override fun equals(other: Any?): Boolean = other is HeightModifier && other.lines == lines
    override fun hashCode(): Int = lines
    override fun toString(): String = "Height($lines)"
}

// ============================================================================
// Range Size Modifiers
// ============================================================================

/**
 * Constrain width to a range.
 *
 * @param min Minimum width (0 for no minimum).
 * @param max Maximum width (Int.MAX_VALUE for no maximum).
 */
fun Modifier.widthIn(min: Int = 0, max: Int = Int.MAX_VALUE): Modifier {
    require(min >= 0) { "min must be >= 0" }
    require(max >= min) { "max must be >= min" }
    return then(WidthInModifier(min, max))
}

/**
 * Constrain height to a range.
 *
 * @param min Minimum height (0 for no minimum).
 * @param max Maximum height (Int.MAX_VALUE for no maximum).
 */
fun Modifier.heightIn(min: Int = 0, max: Int = Int.MAX_VALUE): Modifier {
    require(min >= 0) { "min must be >= 0" }
    require(max >= min) { "max must be >= min" }
    return then(HeightInModifier(min, max))
}

/**
 * Constrain both dimensions to a range.
 */
fun Modifier.sizeIn(
    minWidth: Int = 0,
    maxWidth: Int = Int.MAX_VALUE,
    minHeight: Int = 0,
    maxHeight: Int = Int.MAX_VALUE,
): Modifier = widthIn(minWidth, maxWidth).heightIn(minHeight, maxHeight)

private class WidthInModifier(private val min: Int, private val max: Int) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        return constraints.copy(
            minWidth = maxOf(constraints.minWidth, min),
            maxWidth = minOf(constraints.maxWidth, max)
        )
    }

    override fun equals(other: Any?): Boolean =
        other is WidthInModifier && other.min == min && other.max == max
    override fun hashCode(): Int = 31 * min + max
    override fun toString(): String = "WidthIn($min..$max)"
}

private class HeightInModifier(private val min: Int, private val max: Int) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        return constraints.copy(
            minHeight = maxOf(constraints.minHeight, min),
            maxHeight = minOf(constraints.maxHeight, max)
        )
    }

    override fun equals(other: Any?): Boolean =
        other is HeightInModifier && other.min == min && other.max == max
    override fun hashCode(): Int = 31 * min + max
    override fun toString(): String = "HeightIn($min..$max)"
}

// ============================================================================
// Weight Modifier (for flex layouts)
// ============================================================================

/**
 * Weight for distributing remaining space in Row/Column layouts.
 *
 * Children with weight will share the remaining space proportionally.
 *
 * Example:
 * ```kotlin
 * Column {
 *     Text("Header", modifier = Modifier.height(3))
 *     Box(modifier = Modifier.weight(1f)) { /* Takes remaining space */ }
 *     Text("Footer", modifier = Modifier.height(2))
 * }
 * ```
 *
 * @param weight The weight factor (must be > 0).
 */
fun Modifier.weight(weight: Float): Modifier {
    require(weight > 0f) { "weight must be > 0, was $weight" }
    return then(WeightModifier(weight))
}

/**
 * Marker modifier for weighted flex children.
 */
data class WeightModifier(val weight: Float) : Modifier.Element {
    override fun toString(): String = "Weight($weight)"
}

// ============================================================================
// Default Size Modifier
// ============================================================================

/**
 * Set a default size that is used only if the incoming constraints are unbounded.
 *
 * @param width Default width if unbounded.
 * @param height Default height if unbounded.
 */
fun Modifier.defaultSize(width: Int? = null, height: Int? = null): Modifier {
    if (width == null && height == null) return this
    return then(DefaultSizeModifier(width, height))
}

private class DefaultSizeModifier(
    private val defaultWidth: Int?,
    private val defaultHeight: Int?,
) : SizeModifier {
    override fun modifyConstraints(constraints: Constraints): Constraints {
        var result = constraints

        if (defaultWidth != null && !constraints.hasBoundedWidth) {
            result = result.copy(minWidth = defaultWidth, maxWidth = defaultWidth)
        }

        if (defaultHeight != null && !constraints.hasBoundedHeight) {
            result = result.copy(minHeight = defaultHeight, maxHeight = defaultHeight)
        }

        return result
    }

    override fun equals(other: Any?): Boolean =
        other is DefaultSizeModifier && other.defaultWidth == defaultWidth && other.defaultHeight == defaultHeight
    override fun hashCode(): Int = 31 * (defaultWidth ?: 0) + (defaultHeight ?: 0)
    override fun toString(): String = "DefaultSize($defaultWidth, $defaultHeight)"
}

// ============================================================================
// Helper to apply size modifiers
// ============================================================================

/**
 * Apply all size modifiers in the chain to constraints.
 */
fun Modifier.applyToConstraints(constraints: Constraints): Constraints {
    return foldIn(constraints) { acc, element ->
        if (element is SizeModifier) {
            element.modifyConstraints(acc)
        } else {
            acc
        }
    }
}
