package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.DispatchConfig
import com.ead.dispatch.runtime.DispatchScope
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalDispatchScope
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.withComposer
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertTrue

class StructuralStabilityTest {
    private class TestDispatchScope(
        override val terminal: Terminal,
        override val theme: DispatchTheme,
        private val keyboardInterceptor: KeyboardInterceptor,
        override val args: Array<String> = emptyArray(),
    ) : DispatchScope {
        private var keyHandler: ((KeyboardEvent) -> Unit)? = null

        override val terminalWidth: Int get() = terminal.size.width
        override val terminalHeight: Int get() = terminal.size.height

        override fun config(block: DispatchConfig.() -> Unit) {
            DispatchConfig().block()
        }

        override fun exit(code: Int) = Unit
        override fun hasFlag(name: String): Boolean = false
        override fun getArgument(name: String): String? = null
        override fun launch(block: suspend CoroutineScope.() -> Unit): Job = Job()
        override fun clearScreen(clearScrollback: Boolean) = Unit
        override fun onMouseEvent(handler: (MouseEvent) -> Unit) = Unit
        override fun renderer(block: @Dispatchable () -> Unit) = Unit

        override fun onKeyEvent(handler: (KeyboardEvent) -> Unit) {
            keyHandler = handler
        }

        fun sendKey(event: KeyboardEvent) {
            if (keyboardInterceptor.tryIntercept(event)) return
            keyHandler?.invoke(event)
        }
    }

    private class Harness {
        private val terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 80,
            height = 20,
            interactive = false,
        )
        private val keyboardInterceptor = KeyboardInterceptor()
        private val focusRegistry = com.ead.dispatch.runtime.FocusRegistry()
        private val dispatchScope = TestDispatchScope(terminal, DispatchTheme.Dark, keyboardInterceptor)
        private val composer = Composer()

        var inputValue: String = ""
            private set
        var showDecision: Boolean = false
        var showStatus: Boolean = false

        fun render() {
            withComposer(composer) {
                composer.startComposition()
                CompositionLocalProvider(
                    LocalTerminal provides terminal,
                    LocalTerminalWidth provides terminal.size.width,
                    LocalTerminalHeight provides terminal.size.height,
                    LocalKeyboardInterceptor provides keyboardInterceptor,
                    LocalFocusRegistry provides focusRegistry,
                    LocalDispatchScope provides dispatchScope,
                    LocalTheme provides DispatchTheme.Dark,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (showDecision) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DecisionPrompt(
                                        question = "Choose next action",
                                        options = listOf(
                                            DecisionOption("Option A"),
                                            DecisionOption("Option B"),
                                            DecisionOption("Option C"),
                                        ),
                                        placeholder = "tell the assistant how it should proceed...",
                                        enabled = false,
                                        onSubmit = {},
                                    )
                                }
                            }
                        }
                        if (showStatus) {
                            item { StatusTicker() }
                        }
                        item {
                            InputTextField(
                                value = inputValue,
                                onValueChange = { inputValue = it },
                                icon = "> ",
                                placeholder = "Type something",
                            )
                        }
                    }
                }
                composer.endComposition()
            }

            val rootNode = composer.getRootNode() ?: return
            focusRegistry.sync(rootNode)
            rootNode.measure(Constraints.fixedWidth(terminal.size.width))
        }

        fun press(key: String) {
            dispatchScope.sendKey(KeyboardEvent(key))
            render()
        }
    }

    @Test
    fun `input remains stable while optional siblings are inserted and removed`() {
        val harness = Harness()
        harness.render()

        repeat(100) { index ->
            harness.showDecision = index % 2 == 0
            harness.showStatus = index % 3 == 0
            harness.render()
            harness.press("x")
        }

        assertTrue(harness.inputValue.isNotEmpty())
        assertTrue(harness.inputValue.length >= 90)
    }
}

@Dispatchable
private fun StatusTicker() {
    var tick by remember { mutableStateOf(0) }
    tick = (tick + 1) % 10
    Text("status $tick")
}
