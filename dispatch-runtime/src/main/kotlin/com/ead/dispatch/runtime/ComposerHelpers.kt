package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Measurable

/**
 * Helper function to create a composable widget with standard Composer lifecycle.
 *
 * This reduces boilerplate in widget implementations by handling:
 * - Getting the current Composer
 * - Starting a node with the given name
 * - Registering the measurable
 * - Ending the node
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Button(text: String, modifier: Modifier = Modifier) = composableWidget("Button") {
 *     ButtonMeasurable(text, modifier)
 * }
 * ```
 *
 * @param name The node name for this widget (used for debugging/identification).
 * @param measurableFactory Factory function that creates the Measurable for this widget.
 */
@Dispatchable
inline fun <T : Measurable> composableWidget(
    name: String,
    measurableFactory: () -> T
) {
    val composer = Composer.current
    composer.startNode(name)
    composer.registerMeasurable(measurableFactory())
    composer.endNode()
}
