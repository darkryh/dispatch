package com.ead.dispatch.state

import com.ead.dispatch.runtime.Recomposer

/**
 * A [MutableList] that triggers recomposition when modified.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun MessageList() {
 *     val messages = remember { mutableStateListOf<Message>() }
 *
 *     messages.add(Message("Hello"))  // Triggers recomposition
 *
 *     Column {
 *         messages.forEach { message ->
 *             Text(message.text)
 *         }
 *     }
 * }
 * ```
 */
@Suppress("TooManyFunctions", "ClassOrdering")
class SnapshotStateList<T> : MutableList<T>, DerivedStateDependency {
    private val backing = mutableListOf<T>()
    private val readers = mutableSetOf<Any>()
    private val dependents = mutableSetOf<DerivedState<*>>()

    private fun recordRead() {
        Recomposer.currentScope?.let { scope ->
            readers.add(scope)
        }
        DerivedStateObserver.recordDependency(this)
    }

    private fun notifyChanged() {
        dependents.forEach { it.invalidate() }
        readers.forEach { scope ->
            Recomposer.invalidateScope(scope)
        }
    }

    override val size: Int
        get() {
            recordRead()
            return backing.size
        }

    override fun contains(element: T): Boolean {
        recordRead()
        return backing.contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        recordRead()
        return backing.containsAll(elements)
    }

    override fun get(index: Int): T {
        recordRead()
        return backing[index]
    }

    override fun indexOf(element: T): Int {
        recordRead()
        return backing.indexOf(element)
    }

    override fun isEmpty(): Boolean {
        recordRead()
        return backing.isEmpty()
    }

    override fun iterator(): MutableIterator<T> {
        recordRead()
        return SnapshotIterator(backing.iterator())
    }

    override fun lastIndexOf(element: T): Int {
        recordRead()
        return backing.lastIndexOf(element)
    }

    override fun add(element: T): Boolean {
        val result = backing.add(element)
        if (result) notifyChanged()
        return result
    }

    override fun add(index: Int, element: T) {
        backing.add(index, element)
        notifyChanged()
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        val result = backing.addAll(index, elements)
        if (result) notifyChanged()
        return result
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val result = backing.addAll(elements)
        if (result) notifyChanged()
        return result
    }

    override fun clear() {
        if (backing.isNotEmpty()) {
            backing.clear()
            notifyChanged()
        }
    }

    override fun listIterator(): MutableListIterator<T> {
        recordRead()
        return SnapshotListIterator(backing.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        recordRead()
        return SnapshotListIterator(backing.listIterator(index))
    }

    override fun remove(element: T): Boolean {
        val result = backing.remove(element)
        if (result) notifyChanged()
        return result
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val result = backing.removeAll(elements)
        if (result) notifyChanged()
        return result
    }

    override fun removeAt(index: Int): T {
        val result = backing.removeAt(index)
        notifyChanged()
        return result
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val result = backing.retainAll(elements)
        if (result) notifyChanged()
        return result
    }

    override fun set(index: Int, element: T): T {
        val result = backing.set(index, element)
        notifyChanged()
        return result
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        recordRead()
        return SnapshotSubList(backing.subList(fromIndex, toIndex))
    }

    override fun addDependent(dependent: Any) {
        val derived = dependent as? DerivedState<*> ?: return
        dependents.add(derived)
    }

    override fun removeDependent(dependent: Any) {
        val derived = dependent as? DerivedState<*> ?: return
        dependents.remove(derived)
    }

    private inner class SnapshotIterator(
        private val delegate: MutableIterator<T>
    ) : MutableIterator<T> {
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): T = delegate.next()
        override fun remove() {
            delegate.remove()
            notifyChanged()
        }
    }

    private inner class SnapshotListIterator(
        private val delegate: MutableListIterator<T>
    ) : MutableListIterator<T> {
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun hasPrevious(): Boolean = delegate.hasPrevious()
        override fun next(): T = delegate.next()
        override fun nextIndex(): Int = delegate.nextIndex()
        override fun previous(): T = delegate.previous()
        override fun previousIndex(): Int = delegate.previousIndex()
        override fun add(element: T) {
            delegate.add(element)
            notifyChanged()
        }
        override fun remove() {
            delegate.remove()
            notifyChanged()
        }
        override fun set(element: T) {
            delegate.set(element)
            notifyChanged()
        }
    }

