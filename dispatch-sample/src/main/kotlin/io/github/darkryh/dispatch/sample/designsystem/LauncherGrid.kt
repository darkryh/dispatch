@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.BorderStyle
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.navigation.CatalogDestination
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextStyles

/** Fixed width of one launcher card. [CatalogDestination.COLUMNS] of these plus gaps form the menu block. */
private const val CARD_WIDTH = 24

/** Horizontal gap between cards in a row. */
private const val CARD_GAP = 2

/**
 * The Home body: every [CatalogDestination] rendered as a fixed-width card in a
 * [CatalogDestination.COLUMNS]-column block that is **horizontally centred** in the terminal — the
 * side margin grows on wider screens. The card at [selectedIndex] is highlighted (heavy border +
 * accent text); each card's glyph, title and blurb are centre-aligned.
 *
 * This deliberately does NOT use the library `Grid` (whose column count is driven by terminal width
 * and which fills the full width); here we want a fixed, centred block. Centring is done with an
 * explicit leading margin so it is independent of any container alignment. 2-D cursor movement lives
 * in `HomeViewModel`; rows are chunked in the same column count it navigates by.
 */
@Composable
fun LauncherGrid(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val terminalWidth = LocalTerminalWidth.current
    val columns = CatalogDestination.COLUMNS
    val blockWidth = columns * CARD_WIDTH + (columns - 1) * CARD_GAP
    val leftMargin = ((terminalWidth - blockWidth) / 2).coerceAtLeast(0)
    val rows = CatalogDestination.entries.chunked(columns)

    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                if (leftMargin > 0) {
                    Spacer(Modifier.width(leftMargin))
                }
                row.forEachIndexed { index, destination ->
                    LauncherCard(destination, destination.ordinal == selectedIndex)
                    if (index != row.lastIndex) {
                        Spacer(Modifier.width(CARD_GAP))
                    }
                }
            }
            Spacer(Modifier.height(1))
        }
    }
}

@Composable
private fun LauncherCard(
    destination: CatalogDestination,
    isSelected: Boolean,
) {
    val theme = LocalTheme.current
    val titleStyle = (if (isSelected) theme.accent else theme.primary) + TextStyles.bold.style
    Panel(
        modifier = Modifier.width(CARD_WIDTH),
        borderStyle = if (isSelected) BorderStyle.Heavy else BorderStyle.Rounded,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(1))
            Text(
                "${destination.glyph} ${destination.title}",
                align = TextAlign.CENTER,
                modifier = Modifier.fillMaxWidth(),
                style = titleStyle,
            )
            Spacer(Modifier.height(1))
            Text(destination.blurb, align = TextAlign.CENTER, modifier = Modifier.fillMaxWidth(), style = theme.muted)
            Spacer(Modifier.height(1))
        }
    }
}
