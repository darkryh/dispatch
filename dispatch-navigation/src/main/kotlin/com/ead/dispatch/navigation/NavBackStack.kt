package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.state.Saver
import com.ead.dispatch.state.SnapshotStateList
import com.ead.dispatch.state.mutableStateListOf
import com.ead.dispatch.state.rememberSaveable
import kotlinx.serialization.json.Json

/**
 * Marker interface for navigation keys.
 *
 * Keys should be @Serializable to support save/restore.
 */
interface NavKey

/**
 * A mutable back stack of NavKeys that integrates with Dispatch state.
 */
class NavBackStack<T : NavKey> internal constructor(
    internal val base: SnapshotStateList<T>,
) : MutableList<T> by base {
    constructor() : this(base = mutableStateListOf())
    constructor(vararg elements: T) : this(base = mutableStateListOf(*elements))
}

/**
 * Remember a NavBackStack that can be saved and restored.
 */
@Dispatchable
fun rememberNavBackStack(
    vararg elements: NavKey,
    json: Json = DefaultRouteJson,
): NavBackStack<NavKey> {
    val saver = navBackStackSaver(json)
    return rememberSaveable(saver) {
        NavBackStack<NavKey>().apply { addAll(elements) }
    }
}

private fun navBackStackSaver(json: Json): Saver<NavBackStack<NavKey>, Any> =
    object : Saver<NavBackStack<NavKey>, Any> {
        override fun save(value: NavBackStack<NavKey>): Any = value.map { encodeNavKeyForSave(it, json) }

        override fun restore(value: Any): NavBackStack<NavKey>? {
            val encoded = value as? List<*> ?: return null
            val entries = encoded.filterIsInstance<String>()
            if (entries.size != encoded.size) return null
            return NavBackStack<NavKey>().apply {
                addAll(entries.map { decodeNavKeyFromSave(it, json) })
            }
        }
    }
