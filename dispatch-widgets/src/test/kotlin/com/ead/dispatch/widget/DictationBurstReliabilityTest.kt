package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.widget.harness.ReliabilityHarness
import kotlin.test.Test
import kotlin.test.assertTrue

class DictationBurstReliabilityTest {
    @Test
    fun `dictation-like bursts keep mode and context footer visible`() {
        val harness = ReliabilityHarness(width = 120, height = 40)
        var inputValue = ""

        val bursts =
            listOf(
                "Describe a cold rain over the harbor with distant bells",
                "and then pivot into a memory of childhood fear",
                "before ending with a fragile promise",
                "now add concrete sensory details and tactile impressions",
                "make it less poetic and more direct for clarity",
            )

        repeat(120) { iteration ->
            val burst = bursts[iteration % bursts.size]
            inputValue = (inputValue + " " + burst).trim().takeLast(2_500)

            val beforeKeyLines =
                harness.render {
                    DictationFooterScreen(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        contextPercent = 93,
                    )
                }
            assertTrue(beforeKeyLines.any { it.contains("93% context left") }, "context footer missing before key")
            assertTrue(beforeKeyLines.any { it.contains("chat mode") }, "mode label missing before key")

            harness.press(" ")

            val afterKeyLines =
                harness.render {
                    DictationFooterScreen(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        contextPercent = 93,
                    )
                }
            assertTrue(afterKeyLines.any { it.contains("93% context left") }, "context footer missing after key")
            assertTrue(afterKeyLines.any { it.contains("chat mode") }, "mode label missing after key")
        }
    }
}

@Composable
private fun DictationFooterScreen(
    value: String,
    onValueChange: (String) -> Unit,
    contextPercent: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            icon = "> ",
            placeholder = "Describe your story (characters, genre, setting, plot)...",
            onSubmit = {},
        )
        Spacer(Modifier.height(1))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("  ⏸ chat mode (shift+tab to cycle)")
            Spacer(modifier = Modifier.weight(1f))
            Text("$contextPercent% context left")
        }
    }
}
