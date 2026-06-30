package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.harness.ReliabilityHarness
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import kotlin.test.Test
import kotlin.test.assertTrue

class ReliabilityDesignMatrixTest {
    @Test
    fun `ui design matrix remains stable across themes and viewport constraints`() {
        val sizes =
            listOf(
                TerminalSize(80, 24),
                TerminalSize(100, 30),
                TerminalSize(120, 40),
                TerminalSize(160, 50),
            )
        val themes = listOf(DispatchTheme.Dark, DispatchTheme.Light, DispatchTheme.Minimal)
        val scenarios = ReliabilityScenario.entries

        sizes.forEach { size ->
            themes.forEach { theme ->
                val harness = ReliabilityHarness(width = size.width, height = size.height, theme = theme)
                scenarios.forEach { scenario ->
                    val unbounded =
                        harness.render(theme = theme, boundedHeight = false) {
                            ScenarioScreen(scenario)
                        }
                    assertScenarioVisible(unbounded, scenario, size, theme, bounded = false)

                    val bounded =
                        harness.render(theme = theme, boundedHeight = true) {
                            ScenarioScreen(scenario)
                        }
                    assertScenarioVisible(bounded, scenario, size, theme, bounded = true)
                }
            }
        }
    }

    private fun assertScenarioVisible(
        lines: List<String>,
        scenario: ReliabilityScenario,
        size: TerminalSize,
        theme: DispatchTheme,
        bounded: Boolean,
    ) {
        val mode = if (bounded) "bounded" else "unbounded"
        assertTrue(lines.isNotEmpty(), "[$mode] no output for ${scenario.name} at ${size.width}x${size.height}")
        assertTrue(
            lines.any { it.contains(scenario.anchor) },
            "[$mode] missing anchor '${scenario.anchor}' for ${scenario.name} at ${size.width}x${size.height} (${theme.identity()})",
        )
        assertTrue(
            lines.any { it.isNotBlank() },
            "[$mode] output blank for ${scenario.name} at ${size.width}x${size.height} (${theme.identity()})",
        )
    }
}

private data class TerminalSize(
    val width: Int,
    val height: Int,
)

private enum class ReliabilityScenario(
    val anchor: String,
) {
    CHAT_TRANSCRIPT("anchor::chat_transcript"),
    ANALYTICS_DASHBOARD("anchor::analytics_dashboard"),
    WORKFLOW_CHECKLIST("anchor::workflow_checklist"),
    SELECTOR_WORKBENCH("anchor::selector_workbench"),
    FORM_DECISION("anchor::form_decision"),
    UNICODE_MARKDOWN("anchor::unicode_markdown"),
    TAG_BACKGROUND("anchor::tag_background"),
    GRID_INVENTORY("anchor::grid_inventory"),
    WINDOWED_SELECTABLE("anchor::windowed_selectable"),
    TRANSFER_MONITOR("anchor::transfer_monitor"),
}

@Composable
private fun ScenarioScreen(scenario: ReliabilityScenario) {
    when (scenario) {
        ReliabilityScenario.CHAT_TRANSCRIPT -> ChatTranscriptScenario()
        ReliabilityScenario.ANALYTICS_DASHBOARD -> AnalyticsDashboardScenario()
        ReliabilityScenario.WORKFLOW_CHECKLIST -> WorkflowChecklistScenario()
        ReliabilityScenario.SELECTOR_WORKBENCH -> SelectorWorkbenchScenario()
        ReliabilityScenario.FORM_DECISION -> FormDecisionScenario()
        ReliabilityScenario.UNICODE_MARKDOWN -> UnicodeMarkdownScenario()
        ReliabilityScenario.TAG_BACKGROUND -> TagBackgroundScenario()
        ReliabilityScenario.GRID_INVENTORY -> GridInventoryScenario()
        ReliabilityScenario.WINDOWED_SELECTABLE -> WindowedSelectableScenario()
        ReliabilityScenario.TRANSFER_MONITOR -> TransferMonitorScenario()
    }
}

@Composable
private fun ChatTranscriptScenario() {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Text("anchor::chat_transcript") }
        items(List(240) { "assistant[$it]: this is reliability transcript line ${it + 1}" }) { line ->
            Text(line)
        }
        item {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = "plan the next chapter with stronger tension and a cleaner climax",
                onValueChange = {},
                icon = "> ",
                placeholder = "type message...",
                onSubmit = {},
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("  ⏸ chat mode")
                Spacer(Modifier.weight(1f))
                Text("93% context left")
            }
        }
    }
}

