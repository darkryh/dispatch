@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.widget.DividerStyle
import io.github.darkryh.dispatch.widget.HorizontalDivider
import io.github.darkryh.dispatch.widget.Text

/**
 * The header shown at the top of every screen: a bold title, an optional muted subtitle, and a
 * full-width rule that separates the chrome from the body. Demonstrates: [Text] styling via the
 * theme roles and the [HorizontalDivider] enum overload.
 */
@Composable
fun TitleBanner(
    title: String,
    subtitle: String? = null,
) {
    val theme = LocalTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row {
            Text("▌ ", style = theme.accent)
            Text(title, style = theme.primary)
        }
        if (subtitle != null) {
            Text("  $subtitle", style = theme.muted)
        }
        HorizontalDivider(style = DividerStyle.Light, modifier = Modifier.fillMaxWidth())
    }
}
