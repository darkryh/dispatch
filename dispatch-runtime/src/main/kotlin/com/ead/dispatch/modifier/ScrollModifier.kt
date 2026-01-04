package com.ead.dispatch.modifier

/**
 * Modifier that enables scrolling for content that exceeds available space.
 */
interface ScrollModifier : Modifier.Element {
    /**
     * Whether horizontal scrolling is enabled.
     */
    val horizontalScrollEnabled: Boolean

    /**
     * Whether vertical scrolling is enabled.
     */
    val verticalScrollEnabled: Boolean
}

/**
 * Enable vertical scrolling for content that exceeds available height.
 *
 * @param enabled Whether scrolling is enabled.
 */
fun Modifier.verticalScroll(enabled: Boolean = true): Modifier {
    return then(ScrollModifierImpl(horizontalScrollEnabled = false, verticalScrollEnabled = enabled))
}

/**
 * Enable horizontal scrolling for content that exceeds available width.
 *
 * @param enabled Whether scrolling is enabled.
 */
fun Modifier.horizontalScroll(enabled: Boolean = true): Modifier {
    return then(ScrollModifierImpl(horizontalScrollEnabled = enabled, verticalScrollEnabled = false))
}

/**
 * Enable both horizontal and vertical scrolling.
 *
 * Alias for common use case.
 */
fun Modifier.scrollable(
    horizontal: Boolean = false,
    vertical: Boolean = true,
): Modifier {
    return then(ScrollModifierImpl(horizontalScrollEnabled = horizontal, verticalScrollEnabled = vertical))
}

private data class ScrollModifierImpl(
    override val horizontalScrollEnabled: Boolean,
    override val verticalScrollEnabled: Boolean,
) : ScrollModifier {
    override fun toString(): String = "Scroll(h=$horizontalScrollEnabled, v=$verticalScrollEnabled)"
}

/**
 * Check if the modifier chain has scrolling enabled.
 */
fun Modifier.isScrollable(): Boolean {
    return any { it is ScrollModifier && (it.horizontalScrollEnabled || it.verticalScrollEnabled) }
}

/**
 * Get scroll configuration from modifier chain.
 */
fun Modifier.getScrollConfig(): ScrollConfig {
    var horizontal = false
    var vertical = false

    foldIn(Unit) { _, element ->
        if (element is ScrollModifier) {
            horizontal = horizontal || element.horizontalScrollEnabled
            vertical = vertical || element.verticalScrollEnabled
        }
    }

    return ScrollConfig(horizontal, vertical)
}

/**
 * Scroll configuration.
 */
data class ScrollConfig(
    val horizontal: Boolean,
    val vertical: Boolean,
) {
    val isScrollable: Boolean get() = horizontal || vertical

    companion object {
        val None = ScrollConfig(false, false)
        val Vertical = ScrollConfig(false, true)
        val Horizontal = ScrollConfig(true, false)
        val Both = ScrollConfig(true, true)
    }
}