@Composable
private fun AnalyticsDashboardScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::analytics_dashboard")
        Grid(
            items =
                listOf(
                    "users-online: 245",
                    "active-streams: 18",
                    "alerts-open: 3",
                    "deployments: 7",
                ),
            cells = GridCells.Fixed(2),
        ) { item, width ->
            Text(item.padEnd(width))
        }
        Spacer(Modifier.height(1))
        LinearProgressIndicator(progress = 0.67f, showPercentage = true)
        Spacer(Modifier.height(1))
        KeyHintBar(
            hints =
                listOf(
                    KeyHint("↑/↓", "navigate"),
                    KeyHint("enter", "confirm"),
                    KeyHint("esc", "back"),
                ),
        )
    }
}

@Composable
private fun WorkflowChecklistScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::workflow_checklist")
        TaskList(
            tasks = listOf("Collect data", "Run inference", "Draft answer", "Review"),
            status = listOf(TaskStatus.Completed, TaskStatus.InProgress, TaskStatus.Pending, TaskStatus.Pending),
            spinnerFrame = 3,
        )
        Spacer(Modifier.height(1))
        Checklist(
            items =
                listOf(
                    ChecklistItem("Safety reviewed", checked = true),
                    ChecklistItem("Citations included", checked = false),
                    ChecklistItem("Output signed off", checked = false),
                ),
            selectedIndex = 1,
        )
    }
}

@Composable
private fun SelectorWorkbenchScenario() {
    val commandState = rememberCommandPaletteState<String>()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::selector_workbench")
        CommandPalette(
            options =
                listOf(
                    CommandOption("story", "Open story tools", data = "story"),
                    CommandOption("character", "Open character tools", data = "character"),
                    CommandOption("timeline", "Open timeline tools", data = "timeline"),
                ),
            inputValue = "/sto",
            onOptionSelected = {},
            onInputTransform = {},
            state = commandState,
        )
    }
}

@Composable
private fun FormDecisionScenario() {
    val filterState = rememberTextFieldState("villain")
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::form_decision")
        TextField(state = filterState)
        Spacer(Modifier.height(1))
        DecisionPrompt(
            question = "Choose a follow-up action",
            options =
                listOf(
                    DecisionOption("Expand motivation"),
                    DecisionOption("Add conflict"),
                    DecisionOption("Tighten pacing"),
                ),
            onSubmit = {},
            enabled = true,
        )
    }
}

@Composable
private fun UnicodeMarkdownScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::unicode_markdown")
        Text(
            text = "## Resonance\nA sea of neon, 心音, and soft static.\n- motif: recurring bell\n- cue: 🎻 + 低音",
            markdown = true,
        )
        Spacer(Modifier.height(1))
        Text("Locale: ja-JP / en-US")
        Text("Status: Δ stable")
        Text("GlyphTest: 中文 한국어 عربى")
    }
}

@Composable
private fun TagBackgroundScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::tag_background")
        Panel(modifier = Modifier.fillMaxWidth(), title = "Entity Overview") {
            ChipRow(tags = listOf("mystery", "noir", "flashback", "memory-core"))
            Spacer(Modifier.height(1))
            Text("The dossier combines fractured recollection with precise temporal indexing.")
        }
    }
}

@Composable
private fun GridInventoryScenario() {
    val cells = List(24) { index -> "entity-${index + 1}" }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::grid_inventory")
        Grid(
            items = cells,
            cells = GridCells.Adaptive(minSize = 16),
        ) { item, width ->
            Text(item.padEnd(width))
        }
    }
}

@Composable
private fun WindowedSelectableScenario() {
    val styles =
        SelectableListStyles(
            prefix = rgb("#6F7279"),
            selectedPrefix = rgb("#00BFFF"),
        )
    val items = List(50) { "option-${it + 1}" }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::windowed_selectable")
        SelectableWindowedList(
            items = items,
            selectedIndex = 22,
            visibleCount = 8,
            styles = styles,
        ) { item, _ ->
            Text(item)
        }
        Spacer(Modifier.height(1))
        SelectableList(
            items = items.take(5),
            selectedIndex = 2,
            styles = styles,
        ) { item, isSelected ->
            if (isSelected) {
                Text("$item (active)")
            } else {
                Text(item)
            }
        }
    }
}

@Composable
private fun TransferMonitorScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::transfer_monitor")
        LinearProgressIndicator(progress = 0.25f, showPercentage = true)
        LinearProgressIndicator(progress = 0.50f, showPercentage = true)
        LinearProgressIndicator(progress = 0.90f, showPercentage = true)
    }
}

private fun DispatchTheme.identity(): String =
    when (this) {
        DispatchTheme.Dark -> "dark"
        DispatchTheme.Light -> "light"
        DispatchTheme.Minimal -> "minimal"
        else -> "custom"
    }
