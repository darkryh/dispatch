package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.json.Json

/**
 * Marker interface for navigation keys.
 *
 * Keys should be @Serializable to support save/restore.
 */
interface NavKey

/**
 * A mutable back stack of NavKeys that integrates with Dispatch state.
 *
 * Prefer the [navigate] and [popBackStack] extensions for mutation: they preserve
 * the "keep at least one entry" guard and trigger deterministic entry disposal.
 *
 * This type delegates the full [MutableList] surface for save/restore and
 * snapshot interop. Calling raw mutators such as `clear()` or `removeAt(0)`
 * bypasses [popBackStack]'s guard and disposal hook and can leave NavDisplay with
 * an empty or inconsistent stack — avoid them.
 */
class NavBackStack<T : NavKey> internal constructor(
    internal val base: SnapshotStateList<T>,
) : MutableList<T> by base {
    constructor() : this(base = mutableStateListOf())
    constructor(vararg elements: T) : this(base = mutableStateListOf(*elements))

    /**
     * Hook invoked by [popBackStack] after an entry is removed, with the set of
     * content keys that remain in the stack. NavDisplay installs this so popped
     * entries are disposed deterministically rather than relying solely on the
     * recomposition diff. Null when no NavDisplay is currently hosting the stack.
     */
    internal var onEntriesRemoved: ((remainingContentKeys: Set<Any>) -> Unit)? = null
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