    @Suppress("TooManyFunctions")
    private inner class SnapshotSubList(
        private val delegate: MutableList<T>
    ) : MutableList<T> {
        override val size: Int
            get() {
                recordRead()
                return delegate.size
            }

        override fun contains(element: T): Boolean {
            recordRead()
            return delegate.contains(element)
        }

        override fun containsAll(elements: Collection<T>): Boolean {
            recordRead()
            return delegate.containsAll(elements)
        }

        override fun get(index: Int): T {
            recordRead()
            return delegate[index]
        }

        override fun indexOf(element: T): Int {
            recordRead()
            return delegate.indexOf(element)
        }

        override fun isEmpty(): Boolean {
            recordRead()
            return delegate.isEmpty()
        }

        override fun iterator(): MutableIterator<T> {
            recordRead()
            return SnapshotIterator(delegate.iterator())
        }

        override fun lastIndexOf(element: T): Int {
            recordRead()
            return delegate.lastIndexOf(element)
        }

        override fun add(element: T): Boolean {
            val result = delegate.add(element)
            if (result) notifyChanged()
            return result
        }

        override fun add(index: Int, element: T) {
            delegate.add(index, element)
            notifyChanged()
        }

        override fun addAll(index: Int, elements: Collection<T>): Boolean {
            val result = delegate.addAll(index, elements)
            if (result) notifyChanged()
            return result
        }

        override fun addAll(elements: Collection<T>): Boolean {
            val result = delegate.addAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun clear() {
            if (delegate.isNotEmpty()) {
                delegate.clear()
                notifyChanged()
            }
        }

        override fun listIterator(): MutableListIterator<T> {
            recordRead()
            return SnapshotListIterator(delegate.listIterator())
        }

        override fun listIterator(index: Int): MutableListIterator<T> {
            recordRead()
            return SnapshotListIterator(delegate.listIterator(index))
        }

        override fun remove(element: T): Boolean {
            val result = delegate.remove(element)
            if (result) notifyChanged()
            return result
        }

        override fun removeAll(elements: Collection<T>): Boolean {
            val result = delegate.removeAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun removeAt(index: Int): T {
            val result = delegate.removeAt(index)
            notifyChanged()
            return result
        }

        override fun retainAll(elements: Collection<T>): Boolean {
            val result = delegate.retainAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun set(index: Int, element: T): T {
            val result = delegate.set(index, element)
            notifyChanged()
            return result
        }

        override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
            recordRead()
            return SnapshotSubList(delegate.subList(fromIndex, toIndex))
        }
    }
}

/**
 * Create a [SnapshotStateList] with initial elements.
 */
fun <T> mutableStateListOf(vararg elements: T): SnapshotStateList<T> =
    SnapshotStateList<T>().apply { addAll(elements) }

/**
 * Create an empty [SnapshotStateList].
 */
fun <T> mutableStateListOf(): SnapshotStateList<T> = SnapshotStateList()

/**
 * A [MutableMap] that triggers recomposition when modified.
 */
@Suppress("ClassOrdering")
class SnapshotStateMap<K, V> : MutableMap<K, V>, DerivedStateDependency {
    private val backing = mutableMapOf<K, V>()
    private val readers = mutableSetOf<Any>()
    private val dependents = mutableSetOf<DerivedState<*>>()

    private fun recordRead() {
        Recomposer.currentScope?.let { scope ->
            readers.add(scope)
        }
        DerivedStateObserver.recordDependency(this)
    }

    private fun notifyChanged() {
        dependents.forEach { it.invalidate() }
        readers.forEach { scope ->
            Recomposer.invalidateScope(scope)
        }
    }

    override val size: Int
        get() {
            recordRead()
            return backing.size
        }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            recordRead()
            return SnapshotEntrySet()
        }

    override val keys: MutableSet<K>
        get() {
            recordRead()
            return SnapshotKeySet()
        }

    override val values: MutableCollection<V>
        get() {
            recordRead()
            return SnapshotValues()
        }

    override fun containsKey(key: K): Boolean {
        recordRead()
        return backing.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        recordRead()
        return backing.containsValue(value)
    }

    override fun get(key: K): V? {
        recordRead()
        return backing[key]
    }

    override fun isEmpty(): Boolean {
        recordRead()
        return backing.isEmpty()
    }

    override fun clear() {
        if (backing.isNotEmpty()) {
            backing.clear()
            notifyChanged()
        }
    }

    override fun put(key: K, value: V): V? {
        val result = backing.put(key, value)
        notifyChanged()
        return result
    }

    override fun putAll(from: Map<out K, V>) {
        backing.putAll(from)
        notifyChanged()
    }

    override fun remove(key: K): V? {
        val result = backing.remove(key)
        if (result != null) notifyChanged()
        return result
    }

    override fun addDependent(dependent: Any) {
        val derived = dependent as? DerivedState<*> ?: return
        dependents.add(derived)
    }

    override fun removeDependent(dependent: Any) {
        val derived = dependent as? DerivedState<*> ?: return
        dependents.remove(derived)
    }

    private inner class SnapshotEntrySet : MutableSet<MutableMap.MutableEntry<K, V>> {
        override val size: Int
            get() {
                recordRead()
                return backing.entries.size
            }

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            recordRead()
            return backing.entries.contains(element)
        }

        override fun containsAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
            recordRead()
            return backing.entries.containsAll(elements)
        }

