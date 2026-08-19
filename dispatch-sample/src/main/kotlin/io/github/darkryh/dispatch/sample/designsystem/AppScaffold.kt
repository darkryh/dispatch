@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.input.asKeyEvent
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.layout.TerminalScreen
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.navigation.LocalNavigator
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.widget.KeyHint
import io.github.darkryh.dispatch.widget.KeyHintBar

/**
 * The consistent frame around every screen in the sample. It is the reusable "blessed pattern"
 * a real Dispatch app would copy: a titled header, the caller's body, and a footer that always
 * carries the global "go to…" palette plus a [KeyHintBar] describing the intuitive keys.
 *
 * Behaviour:
 * - **Esc goes back** to the previous screen, registered at the *lowest* keyboard priority (5) so
 *   any widget or overlay that wants Esc (the chat stream, the command palette) consumes it first.
 *   Disabled on screens that own Esc themselves (e.g. Home) via [escGoesBack].
 * - The global [GlobalCommandPalette] is always present, so `Ctrl+P` jumps anywhere from anywhere.
 *
 * @param hints screen-specific key hints appended after the shared base hints.
 * @param footer optional content rendered above the palette/hint bar (e.g. the chat composer).
 */
@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    escGoesBack: Boolean = true,
    hints: List<KeyHint> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val navigator = LocalNavigator.current
    val interceptor = LocalKeyboardInterceptor.current

    // Esc returns to the previous screen, unless a higher-priority handler already consumed it.
    DisposableEffect(escGoesBack, interceptor) {
        if (!escGoesBack) return@DisposableEffect onDispose {}
        val dispose =
            interceptor.register(priority = AppKeyPriority.ESC_BACK) { rawEvent ->
                if (rawEvent.asKeyEvent().key == Key.Escape) {
                    navigator.popBackStack()
                } else {
                    false
                }
            }
        onDispose { dispose() }
    }

    TerminalScreen(
        header = {
            TitleBanner(title = title, subtitle = subtitle)
            Spacer(Modifier.height(1))
        },
        footer = {
            footer?.invoke()
            GlobalCommandPalette()
            KeyHintBar(hints = baseHints(escGoesBack) + hints)
        },
    ) {
        content()
    }
}

private fun baseHints(escGoesBack: Boolean): List<KeyHint> =
    buildList {
        add(KeyHint("Ctrl+P", "go to…"))
        if (escGoesBack) add(KeyHint("Esc", "back"))
        add(KeyHint("Ctrl+C x2", "exit"))
    }

/**
 * The keyboard-interceptor priority ladder for the whole sample, kept in one place so the layering
 * is auditable. Higher numbers see each key first; a handler returns `true` to consume the key.
 */
object AppKeyPriority {
    /** Global "go to…" palette (Ctrl+P). Wins everywhere and is modal while open. */
    const val GLOBAL_PALETTE = 1000

    /** Home launcher-grid 2-D navigation. Home only; safe because Home has no text input. */
    const val HOME_GRID = 900

    /** Chat Esc-to-cancel while a response is streaming. Chat only. */
    const val CHAT_CANCEL = 100

    /**
     * Polling-table selection (↑/↓), detail (Enter) and ←-as-back. That screen only; it is the one
     * sample screen with no [AppScaffold] around it, so it owns its own way out.
     */
    const val POLLING_TABLE = 80

    /** Per-screen playground controls (Tab / ←→ / Space). Disabled while a text field has focus. */
    const val PLAYGROUND = 60

    /** Esc-as-back, registered by [AppScaffold]. The lowest layer so widgets get Esc first. */
    const val ESC_BACK = 5
}
