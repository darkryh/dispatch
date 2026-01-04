package com.ead.dispatch.runtime

/**
 * Registry for saving and restoring state.
 *
 * Used by [rememberSaveable] to persist state across navigation
 * and configuration changes.
 */
class SavedStateRegistry {
    /**
     * Saved state map.
     */
    private val savedState = mutableMapOf<String, Any?>()

    /**
     * Providers that can save state.
     */
    private val providers = mutableMapOf<String, () -> Any?>()

    /**
     * Get a saved value.
     */
    fun get(key: String): Any? = savedState[key]

    /**
     * Save a value directly.
     */
    fun set(key: String, value: Any?) {
        savedState[key] = value
    }

    /**
     * Register a provider that will be called when saving state.
     */
    fun registerProvider(key: String, provider: () -> Any?) {
        providers[key] = provider
    }

    /**
     * Unregister a provider.
     */
    fun unregisterProvider(key: String) {
        providers.remove(key)
    }

    /**
     * Perform save - call all providers and store their values.
     */
    fun performSave(): Map<String, Any?> {
        providers.forEach { (key, provider) ->
            savedState[key] = provider()
        }
        return savedState.toMap()
    }

    /**
     * Restore from a saved state map.
     */
    fun performRestore(state: Map<String, Any?>) {
        savedState.clear()
        savedState.putAll(state)
    }

    /**
     * Clear all saved state.
     */
    fun clear() {
        savedState.clear()
        providers.clear()
    }

    /**
     * Remove a saved value.
     */
    fun remove(key: String) {
        savedState.remove(key)
    }

    /**
     * Check if a key exists.
     */
    fun contains(key: String): Boolean = key in savedState

    /**
     * Get all saved keys.
     */
    fun keys(): Set<String> = savedState.keys.toSet()
}

/**
 * Handle for accessing saved state in ViewModels and compositions.
 */
class SavedStateHandle(
    private val registry: SavedStateRegistry = SavedStateRegistry()
) {
    /**
     * Get a value from saved state.
     */
    operator fun <T> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return registry.get(key) as? T
    }

    /**
     * Set a value in saved state.
     */
    operator fun set(key: String, value: Any?) {
        registry.set(key, value)
    }

    /**
     * Check if a key exists.
     */
    fun contains(key: String): Boolean = registry.contains(key)

    /**
     * Get a value or compute and store it.
     */
    inline fun <T> getOrPut(key: String, defaultValue: () -> T): T {
        val existing = get<T>(key)
        return if (existing != null) {
            existing
        } else {
            val value = defaultValue()
            set(key, value)
            value
        }
    }

    /**
     * Remove a value.
     */
    fun remove(key: String) {
        registry.remove(key)
    }

    /**
     * Get all keys.
     */
    fun keys(): Set<String> = registry.keys()

    /**
     * Get the underlying registry.
     */
    internal fun getRegistry(): SavedStateRegistry = registry
}
