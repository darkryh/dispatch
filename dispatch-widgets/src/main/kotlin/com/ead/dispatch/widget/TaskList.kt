package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Task execution status.
 */
enum class TaskStatus {
    Pending,
    InProgress,
    Completed,
    Failed,
    Skipped,
}

data class TaskListTextStyles(
    val pending: TextStyle? = rgb("#6F7279"),
    val inProgress: TextStyle? = rgb("#ffffff") + TextStyle(bold = true),
    val completed: TextStyle? = rgb("#ffffff"),
    val failed: TextStyle? = rgb("#ffffff"),
    val skipped: TextStyle? = rgb("#6F7279"),
    val details: TextStyle? = rgb("#6F7279"),
)

/**
 * A task list widget for tracking execution progress.
 *
 * Example:
 * ```kotlin
 * val tasks = listOf("Initialize project", "Install dependencies", "Run tests")
 * val status = listOf(TaskStatus.Completed, TaskStatus.InProgress, TaskStatus.Pending)
 *
 * TaskList(tasks = tasks, status = status, spinnerFrame = animationFrame)
 * ```
 */
@Dispatchable
fun TaskList(
    tasks: List<String>,
    modifier: Modifier = Modifier,
    spinnerFrame: Int = 0,
    showStatusIcons: Boolean = true,
    status: List<TaskStatus> = emptyList(),
    details: List<String?> = emptyList(),
    textStyles: TaskListTextStyles = TaskListTextStyles(),
) {
    Column(modifier = modifier) {
        tasks.forEachIndexed { index, taskDescription ->
            val taskStatus = status.getOrNull(index) ?: TaskStatus.Pending
            val taskDetails = details.getOrNull(index)

            Row {
                if (showStatusIcons) {
                    when (taskStatus) {
                        TaskStatus.Pending -> Text("[ ] ", style = textStyles.pending)
                        TaskStatus.InProgress -> {
                            Spinner(frame = spinnerFrame, style = SpinnerStyle.Dots, textStyle = textStyles.inProgress)
                            Text(" ")
                        }
                        TaskStatus.Completed -> Text("[✓] ", style = textStyles.completed)
                        TaskStatus.Failed -> Text("[✗] ", style = textStyles.failed)
                        TaskStatus.Skipped -> Text("[—] ", style = textStyles.skipped)
                    }
                }

                val textStyle = when (taskStatus) {
                    TaskStatus.Pending -> textStyles.pending
                    TaskStatus.InProgress -> textStyles.inProgress
                    TaskStatus.Completed -> textStyles.completed
                    TaskStatus.Failed -> textStyles.failed
                    TaskStatus.Skipped -> textStyles.skipped
                }

                Text(taskDescription, style = textStyle)
            }

            if (taskDetails != null) {
                Row {
                    Text("    ")
                    Text(taskDetails, style = textStyles.details)
                }
            }
        }
    }
}

/**
 * A model-neutral task list widget.
 *
 * Use this overload when you already have a model type, but don't want to depend on a Dispatch
 * data class.
 */
@Dispatchable
fun <T> TaskList(
    tasks: List<T>,
    modifier: Modifier = Modifier,
    spinnerFrame: Int = 0,
    showStatusIcons: Boolean = true,
    status: (T) -> TaskStatus = { TaskStatus.Pending },
    description: (T) -> String = { it.toString() },
    details: (T) -> String? = { null },
    textStyles: TaskListTextStyles = TaskListTextStyles(),
) {
    Column(modifier = modifier) {
        tasks.forEach { task ->
            val taskStatus = status(task)
            val taskDescription = description(task)
            val taskDetails = details(task)
            Row {
                if (showStatusIcons) {
                    when (taskStatus) {
                        TaskStatus.Pending -> Text("[ ] ", style = textStyles.pending)
                        TaskStatus.InProgress -> {
                            Spinner(frame = spinnerFrame, style = SpinnerStyle.Dots, textStyle = textStyles.inProgress)
                            Text(" ")
                        }
                        TaskStatus.Completed -> Text("[✓] ", style = textStyles.completed)
                        TaskStatus.Failed -> Text("[✗] ", style = textStyles.failed)
                        TaskStatus.Skipped -> Text("[—] ", style = textStyles.skipped)
                    }
                }

                val textStyle = when (taskStatus) {
                    TaskStatus.Pending -> textStyles.pending
                    TaskStatus.InProgress -> textStyles.inProgress
                    TaskStatus.Completed -> textStyles.completed
                    TaskStatus.Failed -> textStyles.failed
                    TaskStatus.Skipped -> textStyles.skipped
                }

                Text(taskDescription, style = textStyle)
            }

            if (taskDetails != null) {
                Row {
                    Text("    ")
                    Text(taskDetails, style = textStyles.details)
                }
            }
        }
    }
}
