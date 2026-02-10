package com.ead.dispatch.navigation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal fun <T : Any> routeName(serializer: KSerializer<T>): String = serializer.descriptor.serialName

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> routeKey(): String = routeName(serializer())

fun <T : Any> decodeRoutePayload(
    serializer: KSerializer<T>,
    payload: String,
    json: Json = DefaultRouteJson,
): T =
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

inline fun <reified T : Any> NavBackStackEntry<*>.toRoute(json: Json = DefaultRouteJson): T? {
    val payload = savedStateHandle.getStringArgument(ROUTE_PAYLOAD_KEY) ?: return null
    return decodeRoutePayload(serializer(), payload, json)
}
