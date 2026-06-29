package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.layout.TerminalScreen
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxSize
import com.ead.dispatch.modifier.height
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme

/**
 * General screen frame for a Dispatch terminal app.
 *
 * Lays out a header, a body and a footer via [TerminalScreen]:
 * - the header shows [title]/[subtitle] (via [Text]) unless a custom [header] is supplied;
 * - the body renders [content];
 * - the footer renders the custom [footer] (if any) followed by a [KeyHintBar] of [hints].
 *
 * When [escGoesBack] is `true` and [onBack] is non-null, an Esc handler is registered on the
 * current [LocalKeyboardInterceptor] (at priority `5`, matching the sample's convention) that
 * invokes [onBack] and consumes the event. Unlike a navigation-aware scaffold this widget has no
 * dependency on `dispatch-navigation`; callers wire "back" through the [onBack] callback.
 *
 * This is the reusable screen frame the sample's `SampleScaffold` is expected to delegate to,
 * layering its own Ctrl+P navigator overlay on top.
 *
 * @param modifier Modifiers applied to the underlying [TerminalScreen].
 * @param title Header title; ignored when a custom [header] is provided.
 * @param subtitle Optional header subtitle; ignored when a custom [header] is provided.
 * @param header Custom header content. When non-null it fully replaces the default title/subtitle.
 * @param footer Optional custom footer content, rendered above the [KeyHintBar].
 * @param hints Key hints rendered as a [KeyHintBar] in the footer.
 * @param onBack Invoked when Esc-to-go-back fires; when `null`, no Esc handler is registered.
 * @param escGoesBack Whether Esc should trigger [onBack].
 * @param content The screen body.
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    hints: List<KeyHint> = emptyList(),
    onBack: (() -> Unit)? = null,
    escGoesBack: Boolean = true,
    content: @Composable () -> Unit,
) {
    val theme = LocalTheme.current
    val interceptor = LocalKeyboardInterceptor.current

    // Esc returns "back", unless a widget/overlay already consumed it.
    DisposableEffect(escGoesBack, onBack, interceptor) {
        val back = onBack
        if (!escGoesBack || back == null) return@DisposableEffect onDispose {}
        val dispose =
            interceptor.register(priority = 5) { rawEvent ->
                val event = rawEvent.asKeyEvent()
                if (event.key == Key.Escape) {
                    back()
                    true
                } else {
                    false
                }
            }
        onDispose { dispose() }
    }

    TerminalScreen(
        modifier = Modifier.fillMaxSize().then(modifier),
        header = {
            if (header != null) {
                header()
            } else {
                if (title != null) Text(title, style = theme.primary)
                if (subtitle != null) Text(subtitle, style = theme.muted)
                Spacer(Modifier.height(1))
            }
        },
        footer = {
            footer?.invoke()
            KeyHintBar(hints = hints)
        },
    ) {
        content()
    }
}
