package com.ead.dispatch.sample.presentation.components

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetCatalogCoverageTest {
    @Test
    fun `sample source exercises every public widget family`() {
        val sampleSource = readKotlinSources(findProjectRoot().resolve("dispatch-sample/src/main/kotlin"))
        val missing = catalogWidgets.filterNot { widget -> Regex("\\b$widget\\s*[({]").containsMatchIn(sampleSource) }

        assertEquals(emptyList(), missing, "Add every widget to a purposeful sample screen")
    }

    private fun findProjectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("Could not locate project root")
        }
        return current
    }

    private fun readKotlinSources(root: Path): String =
        Files.walk(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .sorted()
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }

    private companion object {
        val catalogWidgets =
            listOf(
                "Surface",
                "Button",
                "ButtonRow",
                "Checklist",
                "CommandPalette",
                "CountTileGrid",
                "SegmentedButton",
                "DecisionPrompt",
                "DiffReviewPanel",
                "EmptyState",
                "FileDiff",
                "Grid",
                "HorizontalDivider",
                "IconButton",
                "TextField",
                "KeyHintBar",
                "LabeledValueList",
                "LazyColumn",
                "LoadingIndicator",
                "Panel",
                "PasswordField",
                "LinearProgressIndicator",
                "RadioButton",
                "ScrollableList",
                "ScrollableListWithIndicator",
                "Section",
                "SectionHeader",
                "SelectableList",
                "SelectableWindowedList",
                "SessionSelector",
                "Spinner",
                "ChipRow",
                "Chip",
                "TaskList",
                "Text",
                "BasicTextFieldRenderer",
                "ToggleButton",
                "TransferProgress",
                "VerticalDivider",
            )
    }
}
