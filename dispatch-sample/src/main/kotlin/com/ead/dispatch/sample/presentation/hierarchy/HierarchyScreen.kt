@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.hierarchy

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
import com.ead.dispatch.sample.designsystem.PlaygroundScaffold
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.CommandOption
import com.ead.dispatch.widget.CommandPalette
import com.ead.dispatch.widget.DecisionOption
import com.ead.dispatch.widget.DecisionPrompt
import com.ead.dispatch.widget.DecisionSelection
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.Tree
import com.ead.dispatch.widget.TreeNode
import com.ead.dispatch.widget.rememberDecisionPromptState
import com.ead.dispatch.widget.rememberTreeState

/**
 * Hierarchy & Command playground.
 *
 * Demonstrates: [Tree] (keyed expand/collapse with its own arrow-key navigation), [DecisionPrompt]
 * (preset [DecisionOption]s plus a free-text custom answer), and the generic [CommandPalette] (a
 * `/`-triggered, filterable command list). Each widget owns its keys while focused, so this screen
 * deliberately skips the shared `PlaygroundController` (which would steal arrows from the Tree); Tab
 * moves focus between widgets. The controls pane is a read-only echo of what the widgets emit.
 */
@Composable
fun HierarchyScreen(viewModel: HierarchyViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundScaffold(
        title = "Hierarchy & Command",
        subtitle = "Tab between widgets · Tree: ↑/↓ move, →/← expand/collapse · type / in the palette",
        showControlHints = false,
        hints =
            listOf(
                KeyHint("Tab", "focus"),
                KeyHint("↑/↓", "move"),
                KeyHint("→/←", "expand"),
            ),
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Value("Last decision", state.lastDecision),
                        ControlSpec.Value("Last command", state.lastCommand),
                        ControlSpec.Value("Palette input", state.paletteInput.ifEmpty { "—" }),
                    ),
                selected = -1,
            )
        },
        preview = { HierarchyPreview(state, viewModel) },
    )
}

@Composable
private fun HierarchyPreview(
    state: HierarchyState,
    viewModel: HierarchyViewModel,
) {
    val theme = LocalTheme.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Tree — ↑/↓ move · →/← expand/collapse", style = theme.muted)
        Tree(
            roots = PROJECT_TREE,
            state = rememberTreeState(initialExpandedKeys = setOf("src", "main")),
            nodeKey = { it },
        ) { value, _, _ ->
            Text(value, style = theme.primary)
        }
        Spacer(Modifier.height(1))

        Text("DecisionPrompt — ↑/↓ select · Enter submit · Tab for custom", style = theme.muted)
        DecisionPrompt(
            question = "Deploy to production?",
            options =
                listOf(
                    DecisionOption("Yes"),
                    DecisionOption("No"),
                    DecisionOption("Later"),
                ),
            onSubmit = { selection ->
                viewModel.sendIntent(
                    HierarchyIntent.SubmitDecision(
                        when (selection) {
                            is DecisionSelection.Option -> selection.option.label
                            is DecisionSelection.Custom -> selection.text
                        },
                    ),
                )
            },
            state = rememberDecisionPromptState(),
        )
        Spacer(Modifier.height(1))

        Text("CommandPalette — type / to open", style = theme.muted)
        CommandPalette(
            options =
                listOf(
                    CommandOption(label = "build", description = "Compile the project", data = "build"),
                    CommandOption(label = "test", description = "Run the test suite", data = "test"),
                    CommandOption(label = "deploy", description = "Ship to production", data = "deploy"),
                    CommandOption(label = "clean", description = "Delete build artifacts", data = "clean"),
                ),
            inputValue = state.paletteInput,
            onInputTransform = { viewModel.sendIntent(HierarchyIntent.UpdatePalette(it)) },
            onOptionSelected = { viewModel.sendIntent(HierarchyIntent.RunCommand(it.label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A small file-system-like hierarchy demonstrating nested [TreeNode]s. */
private val PROJECT_TREE =
    listOf(
        TreeNode(
            "src",
            listOf(
                TreeNode(
                    "main",
                    listOf(
                        TreeNode("App.kt"),
                        TreeNode("Router.kt"),
                    ),
                ),
                TreeNode(
                    "test",
                    listOf(
                        TreeNode("AppTest.kt"),
                    ),
                ),
            ),
        ),
        TreeNode("build.gradle.kts"),
        TreeNode("README.md"),
    )
