@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.widget.DividerStyle
import com.ead.dispatch.widget.HorizontalDivider
import com.ead.dispatch.widget.Text

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
