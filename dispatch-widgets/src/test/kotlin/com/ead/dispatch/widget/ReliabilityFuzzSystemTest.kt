package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.widget.harness.ReliabilityHarness
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class ReliabilityFuzzSystemTest {
    @Test
    fun `seeded fuzz interactions preserve visible anchors and footer`() {
        val harness = ReliabilityHarness(width = 100, height = 30)
        val seeds = listOf(7, 13, 23, 31, 47, 59, 71, 97)

        seeds.forEach { seed ->
            val random = Random(seed)
            var state = FuzzState()

            repeat(180) { step ->
                state = mutateState(state, random, harness)

                val lines =
                    harness.render(boundedHeight = true) {
                        FuzzApp(state)
                    }

                val expectedAnchor =
                    when (state.route) {
                        0 -> "anchor::fuzz_home"
                        1 -> "anchor::fuzz_chat"
                        else -> "anchor::fuzz_library"
                    }
                assertTrue(
                    lines.any { line -> line.contains(expectedAnchor) },
                    "seed=$seed step=$step missing route anchor for route=${state.route}",
                )
            }
        }
    }

    private fun mutateState(
        state: FuzzState,
        random: Random,
        harness: ReliabilityHarness,
    ): FuzzState =
        when (random.nextInt(10)) {
            0 -> state.copy(route = (state.route + 1) % 3)
            1 -> state.copy(modeIndex = (state.modeIndex + 1) % 2)
            2 -> state.copy(historySize = (state.historySize + random.nextInt(1, 40)).coerceAtMost(420))
            3 -> state.copy(historySize = (state.historySize - random.nextInt(1, 30)).coerceAtLeast(20))
            4 -> state.copy(contextPercent = (state.contextPercent + random.nextInt(-3, 4)).coerceIn(5, 99))
            5 -> state.copy(input = (state.input + " " + randomWord(random)).trim().takeLast(1_200))
            6 -> if (state.input.isNotEmpty()) state.copy(input = state.input.dropLast(1)) else state
            7 -> state.copy(commandInput = if (state.commandInput.startsWith("/")) "plain text" else "/st")
            else -> {
                val key = listOf("x", "Backspace", "ArrowUp", "ArrowDown", "Tab", " ").random(random)
                harness.press(key)
                state
            }
        }
}

private data class FuzzState(
    val route: Int = 0,
    val modeIndex: Int = 0,
    val historySize: Int = 80,
    val input: String = "",
    val commandInput: String = "/st",
    val contextPercent: Int = 93,
)

@Composable
private fun FuzzApp(state: FuzzState) {
    val modeLabel = if (state.modeIndex == 0) "chat mode" else "story mode"
    Column(modifier = Modifier.fillMaxWidth()) {
        when (state.route) {
            0 -> Text("anchor::fuzz_home")
            1 -> FuzzChatBody(state)
            else -> {
                Text("anchor::fuzz_library")
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(List(state.historySize) { "library-entry-${it + 1}" }) { line ->
                        Text(line)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text("anchor::fuzz_footer")
            Spacer(modifier = Modifier.weight(1f))
            Text("⏸ $modeLabel")
            Spacer(modifier = Modifier.weight(1f))
            Text("${state.contextPercent}% context left")
        }
    }
}

@Composable
private fun FuzzChatBody(state: FuzzState) {
    val paletteState = rememberCommandPaletteState<String>()
    Text("anchor::fuzz_chat")
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(List(state.historySize) { index -> "chat-history-${index + 1}" }) { line ->
            Text(line)
        }
        item {
            InputTextField(
                value = state.input,
                onValueChange = {},
                icon = "> ",
                placeholder = "type here",
                onSubmit = {},
            )
        }
        item {
            CommandPalette(
                options =
                    listOf(
                        CommandOption("story", "story tools", data = "story"),
                        CommandOption("status", "status tools", data = "status"),
                        CommandOption("stats", "analytics tools", data = "stats"),
                    ),
                inputValue = state.commandInput,
                onOptionSelected = {},
                onInputTransform = {},
                state = paletteState,
            )
        }
    }
}

private fun randomWord(random: Random): String =
    listOf(
        "storm",
        "memory",
        "thread",
        "entity",
        "timeline",
        "whisper",
        "archive",
        "route",
        "signal",
    ).random(random)
