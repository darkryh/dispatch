@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.layout.TerminalScreen
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.ComponentSections
import com.ead.dispatch.sample.navigation.ComponentsRoute
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.Button
import com.ead.dispatch.widget.ButtonStyle
import com.ead.dispatch.widget.CycleButton
import com.ead.dispatch.widget.IconButton
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.PasswordField
import com.ead.dispatch.widget.RadioButton
import com.ead.dispatch.widget.SectionHeader
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.ToggleButton

@Composable
fun ComponentsScreen(
    route: ComponentsRoute,
    viewModel: ComponentsViewModel = viewModel(),
) {
    when (route.section) {
        ComponentSections.CONTROLS -> ControlsGallery(viewModel)
        ComponentSections.SURFACES -> SurfacesGallery()
        ComponentSections.DATA -> DataGallery()
        ComponentSections.PROGRESS -> ProgressGallery()
        ComponentSections.LISTS -> ListsGallery()
        ComponentSections.REVIEW -> ReviewGallery()
        ComponentSections.WORKFLOW -> WorkflowGallery()
        else -> UnknownGallery(route.section)
    }
}

@Composable
private fun ControlsGallery(viewModel: ComponentsViewModel) {
    val isEnabled by viewModel.enabledState.collectAsState()
    val count by viewModel.count.collectAsState()
    val name by viewModel.name.collectAsState()
    val password by viewModel.password.collectAsState()
    val cycleValue by viewModel.cycleValue.collectAsState()
    val radioIndex by viewModel.radioIndex.collectAsState()

    GalleryScreen(title = "Controls", subtitle = "Tab moves the highlighted focus background") {
        SectionHeader("Text input")
        InputTextField(
            value = name,
            onValueChange = viewModel::updateName,
            modifier = Modifier.fillMaxWidth(),
            icon = "Name: ",
            placeholder = "Type here",
            maxLines = 2,
        )
        PasswordField(
            value = password,
            onValueChange = viewModel::updatePassword,
            icon = "Mask: ",
            placeholder = "Password display",
        )
        Text("Name: $name | Password length: ${password.length} | Count: $count")
        SectionHeader("Actions")
        Button("Outlined", onClick = viewModel::increment, style = ButtonStyle.Outlined)
        Button("Angled", onClick = viewModel::increment, style = ButtonStyle.Angled)
        Button("Filled", onClick = viewModel::increment, style = ButtonStyle.Filled)
        Button("Rounded", onClick = viewModel::increment, style = ButtonStyle.Rounded)
        Button("Text", onClick = viewModel::increment, style = ButtonStyle.Text)
        IconButton("+", onClick = viewModel::increment)
        ToggleButton(
            checked = isEnabled,
            onCheckedChange = { viewModel.toggle() },
            label = "Feature enabled",
        )
        RadioButton(selected = radioIndex == 0, onClick = { viewModel.selectRadio(0) }, label = "Alpha")
        RadioButton(selected = radioIndex == 1, onClick = { viewModel.selectRadio(1) }, label = "Beta")
        CycleButton(
            value = cycleValue,
            options = listOf("ALPHA", "BETA", "GAMMA"),
            onValueChange = viewModel::updateCycleValue,
            paddingVertical = 0,
        )
        Text("Toggle: $isEnabled | Radio: $radioIndex | Cycle: $cycleValue")
    }
}

@Composable
internal fun GalleryScreen(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current
    TerminalScreen(
        header = {
            Text("$title widget gallery", style = theme.primary)
            Text(subtitle, style = theme.muted)
            Spacer(Modifier.height(1))
        },
        footer = {
            Button("Back to catalogue", onClick = { navigator.popBackStack() })
            KeyHintBar(
                listOf(
                    KeyHint("Tab", "next focus"),
                    KeyHint("Shift+Q", "previous focus"),
                    KeyHint("Enter/Space", "activate"),
                ),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun UnknownGallery(section: String) {
    GalleryScreen("Unknown", "No catalogue section named '$section'") {
        Text("Return to the catalogue and choose a listed section.")
    }
}
