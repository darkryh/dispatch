package com.ead.dispatch.benchmarks

import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.focusable
import com.ead.dispatch.runtime.FocusRegistry
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole

/**
 * Benchmarks `FocusRegistry.sync` over a static layout tree.
 *
 * `sync` reuses its `order` list and `seenScratch` HashSet and walks each node's modifier chain with
 * `foldIn`. After one priming sync (so `focusedToken` is set and the lists are warm), a steady-state
 * re-sync of an unchanged tree should allocate ~0 bytes regardless of `nodes` — verify with
 * `-prof gc` (`gc.alloc.rate.norm` ~ 0). About one third of the nodes carry `Modifier.focusable()`.
 *
 * `FocusRegistry` and `LayoutNode` are public, so no reflection is needed. A node only carries a
 * modifier when given a delegate, so each node is wired with a [FixedMeasurable] whose modifier is
 * either `Modifier.focusable()` (every third node) or the empty `Modifier`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class FocusSyncBenchmark {

    @Param("64", "512", "4096")
    var nodes: Int = 0

    @Param("2", "8")
    var branching: Int = 0

    private var registry: FocusRegistry = FocusRegistry()
    private var root: LayoutNode = LayoutNode("root")

    @Setup(Level.Trial)
    fun setUp() {
        root = buildTree(nodes, branching)
        registry = FocusRegistry()
        // Prime: establishes focusedToken and warms order/seenScratch so the measured syncs allocate 0.
        registry.sync(root)
    }

    @Benchmark
    fun syncSteadyState(bh: Blackhole) {
        registry.sync(root)
        // root carries focusable() (index 0 -> every-third rule), so it is a valid focus token.
        bh.consume(registry.isFocused(root))
    }

    private fun buildTree(total: Int, branching: Int): LayoutNode {
        val focusableModifier: Modifier = Modifier.focusable()
        val emptyModifier: Modifier = Modifier
        var created = 0

        fun newNode(): LayoutNode {
            val node = LayoutNode("n$created")
            val modifier = if (created % 3 == 0) focusableModifier else emptyModifier
            node.setDelegate(FixedMeasurable(1, 1, modifier))
            created++
            return node
        }

        val rootNode = newNode()
        val queue = ArrayDeque<LayoutNode>()
        queue.add(rootNode)
        while (created < total && queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            var child = 0
            while (child < branching && created < total) {
                val node = newNode()
                parent.addChild(node)
                queue.add(node)
                child++
            }
        }
        return rootNode
    }
}
