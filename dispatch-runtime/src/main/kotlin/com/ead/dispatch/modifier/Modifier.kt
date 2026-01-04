package com.ead.dispatch.modifier

/**
 * An immutable chain of modifiers for layout elements.
 *
 * Modifiers describe how an element should be sized, padded, bordered, etc.
 * They are applied in order, with each modifier wrapping the previous.
 *
 * Example:
 * ```kotlin
 * Text(
 *     "Hello",
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .padding(horizontal = 2)
 *         .border(BorderStyle.Rounded)
 * )
 * ```
 *
 * Modifiers are immutable and can be reused across multiple compositions.
 */
interface Modifier {
    /**
     * Accumulates all modifier elements in this chain from outside to inside.
     *
     * @param initial The initial accumulator value.
     * @param operation The operation to apply to each element.
     * @return The final accumulated value.
     */
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

    /**
     * Accumulates all modifier elements in this chain from inside to outside.
     *
     * @param initial The initial accumulator value.
     * @param operation The operation to apply to each element.
     * @return The final accumulated value.
     */
    fun <R> foldOut(initial: R, operation: (Element, R) -> R): R

    /**
     * Returns true if any element matches the predicate.
     */
    fun any(predicate: (Element) -> Boolean): Boolean

    /**
     * Returns all elements of the given type.
     */
    fun <T : Element> allOf(type: Class<T>): List<T>

    /**
     * Returns the first element of the given type, or null.
     */
    fun <T : Element> firstOrNull(type: Class<T>): T? = allOf(type).firstOrNull()

    /**
     * Concatenates another modifier to this one.
     *
     * The [other] modifier will be applied after this one.
     */
    infix fun then(other: Modifier): Modifier

    /**
     * A single element in the modifier chain.
     */
    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R =
            operation(initial, this)

        override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R =
            operation(this, initial)

        override fun any(predicate: (Element) -> Boolean): Boolean = predicate(this)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Element> allOf(type: Class<T>): List<T> =
            if (type.isInstance(this)) listOf(this as T) else emptyList()

        override infix fun then(other: Modifier): Modifier =
            if (other === Companion) this else CombinedModifier(this, other)
    }

    /**
     * The empty modifier - identity element for [then].
     */
    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R = initial
        override fun any(predicate: (Element) -> Boolean): Boolean = false
        override fun <T : Element> allOf(type: Class<T>): List<T> = emptyList()
        override infix fun then(other: Modifier): Modifier = other
        override fun toString(): String = "Modifier"
    }
}

/**
 * Combines two modifiers into a chain.
 */
internal class CombinedModifier(
    private val outer: Modifier,
    private val inner: Modifier,
) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R =
        outer.foldOut(inner.foldOut(initial, operation), operation)

    override fun any(predicate: (Modifier.Element) -> Boolean): Boolean =
        outer.any(predicate) || inner.any(predicate)

    override fun <T : Modifier.Element> allOf(type: Class<T>): List<T> =
        outer.allOf(type) + inner.allOf(type)

    override fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CombinedModifier) return false
        return outer == other.outer && inner == other.inner
    }

    override fun hashCode(): Int = 31 * outer.hashCode() + inner.hashCode()

    override fun toString(): String = "[$outer, $inner]"
}

/**
 * Inline extension to get all elements of a specific type.
 */
inline fun <reified T : Modifier.Element> Modifier.allOf(): List<T> = allOf(T::class.java)

/**
 * Inline extension to get the first element of a specific type.
 */
inline fun <reified T : Modifier.Element> Modifier.firstOrNull(): T? = firstOrNull(T::class.java)
