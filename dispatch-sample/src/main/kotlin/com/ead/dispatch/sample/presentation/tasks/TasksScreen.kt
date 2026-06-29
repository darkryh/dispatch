@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.designsystem.ControlPanel
import com.ead.dispatch.sample.designsystem.ControlSpec
import com.ead.dispatch.sample.designsystem.PlaygroundController
import com.ead.dispatch.sample.designsystem.PlaygroundIntent
import com.ead.dispatch.sample.designsystem.PlaygroundScaffold
import com.ead.dispatch.sample.widgets.CountTile
import com.ead.dispatch.sample.widgets.CountTileGrid
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.Checklist
import com.ead.dispatch.widget.ChecklistItem
import com.ead.dispatch.widget.ChecklistStyle
import com.ead.dispatch.widget.TaskList
import com.ead.dispatch.widget.TaskStatus
import com.ead.dispatch.widget.Text

/**
 * Checklist & Tasks playground.
 *
 * Demonstrates: [Checklist] (all four [ChecklistStyle]s, per-item checked state and a moveable
 * selection cursor), the model-neutral [TaskList] overload (driven by `status`/`description`/
 * `details` lambdas over [DemoTask]), and the reusable
 * [com.ead.dispatch.sample.widgets.CountTileGrid] summarising task-status counts — each rendered
 * live from the controls pane.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Checklist & Tasks",
        subtitle = "Cycle styles/items with ←/→, tick the selected item with Space.",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle("Checklist style", ChecklistStyle.entries.map { it.name }, state.checklistStyle),
                        ControlSpec.Cycle("Checklist item", CHECKLIST_ITEMS, state.itemIndex),
                        ControlSpec.Toggle("Item checked", state.itemIndex in state.checkedItems),
                        ControlSpec.Cycle("Task", DEMO_TASKS.map { it.title }, state.taskIndex),
                        ControlSpec.Cycle("Task status", TaskStatus.entries.map { it.name }, state.statuses[state.taskIndex]),
                    ),
                selected = state.selected,
            )
        },
        preview = { TasksPreview(state) },
    )
}

@Composable
private fun TasksPreview(state: TasksState) {
    val theme = LocalTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Checklist — ${state.checklistStyleValue.name}", style = theme.muted)
        Checklist(
            items = CHECKLIST_ITEMS.mapIndexed { index, name -> ChecklistItem(name = name, checked = index in state.checkedItems) },
            selectedIndex = state.itemIndex,
            style = state.checklistStyleValue,
        )
        Spacer(Modifier.height(1))

        Text("TaskList", style = theme.muted)
        TaskList(
            tasks = DEMO_TASKS,
            status = { TaskStatus.entries[state.statuses[DEMO_TASKS.indexOf(it)]] },
            description = { it.title },
            details = { it.detail },
        )
        Spacer(Modifier.height(1))

        Text("Status counts", style = theme.muted)
        CountTileGrid(
            items =
                listOf(
                    CountTile("Completed", state.statusCount(TaskStatus.Completed)),
                    CountTile("In progress", state.statusCount(TaskStatus.InProgress)),
                    CountTile("Failed", state.statusCount(TaskStatus.Failed)),
                    CountTile("Pending", state.statusCount(TaskStatus.Pending)),
                ),
            styleForCount = { if (it > 0) theme.success else theme.muted },
        )
    }
}

private fun TasksState.statusCount(status: TaskStatus): Int = statuses.count { TaskStatus.entries[it] == status }
