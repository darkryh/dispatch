package com.ead.dispatch.navigation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.Base64

const val ROUTE_PAYLOAD_KEY = "__route"

val DefaultRouteJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal fun <T : Any> serializerFor(value: T): KSerializer<T> =
    @Suppress("UNCHECKED_CAST")
    (serializer(value::class.java) as KSerializer<T>)

@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal fun serializerForClass(className: String): KSerializer<Any> {
    val clazz = Class.forName(className)
    @Suppress("UNCHECKED_CAST")
    return serializer(clazz) as KSerializer<Any>
}

internal fun encodeNavKeyPayload(
    key: NavKey,
    json: Json = DefaultRouteJson,
): String {
    val serializer = serializerFor(key)
    return json.encodeToString(serializer, key)
}

internal fun decodeNavKeyPayload(
    serializer: KSerializer<Any>,
    payload: String,
    json: Json = DefaultRouteJson,
): Any =
    try {
        json.decodeFromString(serializer, payload)
    } catch (primary: SerializationException) {
        val routedPayload = """{"value":$payload}"""
        try {
            json.decodeFromString(serializer, routedPayload)
        } catch (_: SerializationException) {
            throw primary
        }
    }

internal fun encodeNavKeyForSave(
    key: NavKey,
    json: Json = DefaultRouteJson,
): String {
    val payload = encodeNavKeyPayload(key, json)
    val encoded = Base64.getUrlEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
    return "${key::class.java.name}|$encoded"
}

internal fun decodeNavKeyFromSave(
    encoded: String,
    json: Json = DefaultRouteJson,
): NavKey {
    val parts = encoded.split("|", limit = 2)
    require(parts.size == 2) { "Invalid nav key encoding: $encoded" }
    val className = parts[0]
    val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    val serializer = serializerForClass(className)
    @Suppress("UNCHECKED_CAST")
    return decodeNavKeyPayload(serializer, payload, json) as NavKey
}
