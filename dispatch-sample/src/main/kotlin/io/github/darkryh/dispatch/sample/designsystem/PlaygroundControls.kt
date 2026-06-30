@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.input.asKeyEvent
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.widget.Text

/**
 * The single intent type every playground view-model handles, matching [PlaygroundController]:
 * [Select] moves the highlighted control, [Change] adjusts the selected cycle, [Toggle] flips the
 * selected boolean. Sharing one type keeps every category screen's wiring identical.
 */
sealed interface PlaygroundIntent {
    data class Select(
        val delta: Int,
    ) : PlaygroundIntent

    data class Change(
        val delta: Int,
    ) : PlaygroundIntent

    data object Toggle : PlaygroundIntent
}

/** Wraps an index by [delta] within `0 until size` (size 0 returns 0). */
fun wrapIndex(
    current: Int,
    delta: Int,
    size: Int,
): Int = if (size <= 0) 0 else ((current + delta) % size + size) % size

sealed interface ControlSpec {
    val label: String

    /** A control whose value is one of [options]; changed with ←/→. */
    data class Cycle(
        override val label: String,
        val options: List<String>,
        val index: Int,
    ) : ControlSpec

    /** A boolean control flipped with Space. */
    data class Toggle(
        override val label: String,
        val on: Boolean,
    ) : ControlSpec

    /** A read-only echo of some state (e.g. a counter or the current input). */
    data class Value(
        override val label: String,
        val value: String,
    ) : ControlSpec
}

/**
 * Renders a list of [ControlSpec]s as aligned rows, highlighting the [selected] one. Pure rendering:
 * all key handling lives in the owning screen's view-model so this stays trivially testable.
 */
@Composable
fun ControlPanel(
    specs: List<ControlSpec>,
    selected: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val labelWidth = (specs.maxOfOrNull { it.label.length } ?: 0).coerceAtLeast(1)

    Column(modifier = modifier) {
        specs.forEachIndexed { index, spec ->
            val isSelected = index == selected
            val cursorStyle = if (isSelected) theme.accent else theme.muted
            val labelStyle = if (isSelected) theme.primary else theme.muted
            Row {
                Text(if (isSelected) "❯ " else "  ", style = cursorStyle)
                Text(spec.label.padEnd(labelWidth) + "  ", style = labelStyle)
                when (spec) {
                    is ControlSpec.Cycle -> {
                        val current = spec.options.getOrNull(spec.index).orEmpty()
                        Text("‹ ", style = theme.muted)
                        Text(current, style = if (isSelected) theme.accent else theme.info)
                        Text(" ›", style = theme.muted)
                        if (isSelected) Text("   ←/→", style = theme.muted)
                    }
                    is ControlSpec.Toggle -> {
                        Text(if (spec.on) "[x]" else "[ ]", style = if (isSelected) theme.accent else theme.info)
                        if (isSelected) Text("   Space", style = theme.muted)
                    }
                    is ControlSpec.Value -> {
                        Text(spec.value, style = theme.info)
                    }
                }
            }
        }
    }
}

/**
 * Registers the uniform playground key bindings at [AppKeyPriority.PLAYGROUND] (above the library's
 * focusable widgets, which sit at -1 and only act while focused, but below the global palette):
 * - **↑/↓** moves the selected control (`onSelect(-1)` / `onSelect(+1)`),
 * - **←/→** changes the selected [ControlSpec.Cycle] (`onChange(-1)` / `onChange(+1)`),
 * - **Space** flips the selected [ControlSpec.Toggle] (`onToggle()`).
 *
 * Leaves Tab and Enter alone so genuinely focusable preview widgets still traverse and activate.
 * Set [enabled] to false on screens where a text field owns the keyboard.
 */
@Composable
fun PlaygroundController(
    onSelect: (Int) -> Unit,
    onChange: (Int) -> Unit,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    val interceptor = LocalKeyboardInterceptor.current
    DisposableEffect(interceptor, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val dispose =
            interceptor.register(priority = AppKeyPriority.PLAYGROUND) { rawEvent ->
                when (rawEvent.asKeyEvent().key) {
                    Key.ArrowUp -> {
                        onSelect(-1)
                        true
                    }
                    Key.ArrowDown -> {
                        onSelect(1)
                        true
                    }
                    Key.ArrowLeft -> {
                        onChange(-1)
                        true
                    }
                    Key.ArrowRight -> {
                        onChange(1)
                        true
                    }
                    Key.Space -> {
                        onToggle()
                        true
                    }
                    else -> false
                }
            }
        onDispose { dispose() }
    }
}
