package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.widget.harness.ReliabilityHarness
import kotlin.test.Test
import kotlin.test.assertTrue

class NavigationChurnReliabilityTest {
    @Test
    fun `rapid cross-screen navigation keeps anchors visible`() {
        val harness = ReliabilityHarness(width = 120, height = 40)
        var route = ChurnRoute.HOME
        var chatInput = ""

        repeat(450) { iteration ->
            route = route.next()

            val lines =
                harness.render {
                    ReliabilityApp(
                        route = route,
                        chatInput = chatInput,
                        onChatInputChange = { chatInput = it },
                    )
                }

            val anchor = route.anchor
            assertTrue(lines.any { it.contains(anchor) }, "missing route anchor: $anchor")
            assertTrue(lines.any { it.contains("reliability-footer") }, "shared footer must stay visible")

            if (route == ChurnRoute.CHAT) {
                harness.press("x")
                harness.render {
                    ReliabilityApp(
                        route = route,
                        chatInput = chatInput,
                        onChatInputChange = { chatInput = it },
                    )
                }
            }
        }
    }
}

private enum class ChurnRoute(
    val anchor: String,
) {
    HOME("anchor::home"),
    CHAT("anchor::chat"),
    LIBRARY("anchor::library"),
    ;

    fun next(): ChurnRoute =
        when (this) {
            HOME -> CHAT
            CHAT -> LIBRARY
            LIBRARY -> HOME
        }
}

@Composable
private fun ReliabilityApp(
    route: ChurnRoute,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (route) {
            ChurnRoute.HOME -> HomeScreen()
            ChurnRoute.CHAT -> ChatScreen(chatInput = chatInput, onChatInputChange = onChatInputChange)
            ChurnRoute.LIBRARY -> LibraryScreen()
        }

        Spacer(Modifier.height(1))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("reliability-footer")
            Spacer(modifier = Modifier.weight(1f))
            Text("frame-stable")
        }
    }
}

@Composable
private fun HomeScreen() {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::home")
        Text("dashboard metrics")
        Text("queue depth: 17")
    }
}

@Composable
private fun ChatScreen(
    chatInput: String,
    onChatInputChange: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Text("anchor::chat") }
        items(List(120) { "chat-history-${it + 1}" }) { line ->
            Text(line)
        }
        item {
            TextField(
                value = chatInput,
                onValueChange = onChatInputChange,
                icon = "> ",
                placeholder = "type message",
                onSubmit = {},
            )
        }
    }
}

@Composable
private fun LibraryScreen() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("anchor::library")
            Text("characters")
            Text("locations")
            Text("timeline")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("selected: chapter-03")
            Text("status: indexed")
            Text("items: 248")
        }
    }
}
