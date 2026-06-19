package com.ead.dispatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
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
@Composable
fun rememberNavBackStack(
    vararg elements: NavKey,
    json: Json = DefaultRouteJson,
): NavBackStack<NavKey> {
    val saver = navBackStackSaver(json)
    return rememberSaveable(saver = saver) {
        NavBackStack<NavKey>().apply { addAll(elements) }
    }
}

private fun navBackStackSaver(json: Json): Saver<NavBackStack<NavKey>, Any> =
    Saver(
        save = { value -> value.map { encodeNavKeyForSave(it, json) } },
        restore = { value ->
            val encoded = value as? List<*> ?: return@Saver null
            val entries = encoded.filterIsInstance<String>()
            if (entries.size != encoded.size) return@Saver null
            NavBackStack<NavKey>().apply {
                addAll(entries.map { decodeNavKeyFromSave(it, json) })
            }
        },
    )
