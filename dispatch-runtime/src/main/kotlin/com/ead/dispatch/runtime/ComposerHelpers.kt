package com.ead.dispatch.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.modifier.Modifier

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
 * @Composable
 * fun Button(text: String, modifier: Modifier = Modifier) = composableWidget("Button") {
 *     ButtonMeasurable(text, modifier)
 * }
 * ```
 *
 * @param name The node name for this widget (used for debugging/identification).
 * @param measurableFactory Factory function that creates the Measurable for this widget.
 */
@Composable
fun <T : Measurable> composableWidget(
    name: String,
    measurableFactory: () -> T
) {
    ComposeNode<com.ead.dispatch.layout.LayoutNode, DispatchNodeApplier>(
        factory = { com.ead.dispatch.layout.LayoutNode(name) },
        update = {
            set(measurableFactory()) { setDelegate(it) }
        },
    )
}

@Composable
fun composableContainer(
    name: String,
    modifier: Modifier,
    measurableFactory: (List<Measurable>) -> Measurable,
    content: @Composable () -> Unit,
) {
    ComposeNode<LayoutNode, DispatchNodeApplier>(
        factory = { LayoutNode(name) },
        update = {
            // Two independent set() calls instead of `set(modifier to measurableFactory)` avoid
            // allocating a Pair every recomposition. Each block rebuilds from the latest captured
            // modifier AND factory (closure params), so neither half can go stale.
            set(modifier) {
                setDelegate(
                    DynamicChildrenMeasurable(
                        modifier = modifier,
                        children = { children },
                        measurableFactory = measurableFactory,
                    )
                )
            }
            set(measurableFactory) {
                setDelegate(
                    DynamicChildrenMeasurable(
                        modifier = modifier,
                        children = { children },
                        measurableFactory = measurableFactory,
                    )
                )
            }
        },
        content = content,
    )
}

private class DynamicChildrenMeasurable(
    override val modifier: Modifier,
    private val children: () -> List<Measurable>,
    private val measurableFactory: (List<Measurable>) -> Measurable,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable =
        measurableFactory(children()).measure(constraints)
}
