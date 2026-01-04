package com.ead.dispatch.navigation

import com.ead.dispatch.runtime.SavedStateHandle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Read a string argument from a SavedStateHandle.
 */
fun SavedStateHandle.getStringArgument(key: String): String? {
    val value = this.get<Any?>(key)
    return when (value) {
        null -> null
        is String -> value
        else -> value.toString()
    }
}

/**
 * Read a string argument or throw if missing.
 */
fun SavedStateHandle.requireStringArgument(key: String): String {
    return getStringArgument(key)
        ?: throw IllegalArgumentException("Missing required argument: $key")
}

/**
 * Read an int argument from a SavedStateHandle.
 */
fun SavedStateHandle.getIntArgument(key: String): Int? {
    val value = this.get<Any?>(key)
    return when (value) {
        is Int -> value
        is String -> value.toIntOrNull()
        else -> null
    }
}

/**
 * Read an int argument or throw if missing or invalid.
 */
fun SavedStateHandle.requireIntArgument(key: String): Int {
    return getIntArgument(key)
        ?: throw IllegalArgumentException("Missing or invalid int argument: $key")
}

/**
 * Read a long argument from a SavedStateHandle.
 */
fun SavedStateHandle.getLongArgument(key: String): Long? {
    val value = this.get<Any?>(key)
    return when (value) {
        is Long -> value
        is Int -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

/**
 * Read a long argument or throw if missing or invalid.
 */
fun SavedStateHandle.requireLongArgument(key: String): Long {
    return getLongArgument(key)
        ?: throw IllegalArgumentException("Missing or invalid long argument: $key")
}

/**
 * Read a boolean argument from a SavedStateHandle.
 */
fun SavedStateHandle.getBooleanArgument(key: String): Boolean? {
    val value = this.get<Any?>(key)
    return when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }
}

/**
 * Read a boolean argument or throw if missing or invalid.
 */
fun SavedStateHandle.requireBooleanArgument(key: String): Boolean {
    return getBooleanArgument(key)
        ?: throw IllegalArgumentException("Missing or invalid boolean argument: $key")
}

/**
 * Decode a typed route payload stored in the SavedStateHandle.
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> SavedStateHandle.toRoute(
    json: Json = DefaultRouteJson,
): T {
    val payload = getStringArgument(ROUTE_PAYLOAD_KEY)
        ?: throw IllegalArgumentException("Missing route payload for ${routeKey<T>()}")
    return decodeRoutePayload(serializer(), payload, json)
}

/**
 * Decode a typed route payload or throw if missing.
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> SavedStateHandle.requireRoute(
    json: Json = DefaultRouteJson,
): T = toRoute(json)
