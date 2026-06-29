package io.github.darkryh.dispatch.initializr.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A box-bordered terminal panel with an accent title (the web twin of the library's `Panel`). */
@Composable
fun TerminalPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fonts = LocalTermFonts.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, Term.border)
                .padding(14.dp),
    ) {
        Text(
            text = "╾ $title",
            color = Term.accent,
            fontFamily = fonts,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A terminal-style input row: `❯ label  value`, with an animated accent underline on focus. */
@Composable
fun PromptField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalTermFonts.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(Tempo.FOCUS_GLOW),
        label = "underline",
    )

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp)
                    .drawBehind {
                        val y = size.height
                        drawLine(Term.borderMuted, Offset(0f, y), Offset(size.width, y), 1f)
                        if (grow > 0f) {
                            drawLine(Term.accent, Offset(0f, y), Offset(size.width * grow, y), 2f)
                        }
                    },
        ) {
            Text(
                text = "❯ ",
                color = if (focused) Term.accent else Term.muted,
                fontFamily = fonts,
                fontSize = 14.sp,
            )
            Text(
                text = label.padEnd(13),
                color = if (focused) Term.secondary else Term.muted,
                fontFamily = fonts,
                fontSize = 14.sp,
            )
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && !focused) {
                    Text("…", color = Term.faint, fontFamily = fonts, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle =
                        TextStyle(color = Term.primary, fontFamily = fonts, fontSize = 14.sp),
                    cursorBrush = SolidColor(Term.cursor),
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error != null) {
            Text(
                text = "  ✗ $error",
                color = Term.error,
                fontFamily = fonts,
                fontSize = 12.sp,
            )
        }
    }
}

/** The dimmed, read-only `↳ group … · artifact …` echo under the package field. */
@Composable
fun DerivedLine(
    groupId: String,
    artifactId: String,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalTermFonts.current
    Text(
        text = "    ↳ group $groupId · artifact $artifactId",
        color = Term.muted,
        fontFamily = fonts,
        fontSize = 12.sp,
        modifier = modifier.padding(start = 4.dp, top = 1.dp),
    )
}

private class TreeNode(val name: String) {
    val children = LinkedHashMap<String, TreeNode>()

    fun child(segment: String): TreeNode = children.getOrPut(segment) { TreeNode(segment) }
}

private data class TreeLine(val text: String, val isDir: Boolean)

private fun buildTree(
    rootName: String,
    paths: List<String>,
): List<TreeLine> {
    val root = TreeNode(rootName)
    for (path in paths) {
        var node = root
        for (segment in path.split('/')) node = node.child(segment)
    }
    val lines = mutableListOf(TreeLine("$rootName/", isDir = true))
    fun walk(
        node: TreeNode,
        prefix: String,
    ) {
        val entries = node.children.values.toList()
        entries.forEachIndexed { index, child ->
            val last = index == entries.lastIndex
            val isDir = child.children.isNotEmpty()
            val connector = if (last) "└─ " else "├─ "
            lines += TreeLine(prefix + connector + child.name + if (isDir) "/" else "", isDir)
            walk(child, prefix + if (last) "   " else "│  ")
        }
    }
    walk(root, "")
    return lines
}

/** Render the generated file paths as a box-drawing tree, dirs in cyan, `.kt` files tinted. */
@Composable
fun FileTree(
    rootName: String,
    paths: List<String>,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalTermFonts.current
    Column(modifier) {
        buildTree(rootName, paths).forEach { line ->
            val color =
                when {
                    line.isDir -> Term.secondary
                    line.text.endsWith(".kt") -> Term.code
                    else -> Term.primary
                }
            Text(line.text, color = color, fontFamily = fonts, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** A terminal action button: `[ ⏎ label ]`, inverting to accent fill on hover. */
@Composable
fun BracketButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalTermFonts.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = enabled && hovered
    Box(
        modifier =
            modifier
                .hoverable(interaction)
                .clickable(enabled = enabled) { onClick() }
                .background(if (active) Term.accent else Term.surface)
                .border(1.dp, if (enabled) Term.accent else Term.faint)
                .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = if (active) Term.background else if (enabled) Term.primary else Term.faint,
            fontFamily = fonts,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The bottom hint/status bar — the web twin of the library's `KeyHintBar`. */
@Composable
fun StatusBar(
    ready: Boolean,
    issues: Int,
    versions: String,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalTermFonts.current
    Row(
        modifier = modifier.fillMaxWidth().background(Term.surface).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Hint("⏎", "generate", fonts)
            Spacer(Modifier.width(14.dp))
            Hint("⇥", "next field", fonts)
        }
        Text(
            text = if (ready) "✓ ready" else "✗ $issues ${if (issues == 1) "issue" else "issues"}",
            color = if (ready) Term.success else Term.error,
            fontFamily = fonts,
            fontSize = 12.sp,
        )
        Text(versions, color = Term.muted, fontFamily = fonts, fontSize = 12.sp)
    }
}

@Composable
private fun Hint(
    key: String,
    desc: String,
    fonts: androidx.compose.ui.text.font.FontFamily,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = Term.accent, fontFamily = fonts, fontSize = 12.sp)
        Text(" $desc", color = Term.muted, fontFamily = fonts, fontSize = 12.sp)
    }
}
