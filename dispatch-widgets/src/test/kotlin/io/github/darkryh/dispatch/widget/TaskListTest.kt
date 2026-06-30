package io.github.darkryh.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskListTest {
    @Test
    fun `renders status icons and details`() {
        val lines =
            renderLines(width = 60) {
                TaskList(
                    tasks = listOf("First", "Second", "Third"),
                    status = listOf(TaskStatus.Completed, TaskStatus.Failed, TaskStatus.Pending),
                    details = listOf("done", null, "waiting"),
                )
            }

        assertTrue(lines.any { it.contains("[✓]") })
        assertTrue(lines.any { it.contains("[✗]") })
        assertTrue(lines.any { it.contains("[ ]") })
        assertTrue(lines.any { it.contains("done") })
        assertTrue(lines.any { it.contains("waiting") })
        assertEquals(5, lines.size)
    }
}
