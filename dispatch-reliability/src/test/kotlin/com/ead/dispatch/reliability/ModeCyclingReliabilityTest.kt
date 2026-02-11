package com.ead.dispatch.reliability

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.reliability.harness.ReliabilityHarness
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Text
import kotlin.test.Test
import kotlin.test.assertTrue

class ModeCyclingReliabilityTest {
    @Test
    fun `cycling modes with long history keeps footer stable`() {
        val harness = ReliabilityHarness(width = 120, height = 40)
        val history = List(500) { index ->
            "[$index] assistant: generated long-running content line ${index + 1}"
        }

        repeat(300) { iteration ->
            val chatMode = iteration % 2 == 0
            val modeLabel = if (chatMode) "chat mode" else "story mode"

            val lines = harness.render {
                ModeCyclingScreen(
                    history = history,
                    modeLabel = modeLabel,
                    contextPercent = 93,
                )
            }

            assertTrue(lines.any { it.contains("93% context left") }, "context footer must stay visible")
            assertTrue(lines.any { it.contains(modeLabel) }, "mode label must stay visible")
        }
    }
}

@Dispatchable
private fun ModeCyclingScreen(
    history: List<String>,
    modeLabel: String,
    contextPercent: Int,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(history) { line ->
            Text(line)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("  ⏸ $modeLabel (shift+tab to cycle)")
                Spacer(modifier = Modifier.weight(1f))
                Text("$contextPercent% context left")
            }
        }
    }
}
