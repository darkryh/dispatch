@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.widget.Checklist
import com.ead.dispatch.widget.ChecklistItem
import com.ead.dispatch.widget.CountTile
import com.ead.dispatch.widget.CountTileGrid
import com.ead.dispatch.widget.EmptyState
import com.ead.dispatch.widget.Grid
import com.ead.dispatch.widget.GridCells
import com.ead.dispatch.widget.LabeledValue
import com.ead.dispatch.widget.LabeledValueList
import com.ead.dispatch.widget.SectionHeader
import com.ead.dispatch.widget.TaskList
import com.ead.dispatch.widget.TaskStatus
import com.ead.dispatch.widget.Text

@Composable
internal fun DataGallery() {
    GalleryScreen("Data", "Structured summaries, status collections, and empty states") {
        SectionHeader("Counts")
        CountTileGrid(
            listOf(
                CountTile("Ready", 8),
                CountTile("Running", 2),
                CountTile("Failed", 1),
            ),
        )
        SectionHeader("Adaptive grid")
        Grid(items = listOf("One", "Two", "Three"), cells = GridCells.Fixed(3)) { item, _ ->
            Text("[$item]")
        }
        LabeledValueList(
            listOf(
                LabeledValue("Runtime", "Compose"),
                LabeledValue("Renderer", "Terminal"),
            ),
        )
        SectionHeader("Checklist")
        Checklist(
            items =
                listOf(
                    ChecklistItem("Compose layout", checked = true),
                    ChecklistItem("Validate focus"),
                    ChecklistItem("Publish", enabled = false),
                ),
            selectedIndex = 1,
        )
        SectionHeader("Tasks")
        TaskList(
            tasks = listOf("Build distribution", "Run PTY suite", "Collect report"),
            status = listOf(TaskStatus.Completed, TaskStatus.InProgress, TaskStatus.Pending),
            details = listOf(null, "Operating-system pseudo-terminal", null),
        )
        EmptyState(
            title = "No additional records",
            description = "EmptyState gives missing content an explicit presentation.",
        )
    }
}