        override fun isEmpty(): Boolean {
            recordRead()
            return backing.entries.isEmpty()
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            recordRead()
            return SnapshotEntryIterator(backing.entries.iterator())
        }

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            val result = backing.entries.add(element)
            if (result) notifyChanged()
            return result
        }

        override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
            val result = backing.entries.addAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun clear() {
            if (backing.isNotEmpty()) {
                backing.entries.clear()
                notifyChanged()
            }
        }

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            val result = backing.entries.remove(element)
            if (result) notifyChanged()
            return result
        }

        override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
            val result = backing.entries.removeAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
            val result = backing.entries.retainAll(elements)
            if (result) notifyChanged()
            return result
        }
    }

    private inner class SnapshotEntryIterator(
        private val delegate: MutableIterator<MutableMap.MutableEntry<K, V>>
    ) : MutableIterator<MutableMap.MutableEntry<K, V>> {
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): MutableMap.MutableEntry<K, V> = SnapshotEntry(delegate.next())
        override fun remove() {
            delegate.remove()
            notifyChanged()
        }
    }

    private inner class SnapshotEntry(
        private val delegate: MutableMap.MutableEntry<K, V>
    ) : MutableMap.MutableEntry<K, V> {
        override val key: K
            get() = delegate.key
        override val value: V
            get() = delegate.value

        override fun setValue(newValue: V): V {
            val result = delegate.setValue(newValue)
            notifyChanged()
            return result
        }

        override fun equals(other: Any?): Boolean = delegate == other
        override fun hashCode(): Int = delegate.hashCode()
        override fun toString(): String = delegate.toString()
    }

    private inner class SnapshotKeySet : MutableSet<K> {
        override val size: Int
            get() {
                recordRead()
                return backing.keys.size
            }

        override fun contains(element: K): Boolean {
            recordRead()
            return backing.keys.contains(element)
        }

        override fun containsAll(elements: Collection<K>): Boolean {
            recordRead()
            return backing.keys.containsAll(elements)
        }

        override fun isEmpty(): Boolean {
            recordRead()
            return backing.keys.isEmpty()
        }

        override fun iterator(): MutableIterator<K> {
            recordRead()
            return SnapshotKeyIterator(backing.keys.iterator())
        }

        override fun add(element: K): Boolean {
            val result = backing.keys.add(element)
            if (result) notifyChanged()
            return result
        }

        override fun addAll(elements: Collection<K>): Boolean {
            val result = backing.keys.addAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun clear() {
            if (backing.isNotEmpty()) {
                backing.keys.clear()
                notifyChanged()
            }
        }

        override fun remove(element: K): Boolean {
            val result = backing.keys.remove(element)
            if (result) notifyChanged()
            return result
        }

        override fun removeAll(elements: Collection<K>): Boolean {
            val result = backing.keys.removeAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun retainAll(elements: Collection<K>): Boolean {
            val result = backing.keys.retainAll(elements)
            if (result) notifyChanged()
            return result
        }
    }

    private inner class SnapshotKeyIterator(
        private val delegate: MutableIterator<K>
    ) : MutableIterator<K> {
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): K = delegate.next()
        override fun remove() {
            delegate.remove()
            notifyChanged()
        }
    }

    private inner class SnapshotValues : MutableCollection<V> {
        override val size: Int
            get() {
                recordRead()
                return backing.values.size
            }

        override fun contains(element: V): Boolean {
            recordRead()
            return backing.values.contains(element)
        }

        override fun containsAll(elements: Collection<V>): Boolean {
            recordRead()
            return backing.values.containsAll(elements)
        }

        override fun isEmpty(): Boolean {
            recordRead()
            return backing.values.isEmpty()
        }

        override fun iterator(): MutableIterator<V> {
            recordRead()
            return SnapshotValueIterator(backing.values.iterator())
        }

        override fun add(element: V): Boolean {
            val result = backing.values.add(element)
            if (result) notifyChanged()
            return result
        }

        override fun addAll(elements: Collection<V>): Boolean {
            val result = backing.values.addAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun clear() {
            if (backing.isNotEmpty()) {
                backing.values.clear()
                notifyChanged()
            }
        }

        override fun remove(element: V): Boolean {
            val result = backing.values.remove(element)
            if (result) notifyChanged()
            return result
        }

        override fun removeAll(elements: Collection<V>): Boolean {
            val result = backing.values.removeAll(elements)
            if (result) notifyChanged()
            return result
        }

        override fun retainAll(elements: Collection<V>): Boolean {
            val result = backing.values.retainAll(elements)
            if (result) notifyChanged()
            return result
        }
    }

    private inner class SnapshotValueIterator(
        private val delegate: MutableIterator<V>
    ) : MutableIterator<V> {
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): V = delegate.next()
        override fun remove() {
            delegate.remove()
            notifyChanged()
        }
    }
}

/**
 * Create a [SnapshotStateMap] with initial entries.
 */
fun <K, V> mutableStateMapOf(vararg pairs: Pair<K, V>): SnapshotStateMap<K, V> =
    SnapshotStateMap<K, V>().apply { putAll(pairs) }

/**
 * Create an empty [SnapshotStateMap].
 */
fun <K, V> mutableStateMapOf(): SnapshotStateMap<K, V> = SnapshotStateMap()
