package com.ead.dispatch.runtime

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.state.Saver
import com.ead.dispatch.state.autoSaver
import java.util.concurrent.atomic.AtomicInteger

/**
 * The composition runtime that manages state and recomposition.
 *
 * Each composition has its own Composer instance that tracks:
 * - Remembered values
 * - Saveable state
 * - Current position in the composition tree
 */
class Composer(
    private val savedStateRegistry: SavedStateRegistry? = null
) {
    /**
     * Storage for remembered values, keyed by slot index.
     */
    private val slots = mutableMapOf<Int, SlotEntry>()

    /**
     * Current slot index during composition.
     */
    private var currentSlot = AtomicInteger(0)

    /**
     * Stack of group markers for tracking nested compositions.
     */
    private val groupStack = mutableListOf<Int>()

    /**
     * Whether we're currently composing.
     */
    @Volatile
    internal var isComposing: Boolean = false
        private set

    /**
     * Start a new composition or recomposition.
     */
    fun startComposition() {
        isComposing = true
        currentSlot.set(0)
        groupStack.clear()
        nodeStack.clear()
        rootNode = null
    }

    /**
     * End the current composition.
     */
    fun endComposition() {
        isComposing = false
        // Clean up any slots that weren't visited (removed content)
        val visitedSlots = currentSlot.get()
        val removedSlots = slots.keys.filter { it >= visitedSlots }
        for (slot in removedSlots) {
            val entry = slots.remove(slot)
            disposeSlot(entry)
        }
    }

    /**
     * Start a group (for tracking nested content).
     */
    fun startGroup(key: Any?) {
        groupStack.add(currentSlot.get())
    }

    /**
     * End the current group.
     */
    fun endGroup() {
        if (groupStack.isNotEmpty()) {
            groupStack.removeLast()
        }
    }

    /**
     * Remember a value at the current slot.
     *
     * If the slot already has a value, return it.
     * Otherwise, compute the value and store it.
     */
    fun <T> remember(calculation: () -> T): T {
        val slot = nextSlot()
        val existing = slots[slot]

        return if (existing != null && existing.key == null) {
            @Suppress("UNCHECKED_CAST")
            existing.value as T
        } else {
            if (existing != null) {
                disposeSlot(existing)
            }
            val value = calculation()
            slots[slot] = SlotEntry(key = null, value = value)
            value
        }
    }

    /**
     * Remember a value that depends on a key.
     *
     * If the key hasn't changed, return the cached value.
     * Otherwise, recompute and store the new value.
     */
    fun <T> remember(key: Any?, calculation: () -> T): T {
        val slot = nextSlot()
        val existing = slots[slot]

        return if (existing != null && existing.key == key) {
            @Suppress("UNCHECKED_CAST")
            existing.value as T
        } else {
            if (existing != null) {
                disposeSlot(existing)
            }
            val value = calculation()
            slots[slot] = SlotEntry(key = key, value = value)
            value
        }
    }

    /**
     * Remember a saveable value.
     *
     * Values are saved to the registry and restored on navigation.
     */
    fun <T> rememberSaveable(calculation: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return rememberSaveableWithSaver(autoSaver<Any>() as Saver<T, Any>, calculation)
    }

    /**
     * Remember a saveable value with a custom saver.
     */
    fun <T> rememberSaveableWithSaver(
        saver: Saver<T, Any>,
        calculation: () -> T
    ): T {
        val slot = nextSlot()
        val slotKey = "saveable_$slot"

        // Try to restore from saved state
        val restored = savedStateRegistry?.get(slotKey)?.let { saved ->
            @Suppress("UNCHECKED_CAST")
            saver.restore(saved)
        }

        val value = restored ?: run {
            val existing = slots[slot]
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                existing.value as T
            } else {
                calculation()
            }
        }

        slots[slot] = SlotEntry(key = slotKey, value = value)

        // Register for saving
        savedStateRegistry?.registerProvider(slotKey) {
            saver.save(value)
        }

        return value
    }

    /**
     * Get the next slot index.
     */
    private fun nextSlot(): Int = currentSlot.getAndIncrement()

    private fun disposeSlot(entry: SlotEntry?) {
        val value = entry?.value ?: return
        when (value) {
            is DisposableEffectState -> value.dispose()
            is LaunchedEffectState -> value.dispose()
        }
    }

    /**
     * Get a unique key for the current composition position.
     */
    fun currentPositionKey(): Int = currentSlot.get()

    /**
     * Clear remembered slots from a starting index.
     *
     * Useful when changing keyed subtrees (e.g., navigation) to avoid
     * reusing incompatible remembered values across different content.
     */
    fun clearSlotsFrom(startIndex: Int) {
        clearSlotsInRange(startIndex, Int.MAX_VALUE)
    }

    /**
     * Clear remembered slots within a specific range.
     *
     * This is safer when only a subtree changes and we want to avoid
     * clearing slots used by siblings composed after the subtree.
     */
    fun clearSlotsInRange(startIndex: Int, endIndexExclusive: Int) {
        if (endIndexExclusive <= startIndex) return
        val removedSlots = slots.keys.filter { it >= startIndex && it < endIndexExclusive }
        for (slot in removedSlots) {
            val entry = slots.remove(slot)
            disposeSlot(entry)
        }
    }

    // ========================================================================
    // Layout Support
    // ========================================================================

    /**
     * Stack of constraints from parent layouts.
     */
    private val constraintStack = mutableListOf<Constraints>()

    /**
     * Stack of layout nodes for building the composition tree.
     */
    private val nodeStack = mutableListOf<LayoutNode>()

    /**
     * Root layout node for the current composition.
     */
    private var rootNode: LayoutNode? = null

    /**
     * Start a layout node.
     *
     * Every composable widget/layout should call this before emitting children.
     * The resulting node becomes the parent for any nodes started during [content].
     */
    fun startNode(name: String): LayoutNode {
        val node = LayoutNode(name)
        nodeStack.add(node)
        return node
    }

    /**
     * End a layout node.
     *
     * This attaches the current node to its parent or marks it as the root.
     */
    fun endNode() {
        if (nodeStack.isNotEmpty()) {
            val node = nodeStack.removeLast()
            val parent = nodeStack.lastOrNull()
            if (parent != null) {
                parent.addChild(node)
            } else {
                rootNode = node
            }
        }
    }

    /**
     * Get the current constraints from parent.
     */
    fun getCurrentConstraints(): Constraints =
        constraintStack.lastOrNull() ?: Constraints.Unbounded

    /**
     * Push constraints onto the stack.
     */
    fun pushConstraints(constraints: Constraints) {
        constraintStack.add(constraints)
    }

    /**
     * Pop constraints from the stack.
     */
    fun popConstraints(): Constraints? =
        if (constraintStack.isNotEmpty()) constraintStack.removeLast() else null

    /**
     * Register a measurable with the current node.
     *
     * This binds the measured/rendered behavior to the layout node created
     * by [startNode], enabling node-based traversal (layout, focus, semantics).
     */
    fun registerMeasurable(measurable: Measurable) {
        val node = nodeStack.lastOrNull()
        if (node != null) {
            node.setDelegate(measurable)
        } else {
            val root = LayoutNode(measurable::class.java.simpleName).apply {
                setDelegate(measurable)
            }
            rootNode = root
        }
    }

    /**
     * Get the root layout node for the current composition.
     *
     * Call after [endComposition] to access the composed layout tree.
     */
    fun getRootNode(): LayoutNode? = rootNode

    /**
     * Get the current layout node (top of the node stack).
     */
    fun currentNode(): LayoutNode? = nodeStack.lastOrNull()

    companion object {
        /**
         * Get the current composer for this thread.
         *
         * @throws IllegalStateException if not currently composing.
         */
        val current: Composer
            get() = threadLocalComposer.get()
                ?: throw IllegalStateException("Not currently composing. Remember can only be called during composition.")
    }
}

/**
 * Entry in the slot table.
 */
internal data class SlotEntry(
    val key: Any?,
    val value: Any?
)

/**
 * Thread-local composer instance.
 */
private val threadLocalComposer = ThreadLocal<Composer>()

/**
 * Get the current composer for this thread.
 *
 * @throws IllegalStateException if not currently composing.
 */
val currentComposer: Composer
    get() = threadLocalComposer.get()
        ?: throw IllegalStateException("Not currently composing. Remember can only be called during composition.")

/**
 * Returns true if the current thread is inside a composition.
 */
fun isInComposition(): Boolean = threadLocalComposer.get()?.isComposing == true

/**
 * Set the composer for the current thread.
 */
internal fun setCurrentComposer(composer: Composer?) {
    if (composer != null) {
        threadLocalComposer.set(composer)
    } else {
        threadLocalComposer.remove()
    }
}

/**
 * Execute a block with the given composer as the current composer.
 */
fun <T> withComposer(composer: Composer, block: () -> T): T {
    val previous = threadLocalComposer.get()
    try {
        threadLocalComposer.set(composer)
        return block()
    } finally {
        if (previous != null) {
            threadLocalComposer.set(previous)
        } else {
            threadLocalComposer.remove()
        }
    }
}
