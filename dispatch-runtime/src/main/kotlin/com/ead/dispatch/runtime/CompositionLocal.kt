package com.ead.dispatch.runtime

/**
 * A composition-scoped value that can be provided and consumed in the composition tree.
 *
 * CompositionLocals allow passing data down through the composition tree implicitly,
 * without having to pass it as parameters through every composable function.
 *
 * Example:
 * ```kotlin
 * // Define a CompositionLocal
 * val LocalTheme = compositionLocalOf { Theme.Dark }
 *
 * // Provide a value
 * @Dispatchable
 * fun App() {
 *     CompositionLocalProvider(LocalTheme provides Theme.Light) {
 *         Content()
 *     }
 * }
 *
 * // Consume the value
 * @Dispatchable
 * fun Content() {
 *     val theme = LocalTheme.current
 *     Text("Using theme: ${theme.name}")
 * }
 * ```
 */
class CompositionLocal<T> internal constructor(
    private val defaultFactory: () -> T
) {
    /**
     * Get the current value of this CompositionLocal.
     *
     * Returns the value provided by the nearest ancestor [CompositionLocalProvider],
     * or the default value if none is provided.
     */
    val current: T
        get() {
            val hasValue = CompositionLocalContext.contains(this)
            if (!hasValue) {
                return defaultFactory()
            }
            val provider = CompositionLocalContext.get(this)
            @Suppress("UNCHECKED_CAST")
            return provider as T
        }

    /**
     * Create a [ProvidedValue] to use with [CompositionLocalProvider].
     */
    infix fun provides(value: T): ProvidedValue<T> = ProvidedValue(this, value)

    /**
     * Create a [ProvidedValue] with a lazy provider.
     */
    infix fun providesDefault(value: () -> T): ProvidedValue<T> =
        ProvidedValue(this, value())
}

/**
 * A value provided by a [CompositionLocal].
 */
data class ProvidedValue<T>(
    val compositionLocal: CompositionLocal<T>,
    val value: T
)

/**
 * Create a [CompositionLocal] with a default value factory.
 *
 * @param defaultFactory Factory function to create the default value.
 * @return A new CompositionLocal.
 */
fun <T> compositionLocalOf(defaultFactory: () -> T): CompositionLocal<T> =
    CompositionLocal(defaultFactory)

/**
 * Create a [CompositionLocal] with a static default value.
 *
 * Use this for values that don't change between compositions.
 *
 * @param defaultFactory Factory function to create the default value.
 * @return A new CompositionLocal.
 */
fun <T> staticCompositionLocalOf(defaultFactory: () -> T): CompositionLocal<T> =
    CompositionLocal(defaultFactory)

/**
 * Internal context for managing CompositionLocal values.
 */
internal object CompositionLocalContext {
    private val threadLocalMap = ThreadLocal<MutableMap<CompositionLocal<*>, Any?>>()

    private fun getMap(): MutableMap<CompositionLocal<*>, Any?> =
        threadLocalMap.get() ?: mutableMapOf<CompositionLocal<*>, Any?>().also {
            threadLocalMap.set(it)
        }

    fun get(key: CompositionLocal<*>): Any? = getMap()[key]

    fun set(key: CompositionLocal<*>, value: Any?) {
        getMap()[key] = value
    }

    fun contains(key: CompositionLocal<*>): Boolean = getMap().containsKey(key)

    fun remove(key: CompositionLocal<*>) {
        getMap().remove(key)
    }

    fun clear() {
        threadLocalMap.remove()
    }
}

/**
 * Provides [CompositionLocal] values to the composition tree.
 *
 * Example:
 * ```kotlin
 * CompositionLocalProvider(
 *     LocalTheme provides Theme.Light,
 *     LocalUser provides currentUser
 * ) {
 *     // Content can access LocalTheme.current and LocalUser.current
 *     MyContent()
 * }
 * ```
 */
object CompositionLocalProvider {
    /**
     * Provide values and execute content.
     */
    operator fun invoke(
        vararg values: ProvidedValue<*>,
        content: () -> Unit
    ) {
        val previousValues = mutableMapOf<CompositionLocal<*>, PreviousValue>()

        // Save previous values and set new ones
        for (provided in values) {
            previousValues[provided.compositionLocal] = PreviousValue(
                hasValue = CompositionLocalContext.contains(provided.compositionLocal),
                value = CompositionLocalContext.get(provided.compositionLocal),
            )
            CompositionLocalContext.set(provided.compositionLocal, provided.value)
        }

        try {
            content()
        } finally {
            // Restore previous values
            for (provided in values) {
                val previous = previousValues[provided.compositionLocal]
                if (previous != null && previous.hasValue) {
                    CompositionLocalContext.set(provided.compositionLocal, previous.value)
                } else {
                    CompositionLocalContext.remove(provided.compositionLocal)
                }
            }
        }
    }

    private data class PreviousValue(
        val hasValue: Boolean,
        val value: Any?,
    )
}
