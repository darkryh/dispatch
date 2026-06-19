package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.SemanticsTagModifier
import com.ead.dispatch.modifier.allOf

/**
 * A node in the layout tree that delegates measurement to a [Measurable].
 */
class LayoutNode(
    val name: String,
) : Measurable {
    private var delegate: Measurable? = null
    private val _children = mutableListOf<LayoutNode>()

    var parent: LayoutNode? = null
        private set

    override var modifier: Modifier = Modifier
        private set

    val children: List<LayoutNode>
        get() = _children.toList()

    /**
     * Semantic tags attached via [com.ead.dispatch.modifier.semantics].
     */
    val semanticsTags: List<String>
        get() = modifier.allOf<SemanticsTagModifier>().map { it.tag }

    fun addChild(child: LayoutNode) {
        child.parent = this
        _children.add(child)
    }

    fun insertChild(index: Int, child: LayoutNode) {
        child.parent = this
        _children.add(index, child)
    }

    fun removeChildren(index: Int, count: Int) {
        repeat(count) {
            _children.removeAt(index).parent = null
        }
    }

    fun moveChildren(from: Int, to: Int, count: Int) {
        if (count == 0 || from == to) return
        val moving = _children.subList(from, from + count).toList()
        repeat(count) { _children.removeAt(from) }
        val destination = if (to > from) to - count else to
        _children.addAll(destination, moving)
    }

    fun clearChildren() {
        _children.forEach { it.parent = null }
        _children.clear()
    }

    fun setDelegate(measurable: Measurable) {
        delegate = measurable
        modifier = measurable.modifier
    }

    override fun measure(constraints: Constraints): Placeable {
        val current = delegate ?: return SimplePlaceable.Empty
        return current.measure(constraints)
    }

    override fun toString(): String = "LayoutNode(name=$name, children=${_children.size})"
}
