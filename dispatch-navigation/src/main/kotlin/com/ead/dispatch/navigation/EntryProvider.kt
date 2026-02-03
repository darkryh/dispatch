package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import kotlin.jvm.JvmSuppressWildcards
import kotlin.reflect.KClass

@DslMarker
annotation class EntryDsl

/**
 * Provides a scope to build an entryProvider that returns NavEntry definitions.
 */
fun <T : NavKey> entryProvider(
    fallback: (unknownScreen: T) -> NavEntry<T> = {
        throw IllegalStateException("Unknown screen $it")
    },
    builder: EntryProviderScope<T>.() -> Unit,
): (T) -> NavEntry<T> = EntryProviderScope(fallback).apply(builder).build()

@EntryDsl
class EntryProviderScope<T : NavKey>(
    private val fallback: (unknownScreen: T) -> NavEntry<T>,
) {
    private val clazzProviders = mutableMapOf<KClass<out T>, EntryClassProvider<out T>>()
    private val providers = mutableMapOf<Any, EntryProvider<out T>>()

    fun <K : T> addEntryProvider(
        key: K,
        contentKey: Any = defaultContentKey(key),
        metadata: Map<String, Any> = emptyMap(),
        content: @Dispatchable (K) -> Unit,
    ) {
        require(key !in providers) {
            "An `entry` with the key `key` has already been added: ${key}."
        }
        providers[key] = EntryProvider(key, contentKey, { metadata }, content)
    }

    fun <K : T> addEntryProvider(
        key: K,
        @Suppress("KotlinDefaultParameterOrder") contentKey: Any = defaultContentKey(key),
        metadata: (K) -> Map<String, Any>,
        content: @Dispatchable (K) -> Unit,
    ) {
        require(key !in providers) {
            "An `entry` with the key `key` has already been added: ${key}."
        }
        providers[key] = EntryProvider(key, contentKey, metadata, content)
    }

    fun <K : T> entry(
        key: K,
        contentKey: Any = defaultContentKey(key),
        metadata: Map<String, Any> = emptyMap(),
        content: @Dispatchable (K) -> Unit,
    ) {
        addEntryProvider(key, contentKey, { metadata }, content)
    }

    fun <K : T> entry(
        key: K,
        @Suppress("KotlinDefaultParameterOrder") contentKey: Any = defaultContentKey(key),
        metadata: (K) -> Map<String, Any>,
        content: @Dispatchable (K) -> Unit,
    ) {
        addEntryProvider(key, contentKey, metadata, content)
    }

    fun <K : T> addEntryProvider(
        clazz: KClass<out K>,
        clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
        metadata: Map<String, Any> = emptyMap(),
        content: @Dispatchable (K) -> Unit,
    ) {
        require(clazz !in clazzProviders) {
            "An `entry` with the same `clazz` has already been added: ${clazz.simpleName}."
        }
        clazzProviders[clazz] = EntryClassProvider(clazz, clazzContentKey, { metadata }, content)
    }

    fun <K : T> addEntryProvider(
        clazz: KClass<out K>,
        @Suppress("KotlinDefaultParameterOrder")
        clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
        metadata: (K) -> Map<String, Any>,
        content: @Dispatchable (K) -> Unit,
    ) {
        require(clazz !in clazzProviders) {
            "An `entry` with the same `clazz` has already been added: ${clazz.simpleName}."
        }
        clazzProviders[clazz] = EntryClassProvider(clazz, clazzContentKey, metadata, content)
    }

    inline fun <reified K : T> entry(
        noinline clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
        metadata: Map<String, Any> = emptyMap(),
        noinline content: @Dispatchable (K) -> Unit,
    ) {
        addEntryProvider(K::class, clazzContentKey, { metadata }, content)
    }

    inline fun <reified K : T> entry(
        @Suppress("KotlinDefaultParameterOrder")
        noinline clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
        noinline metadata: (K) -> Map<String, Any>,
        noinline content: @Dispatchable (K) -> Unit,
    ) {
        addEntryProvider(K::class, clazzContentKey, metadata, content)
    }

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun build(): (T) -> NavEntry<T> = { key ->
        val entryClassProvider = clazzProviders[key::class] as? EntryClassProvider<T>
        val entryProvider = providers[key] as? EntryProvider<T>
        entryClassProvider?.run {
            NavEntry(key, clazzContentKey(key), metadata(key), content)
        }
            ?: entryProvider?.run { NavEntry(key, contentKey, metadata(key), content) }
            ?: fallback.invoke(key)
    }
}

@Suppress("DataClassDefinition")
private data class EntryClassProvider<K : NavKey>(
    val clazz: KClass<K>,
    val clazzContentKey: (key: K) -> Any,
    val metadata: (K) -> Map<String, Any>,
    val content: @Dispatchable (K) -> Unit,
)

@Suppress("DataClassDefinition")
private data class EntryProvider<K : NavKey>(
    val key: K,
    val contentKey: Any,
    val metadata: (K) -> Map<String, Any>,
    val content: @Dispatchable (K) -> Unit,
)
