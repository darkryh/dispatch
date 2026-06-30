package io.github.darkryh.dispatch.sample.presentation.tasks

import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.viewmodel.MviViewModel
import io.github.darkryh.dispatch.widget.ChecklistStyle
import io.github.darkryh.dispatch.widget.TaskStatus

/** A task row rendered by the model-neutral [io.github.darkryh.dispatch.widget.TaskList] overload. */
data class DemoTask(
    val title: String,
    val detail: String,
)

/** Item labels driving the [io.github.darkryh.dispatch.widget.Checklist] preview. */
val CHECKLIST_ITEMS = listOf("Fetch deps", "Compile", "Run tests", "Package", "Publish")

/** Seed rows for the [io.github.darkryh.dispatch.widget.TaskList] preview. */
val DEMO_TASKS =
    listOf(
        DemoTask("Resolve dependencies", "Reads build.gradle.kts"),
        DemoTask("Compile sources", "kotlinc → JVM bytecode"),
        DemoTask("Run unit tests", "JUnit 5 + Kotest"),
        DemoTask("Assemble artifact", "Shadow jar"),
        DemoTask("Publish release", "Maven Central"),
    )

/** Number of editable control rows; selection wraps within this count. */
const val TASKS_CONTROL_COUNT = 5

/**
 * State of the Checklist & Tasks playground. [selected] is the highlighted control row; the rest are
 * the live properties fed into the preview widgets. [statuses] holds one [TaskStatus] ordinal per
 * task in [DEMO_TASKS]; [checkedItems] holds the indices of the ticked checklist entries.
 */
data class TasksState(
    val selected: Int = 0,
    val checklistStyle: Int = 0,
    val itemIndex: Int = 0,
    val checkedItems: Set<Int> = setOf(0, 1),
    val taskIndex: Int = 0,
    val statuses: List<Int> = List(DEMO_TASKS.size) { 0 },
) {
    val checklistStyleValue: ChecklistStyle get() = ChecklistStyle.entries[checklistStyle]
}

/**
 * Drives the Checklist & Tasks playground. Demonstrates [MviViewModel] with the shared
 * [PlaygroundIntent]: ↑/↓ moves [TasksState.selected], ←/→ cycles the selected checklist style,
 * checklist item, task or task status, and Space toggles the selected checklist item's checked
 * state.
 */
class TasksViewModel : MviViewModel<TasksState, PlaygroundIntent>(TasksState()) {
    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, TASKS_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(checklistStyle = wrapIndex(state.checklistStyle, intent.delta, ChecklistStyle.entries.size))
                        1 -> state.copy(itemIndex = wrapIndex(state.itemIndex, intent.delta, CHECKLIST_ITEMS.size))
                        3 -> state.copy(taskIndex = wrapIndex(state.taskIndex, intent.delta, DEMO_TASKS.size))
                        4 -> state.copy(statuses = state.advanceStatus(intent.delta))
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle ->
                updateState { state ->
                    if (state.selected == 2) state.copy(checkedItems = state.toggleChecked(state.itemIndex)) else state
                }
        }
    }

    private fun TasksState.advanceStatus(delta: Int): List<Int> =
        statuses.toMutableList().also { it[taskIndex] = wrapIndex(it[taskIndex], delta, TaskStatus.entries.size) }

    private fun TasksState.toggleChecked(index: Int): Set<Int> = if (index in checkedItems) checkedItems - index else checkedItems + index
}
