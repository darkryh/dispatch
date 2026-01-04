package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.URLEncoder

const val ROUTE_PAYLOAD_KEY = "__route"

val DefaultRouteJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal fun <T : Any> routeName(serializer: KSerializer<T>): String =
    serializer.descriptor.serialName

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> routeKey(): String = routeName(serializer())

inline fun <reified T : Any> routeFor(value: T, json: Json = DefaultRouteJson): String {
    val serializer = serializer<T>()
    val payload = json.encodeToString(serializer, value)
    val encoded = URLEncoder.encode(payload, Charsets.UTF_8)
    return "${routeName(serializer)}?$ROUTE_PAYLOAD_KEY=$encoded"
}

@OptIn(ExperimentalSerializationApi::class)
internal fun routeForValue(value: Any, json: Json = DefaultRouteJson): String {
    val serializer = serializer(value::class.java)
    val payload = json.encodeToString(serializer, value)
    val encoded = URLEncoder.encode(payload, Charsets.UTF_8)
    return "${routeName(serializer)}?$ROUTE_PAYLOAD_KEY=$encoded"
}

inline fun <reified T : Any> toRoute(arguments: Map<String, String>, json: Json = DefaultRouteJson): T? {
    val payload = arguments[ROUTE_PAYLOAD_KEY] ?: return null
    return decodeRoutePayload(serializer(), payload, json)
}

inline fun <reified T : Any> NavBackStackEntry.toRoute(json: Json = DefaultRouteJson): T? =
    toRoute(arguments, json)

inline fun <reified T : Any> NavController.navigate(
    route: T,
    json: Json = DefaultRouteJson,
) {
    navigate(routeFor(route, json))
}

inline fun <reified T : Any> NavController.navigate(
    route: T,
    navOptions: NavOptions,
    json: Json = DefaultRouteJson,
) {
    navigate(routeFor(route, json), navOptions)
}

inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Dispatchable (T) -> Unit,
) {
    val serializer = serializer<T>()
    val key = routeName(serializer)
    screen(key) { entry ->
        val payload = entry.arguments[ROUTE_PAYLOAD_KEY]
            ?: error("Missing route payload for $key")
        val args = decodeRoutePayload(serializer, payload)
        content(args)
    }
}

fun <T : Any> decodeRoutePayload(
    serializer: KSerializer<T>,
    payload: String,
    json: Json = DefaultRouteJson,
): T {
    return try {
        json.decodeFromString(serializer, payload)
    } catch (primary: SerializationException) {
        val routedPayload = """{"value":$payload}"""
        try {
            json.decodeFromString(serializer, routedPayload)
        } catch (_: SerializationException) {
            throw primary
        }
    }
}
