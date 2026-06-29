package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.height
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.DispatchConfig
import com.ead.dispatch.runtime.DispatchScope
import com.ead.dispatch.runtime.FocusRegistry
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalDispatchScope
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.withComposer
import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetMemoryPerfTest {
    /**
     * Inspects a real [KeyboardInterceptor] via reflection (the class is final, so it cannot be
     * subclassed) to expose cumulative registration counts and the live interceptor count.
     *
     * - `nextOrder` is incremented once per [KeyboardInterceptor.register] -> cumulative registers.
     * - the private `interceptors` list size -> currently live interceptors.
     * - disposes = registers - live.
     */
    private class InterceptorInspector(
        val target: KeyboardInterceptor,
    ) {
        private val orderField =
            KeyboardInterceptor::class.java.getDeclaredField("nextOrder").apply { isAccessible = true }
        private val listField =
            KeyboardInterceptor::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

        val registerCount: AtomicInteger
            get() = AtomicInteger((orderField.getLong(target)).toInt())

        val liveCount: Int
            get() = (listField.get(target) as Collection<*>).size

        val disposeCount: AtomicInteger
            get() = AtomicInteger(registerCount.get() - liveCount)
    }

    private class InputHarness(
        private val width: Int = 80,
        private val realInterceptor: KeyboardInterceptor = KeyboardInterceptor(),
    ) {
        val keyboardInterceptor = InterceptorInspector(realInterceptor)
        private val interceptor = realInterceptor
        private val terminal =
            Terminal(ansiLevel = AnsiLevel.NONE, width = width, height = 20, interactive = false)
        private val focusRegistry = FocusRegistry()
        private val dispatchScope = TestDispatchScope(terminal, interceptor)
        private val composer = Composer()

        fun render(content: @Composable () -> Unit): List<String> {
            withComposer(composer) {
                composer.startComposition()
                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides terminal.size.width,
                    LocalTerminalHeight provides terminal.size.height,
                    LocalKeyboardInterceptor provides interceptor,
                    LocalFocusRegistry provides focusRegistry,
                    LocalDispatchScope provides dispatchScope,
                    LocalTheme provides DispatchTheme.Dark,
                ) {
                    content()
                }
                composer.endComposition()
            }
            val rootNode = composer.getRootNode() ?: return emptyList()
            focusRegistry.sync(rootNode)
            return rootNode.measure(Constraints.fixedWidth(width)).lines
        }

        fun press(key: String) {
            dispatchScope.sendKey(KeyboardEvent(key))
        }
    }

    private class TestDispatchScope(
        override val terminal: Terminal,
        private val keyboardInterceptor: KeyboardInterceptor,
        override val args: Array<String> = emptyArray(),
    ) : DispatchScope {
        private var keyHandler: ((KeyboardEvent) -> Unit)? = null
        override val theme: DispatchTheme = DispatchTheme.Dark
        override val terminalWidth: Int get() = terminal.size.width
        override val terminalHeight: Int get() = terminal.size.height

        override fun config(block: DispatchConfig.() -> Unit) = Unit
        override fun exit(code: Int) = Unit
        override fun hasFlag(name: String): Boolean = false
        override fun getArgument(name: String): String? = null
        override fun launch(block: suspend CoroutineScope.() -> Unit): Job = Job()
        override fun clearScreen(clearScrollback: Boolean) = Unit
        override fun onKeyEvent(handler: (KeyboardEvent) -> Unit) {
            keyHandler = handler
        }

        override fun onMouseEvent(handler: (MouseEvent) -> Unit) = Unit
        override fun content(block: @Composable () -> Unit) = Unit

        fun sendKey(event: KeyboardEvent) {
            if (keyboardInterceptor.tryIntercept(event)) return
            keyHandler?.invoke(event)
        }
    }

    @Test
    fun `interceptor not re-registered when historyItems identity changes`() {
        val harness = InputHarness()
        var value = ""

        @Composable
        fun screen() {
            TextField(
                value = value,
                onValueChange = { value = it },
                icon = "> ",
                // Fresh list identity each composition.
                historyItems = listOf("a").toList(),
            )
        }

        repeat(10) { harness.render { screen() } }

        assertEquals(
            1,
            harness.keyboardInterceptor.registerCount.get(),
            "interceptor should register exactly once despite fresh historyItems each frame",
        )
    }

    @Test
    fun `interceptor disposed exactly once across remount`() {
        val harness = InputHarness()
        var enabled = false
        var present = true

        @Composable
        fun screen() {
            if (present) {
                TextField(
                    value = "",
                    onValueChange = {},
                    icon = "> ",
                    enabled = enabled,
                )
            }
        }

        // disabled: no registration yet
        harness.render { screen() }
        // enable -> registers once
        enabled = true
        harness.render { screen() }
        // remove from composition -> disposes
        present = false
        harness.render { screen() }

        val i = harness.keyboardInterceptor
        assertEquals(i.registerCount.get(), i.disposeCount.get(), "register/dispose must balance")
        assertEquals(0, i.liveCount, "no live interceptors should remain after remount")
    }

    @Test
    fun `dictation burst keeps single registration and full value`() {
        val harness = InputHarness()
        var value = ""

        @Composable
        fun screen() {
            TextField(
                value = value,
                onValueChange = { value = it },
                icon = "> ",
                historyItems = listOf("prev").toList(),
            )
        }

        harness.render { screen() }
        repeat(200) {
            harness.press("a")
            harness.render { screen() }
            // The memory-leak guard: every register must be balanced by a dispose, so there is
            // always exactly ONE live handler -- the burst must never accumulate stale handlers.
            // (Cumulative registration may settle/transition a few times due to timing-dependent
            // paste/dictation detection; what must NOT happen is per-keystroke growth or leaks.)
            assertEquals(
                1,
                harness.keyboardInterceptor.liveCount,
                "exactly one live interceptor (no leaked/stale handlers) during a burst",
            )
        }

        // Registration count must stay bounded, NOT scale with the 200 keystrokes (the bug the
        // historyItems-key fix addresses would push this toward ~200).
        assertTrue(
            harness.keyboardInterceptor.registerCount.get() <= 5,
            "interceptor registrations must stay bounded across a burst, not grow per keystroke " +
                "(was ${harness.keyboardInterceptor.registerCount.get()})",
        )
        assertEquals(200, value.length, "all 200 characters should be captured")
    }

    @Test
    fun `LazyColumn composes only windowed items`() {
        val composed = AtomicInteger(0)
        renderLines(width = 40, height = 10) {
            LazyColumn {
                items((0 until 1_000).toList(), key = { it }) { index ->
                    composed.incrementAndGet()
                    Text("Item $index")
                }
            }
        }

        val count = composed.get()
        assertTrue(count in 1..40, "expected a bounded viewport window, composed $count of 1000")
    }

    @Test
    fun `LazyColumn height cache releases removed-item state`() {
        val terminal =
            Terminal(ansiLevel = AnsiLevel.NONE, width = 40, height = 10, interactive = false)
        val composer = Composer()
        val focusRegistry = FocusRegistry()
        val scrollState = ScrollState()
        val keys = androidx.compose.runtime.mutableStateOf((0 until 100).toList())

        fun render() {
            withComposer(composer) {
                composer.startComposition()
                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides 40,
                    LocalTerminalHeight provides 10,
                    LocalKeyboardInterceptor provides KeyboardInterceptor(),
                    LocalFocusRegistry provides focusRegistry,
                ) {
                    LazyColumn(state = scrollState) {
                        items(keys.value, key = { it }) { index -> Text("Item $index") }
                    }
                }
                composer.endComposition()
            }
            val rootNode = composer.getRootNode() ?: return
            focusRegistry.sync(rootNode)
            rootNode.measure(Constraints.maxSize(40, 10)).lines
        }

        render()
        scrollState.scrollBy(50)
        render()
        // Replace the key set entirely; old keys must be pruned from the height cache.
        keys.value = (50 until 150).toList()
        render()

        val cacheSize = LazyColumnTestHooks.lastHeightCacheSize
        assertTrue(
            cacheSize <= 100,
            "height cache must track current item count (<=100), not cumulative keys; was $cacheSize",
        )
    }

    @Test
    fun `snapshot stable across no-op key presses`() {
        val harness = InputHarness()

        @Composable
        fun screen() {
            Panel { Text("x") }
        }

        val first = harness.render { screen() }
        repeat(8) {
            harness.press("F1") // no-op for a static Panel
            val next = harness.render { screen() }
            assertEquals(first, next, "rendered lines must be byte-identical across recompositions")
        }
    }

    @Test
    fun `ScrollState maxOffset reflects content shrink`() {
        val terminal =
            Terminal(ansiLevel = AnsiLevel.NONE, width = 40, height = 5, interactive = false)
        val composer = Composer()
        val focusRegistry = FocusRegistry()
        val scrollState = ScrollState()
        val count = androidx.compose.runtime.mutableStateOf(40)

        fun render(): List<String> {
            withComposer(composer) {
                composer.startComposition()
                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides 40,
                    LocalTerminalHeight provides 5,
                    LocalKeyboardInterceptor provides KeyboardInterceptor(),
                    LocalFocusRegistry provides focusRegistry,
                ) {
                    ScrollableList(
                        items = (0 until count.value).map { "Row $it" },
                        modifier = Modifier.height(5),
                        scrollState = scrollState,
                    ) { item -> Text(item) }
                }
                composer.endComposition()
            }
            val rootNode = composer.getRootNode() ?: return emptyList()
            focusRegistry.sync(rootNode)
            return rootNode.measure(Constraints.maxSize(40, 5)).lines
        }

        render()
        scrollState.scrollToBottom()
        render()
        // Shrink the content well below the previous offset.
        count.value = 3
        val lines = render()

        // When content shrinks below the viewport, maxOffset becomes 0 and the offset must clamp
        // to it -- otherwise the viewport stays scrolled past the (now shorter) content and renders
        // nothing but blank rows. (Blank rows BELOW the 3 visible items are expected and fine.)
        assertEquals(0, scrollState.maxOffset, "maxOffset must be 0 when content fits the viewport")
        assertEquals(0, scrollState.offset, "offset must clamp to maxOffset (0) after content shrinks")
        assertTrue(
            lines.any { it.contains("Row 0") },
            "content must render from the top after shrink, not be scrolled past the end",
        )
    }
}
