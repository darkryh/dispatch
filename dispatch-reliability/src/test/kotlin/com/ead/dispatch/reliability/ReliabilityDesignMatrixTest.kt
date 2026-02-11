package com.ead.dispatch.reliability

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.width
import com.ead.dispatch.reliability.harness.ReliabilityHarness
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.widget.Checklist
import com.ead.dispatch.widget.ChecklistItem
import com.ead.dispatch.widget.CommandOption
import com.ead.dispatch.widget.CommandPalette
import com.ead.dispatch.widget.CountTile
import com.ead.dispatch.widget.CountTileGrid
import com.ead.dispatch.widget.DecisionOption
import com.ead.dispatch.widget.DecisionPrompt
import com.ead.dispatch.widget.FilterBar
import com.ead.dispatch.widget.Grid
import com.ead.dispatch.widget.GridCells
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.ProgressBar
import com.ead.dispatch.widget.SelectableList
import com.ead.dispatch.widget.SelectableListStyles
import com.ead.dispatch.widget.SelectableWindowedList
import com.ead.dispatch.widget.SessionOption
import com.ead.dispatch.widget.SessionSelector
import com.ead.dispatch.widget.TagList
import com.ead.dispatch.widget.TaskList
import com.ead.dispatch.widget.TaskStatus
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TransferProgress
import com.ead.dispatch.widget.rememberCommandPaletteState
import com.ead.dispatch.widget.rememberSessionSelectorState
import com.ead.dispatch.widget.rememberTextFieldState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import kotlin.test.Test
import kotlin.test.assertTrue

class ReliabilityDesignMatrixTest {
    @Test
    fun `ui design matrix remains stable across themes and viewport constraints`() {
        val sizes = listOf(
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
                    val unbounded = harness.render(theme = theme, boundedHeight = false) {
                        ScenarioScreen(scenario)
                    }
                    assertScenarioVisible(unbounded, scenario, size, theme, bounded = false)

                    val bounded = harness.render(theme = theme, boundedHeight = true) {
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

private data class TerminalSize(val width: Int, val height: Int)

private enum class ReliabilityScenario(val anchor: String) {
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

@Dispatchable
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

@Dispatchable
private fun ChatTranscriptScenario() {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Text("anchor::chat_transcript") }
        items(List(240) { "assistant[$it]: this is reliability transcript line ${it + 1}" }) { line ->
            Text(line)
        }
        item {
            InputTextField(
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

@Dispatchable
private fun AnalyticsDashboardScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::analytics_dashboard")
        CountTileGrid(
            items = listOf(
                CountTile("users-online", 245),
                CountTile("active-streams", 18),
                CountTile("alerts-open", 3),
                CountTile("deployments", 7),
            ),
            cells = GridCells.Fixed(2),
        )
        Spacer(Modifier.height(1))
        ProgressBar(progress = 0.67f, showPercentage = true)
        TransferProgress(
            progress = 0.42f,
            bytesTransferred = 4_200_000,
            totalBytes = 10_000_000,
        )
        Spacer(Modifier.height(1))
        KeyHintBar(
            hints = listOf(
                KeyHint("↑/↓", "navigate"),
                KeyHint("enter", "confirm"),
                KeyHint("esc", "back"),
            ),
        )
    }
}

@Dispatchable
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
            items = listOf(
                ChecklistItem("Safety reviewed", checked = true),
                ChecklistItem("Citations included", checked = false),
                ChecklistItem("Output signed off", checked = false),
            ),
            selectedIndex = 1,
        )
    }
}

@Dispatchable
private fun SelectorWorkbenchScenario() {
    val commandState = rememberCommandPaletteState<String>()
    val sessionState = rememberSessionSelectorState<String>()
    val sessions = List(16) { index ->
        SessionOption(
            id = "session-$index",
            title = "Story draft ${index + 1}",
            updatedTime = "${index + 1}h ago",
            conversationId = "conv-${1000 + index}",
            messageCount = (index + 1) * 3,
            data = "session-$index",
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::selector_workbench")
        CommandPalette(
            options = listOf(
                CommandOption("story", "Open story tools", data = "story"),
                CommandOption("character", "Open character tools", data = "character"),
                CommandOption("timeline", "Open timeline tools", data = "timeline"),
            ),
            inputValue = "/sto",
            onOptionSelected = {},
            onInputTransform = {},
            state = commandState,
        )
        Spacer(Modifier.height(1))
        SessionSelector(
            options = sessions,
            onOptionSelected = {},
            onExit = {},
            state = sessionState,
            visibleCount = 5,
        )
    }
}

@Dispatchable
private fun FormDecisionScenario() {
    val filterState = rememberTextFieldState("villain")
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::form_decision")
        FilterBar(state = filterState)
        Spacer(Modifier.height(1))
        DecisionPrompt(
            question = "Choose a follow-up action",
            options = listOf(
                DecisionOption("Expand motivation"),
                DecisionOption("Add conflict"),
                DecisionOption("Tighten pacing"),
            ),
            onSubmit = {},
            enabled = true,
        )
    }
}

@Dispatchable
private fun UnicodeMarkdownScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::unicode_markdown")
        Text(
            text = "## Resonance\nA sea of neon, 心音, and soft static.\n- motif: recurring bell\n- cue: 🎻 + 低音",
            markdown = true,
        )
        Spacer(Modifier.height(1))
        LabeledValueList(
            items = listOf(
                LabeledValue("Locale", "ja-JP / en-US"),
                LabeledValue("Status", "Δ stable"),
                LabeledValue("GlyphTest", "中文 한국어 عربى"),
            )
        )
    }
}

@Dispatchable
private fun TagBackgroundScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::tag_background")
        Panel(modifier = Modifier.fillMaxWidth(), title = "Entity Overview") {
            TagList(tags = listOf("mystery", "noir", "flashback", "memory-core"))
            Spacer(Modifier.height(1))
            Text("The dossier combines fractured recollection with precise temporal indexing.")
        }
    }
}

@Dispatchable
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

@Dispatchable
private fun WindowedSelectableScenario() {
    val styles = SelectableListStyles(
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

@Dispatchable
private fun TransferMonitorScenario() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::transfer_monitor")
        ProgressBar(progress = 0.25f, showPercentage = true)
        ProgressBar(progress = 0.50f, showPercentage = true)
        ProgressBar(progress = 0.90f, showPercentage = true)
        Spacer(Modifier.height(1))
        TransferProgress(
            progress = 0.78f,
            bytesTransferred = 78_000_000,
            totalBytes = 100_000_000,
        )
    }
}

private fun DispatchTheme.identity(): String =
    when (this) {
        DispatchTheme.Dark -> "dark"
        DispatchTheme.Light -> "light"
        DispatchTheme.Minimal -> "minimal"
        else -> "custom"
    }
