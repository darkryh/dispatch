package com.ead.dispatch.modifier

/**
 * Marks a layout node as focusable.
 *
 * @param token Stable identity for focus tracking. If null, the owning [com.ead.dispatch.layout.LayoutNode]
 * will be used as the focus token.
 */
data class FocusTargetModifier(val token: Any?) : Modifier.Element

/**
 * Make a layout element focusable.
 *
 * @param key Stable identity for focus tracking. If null, the owning [com.ead.dispatch.layout.LayoutNode]
 * will be used as the focus token. Focus order is computed when [com.ead.dispatch.runtime.FocusRegistry.sync]
 * walks the layout node tree after composition.
 */
fun Modifier.focusable(key: Any? = null): Modifier = this then FocusTargetModifier(key)
