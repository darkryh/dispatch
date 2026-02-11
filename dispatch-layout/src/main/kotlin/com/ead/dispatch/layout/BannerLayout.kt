package com.ead.dispatch.layout

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints

/**
 * A three-section layout with fixed header and footer, and flexible body.
 *
 * This is the standard layout for terminal applications:
 * - Header: Fixed at top (e.g., title bar, breadcrumbs)
 * - Body: Fills remaining space (scrollable content)
 * - Footer: Fixed at bottom (e.g., status bar, input area)
 *
 * Example:
 * ```kotlin
 * BannerLayout(
 *     modifier = Modifier.fillMaxSize(),
 *     header = { Text("My App v1.0") },
 *     footer = { Text("Press 'q' to quit") },
 * ) {
 *     // Body content
 *     ScrollableList(items)
 * }
 * ```
 *
 * @param modifier Modifiers to apply to this layout.
 * @param header Optional header content (fixed height).
 * @param footer Optional footer content (fixed height).
 * @param body Main body content (takes remaining space).
 */
@Dispatchable
fun BannerLayout(
    modifier: Modifier = Modifier,
    header: (@Dispatchable () -> Unit)? = null,
    footer: (@Dispatchable () -> Unit)? = null,
    body: @Dispatchable () -> Unit,
) {
    Layout(
        modifier = modifier,
        measurePolicy = BannerLayoutMeasurePolicy(),
        content = {
            // Emit sections in order: header, body, footer
            // We use marker wrappers to identify sections
            if (header != null) {
                BannerSection(BannerSectionType.Header) {
                    header()
                }
            }

            BannerSection(BannerSectionType.Body) {
                body()
            }

            if (footer != null) {
                BannerSection(BannerSectionType.Footer) {
                    footer()
                }
            }
        },
    )
}

/**
 * Internal wrapper for banner sections.
 */
@Dispatchable
private fun BannerSection(
    type: BannerSectionType,
    content: @Dispatchable () -> Unit,
) {
    Layout(
        modifier = BannerSectionModifier(type),
        measurePolicy = SingleChildMeasurePolicy(),
        content = content,
    )
}

/**
 * Type of banner section.
 */
internal enum class BannerSectionType {
    Header,
    Body,
    Footer,
}

/**
 * Marker modifier for banner sections.
 */
internal data class BannerSectionModifier(
    val type: BannerSectionType,
) : Modifier.Element

/**
 * Simple measure policy that measures a single child or returns empty.
 */
internal class SingleChildMeasurePolicy : MeasurePolicy {
    override fun measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return MeasureResult(
                width = constraints.minWidth,
                height = constraints.minHeight,
            )
        }

        val placeable = measurables.first().measure(constraints)

        return MeasureResult(
            width = placeable.width,
            height = placeable.height,
        ) {
            placeable.placeAt(0, 0)
        }
    }
}

/**
 * Measure policy for BannerLayout.
 */
internal class BannerLayoutMeasurePolicy : MeasurePolicy {
    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    override fun measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return MeasureResult(
                width = constraints.minWidth,
                height = constraints.minHeight,
            )
        }

        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                0
            }

        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                Int.MAX_VALUE
            }

        // Separate sections by type
        var headerMeasurable: Measurable? = null
        var bodyMeasurable: Measurable? = null
        var footerMeasurable: Measurable? = null

        for (measurable in measurables) {
            val sectionType = measurable.modifier.firstOrNull(BannerSectionModifier::class.java)?.type
            when (sectionType) {
                BannerSectionType.Header -> headerMeasurable = measurable
                BannerSectionType.Body -> bodyMeasurable = measurable
                BannerSectionType.Footer -> footerMeasurable = measurable
                null -> bodyMeasurable = measurable // Fallback
            }
        }

        // Measure header and footer first (fixed height)
        val headerConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = layoutWidth,
                minHeight = 0,
                maxHeight = layoutHeight,
            )

        val headerPlaceable =
            headerMeasurable?.let { measurable ->
                val modified = measurable.modifier.applyToConstraints(headerConstraints)
                measurable.measure(modified)
            }

        val footerPlaceable =
            footerMeasurable?.let { measurable ->
                val modified = measurable.modifier.applyToConstraints(headerConstraints)
                measurable.measure(modified)
            }

        // Calculate remaining height for body
        val headerHeight = headerPlaceable?.height ?: 0
        val footerHeight = footerPlaceable?.height ?: 0
        val bodyHeight = (layoutHeight - headerHeight - footerHeight).coerceAtLeast(0)

        // Measure body with remaining space
        val bodyConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = layoutWidth,
                minHeight = bodyHeight,
                maxHeight = bodyHeight,
            )

        val bodyPlaceable =
            bodyMeasurable?.let { measurable ->
                val modified = measurable.modifier.applyToConstraints(bodyConstraints)
                measurable.measure(modified)
            }

        // Calculate total height
        val totalHeight = headerHeight + (bodyPlaceable?.height ?: bodyHeight) + footerHeight

        return MeasureResult(
            width = layoutWidth,
            height = totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            var y = 0

            // Place header at top
            headerPlaceable?.let { placeable ->
                placeable.placeAt(0, y)
                y += placeable.height
            }

            // Place body in middle
            bodyPlaceable?.let { placeable ->
                placeable.placeAt(0, y)
                y += placeable.height
            }

            // Place footer at bottom
            footerPlaceable?.let { placeable ->
                val footerY = layoutHeight - placeable.height
                placeable.placeAt(0, footerY.coerceAtLeast(y))
            }
        }
    }
}

/**
 * A simpler version with just header and body.
 */
@Dispatchable
fun HeaderLayout(
    modifier: Modifier = Modifier,
    header: @Dispatchable () -> Unit,
    body: @Dispatchable () -> Unit,
) {
    BannerLayout(
        modifier = modifier,
        header = header,
        footer = null,
        body = body,
    )
}

/**
 * A simpler version with just body and footer.
 */
@Dispatchable
fun FooterLayout(
    modifier: Modifier = Modifier,
    footer: @Dispatchable () -> Unit,
    body: @Dispatchable () -> Unit,
) {
    BannerLayout(
        modifier = modifier,
        header = null,
        footer = footer,
        body = body,
    )
}
