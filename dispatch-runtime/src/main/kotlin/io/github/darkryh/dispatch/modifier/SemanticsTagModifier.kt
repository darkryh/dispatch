package io.github.darkryh.dispatch.modifier

/**
 * Semantic tag for identifying layout nodes during testing or diagnostics.
 */
data class SemanticsTagModifier(
    val tag: String,
) : Modifier.Element

/**
 * Attach a semantic tag to a layout element.
 *
 * These tags are surfaced via [io.github.darkryh.dispatch.layout.LayoutNode.semanticsTags].
 */
fun Modifier.semantics(tag: String): Modifier = this then SemanticsTagModifier(tag)
