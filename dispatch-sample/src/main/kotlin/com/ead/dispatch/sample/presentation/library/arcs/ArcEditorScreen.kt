package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.popBackStack
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.sample.navigation.ArcEditorRoute
import com.ead.dispatch.sample.presentation.editor.components.EditorFooter
import com.ead.dispatch.sample.presentation.editor.components.EditorHeader
import com.ead.dispatch.sample.presentation.editor.components.EditorSectionHeader
import com.ead.dispatch.sample.presentation.editor.components.ManualEditorForm
import com.ead.dispatch.sample.presentation.editor.components.rememberEditorScreenStyles
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn

@Dispatchable
fun ArcEditorScreen(backStack: NavBackStack<NavKey>, route: ArcEditorRoute) {
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<ArcEditorViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val styles = rememberEditorScreenStyles()

    DisposableEffect(Unit) {
        val dispose = keyboardInterceptor.register { event ->
            when (event.key) {
                "Escape", "Esc" -> {
                    backStack.popBackStack()
                    true
                }
                "S", "s" -> if (event.ctrl) {
                    viewModel.onEvent(ArcEditorEvent.OnSave)
                    true
                } else false
                "D", "d" -> if (event.ctrl) {
                    viewModel.onEvent(ArcEditorEvent.OnRequestDelete)
                    true
                } else false
                "X", "x" -> if (event.ctrl) {
                    viewModel.onEvent(ArcEditorEvent.OnRequestDelete)
                    true
                } else false
                "Delete" -> {
                    viewModel.onEvent(ArcEditorEvent.OnRequestDelete)
                    true
                }
                "Y", "y" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(ArcEditorEvent.OnConfirmDelete)
                    true
                } else false
                "N", "n" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(ArcEditorEvent.OnCancelDelete)
                    true
                } else false
                else -> false
            }
        }
        onDispose { dispose() }
    }

    val manualFields = ArcEditorFields.manual

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            EditorHeader(title = "Arc", styles = styles)
        }
        item { Spacer(Modifier.height(1)) }

        if (uiState.isLoading) {
            item { EditorFooter(text = "Loading arc...", styles = styles) }
            return@LazyColumn
        }

        val error = uiState.error
        if (error != null) {
            item { EditorFooter(text = "Error: $error", styles = styles) }
            return@LazyColumn
        }

        if (uiState.confirmDelete) {
            item {
                EditorFooter(text = "Delete this arc? Press Y to confirm or N to cancel.", styles = styles)
            }
            item { Spacer(Modifier.height(1)) }
        }

        item {
            EditorSectionHeader(
                title = "Profile",
                hint = "Tab next field. Ctrl+S save. Ctrl+D delete. Esc back.",
                styles = styles,
            )
        }
        item { Spacer(Modifier.height(1)) }
        item {
            ManualEditorForm(
                fields = manualFields,
                values = uiState.values,
                styles = styles,
                onValueChange = { key, text ->
                    viewModel.onEvent(ArcEditorEvent.OnFieldChanged(key, text))
                },
            )
        }
        item { Spacer(Modifier.height(1)) }
        item {
            EditorFooter(
                text = uiState.status ?: "Ctrl+S save · Ctrl+D delete · Esc back",
                styles = styles,
            )
        }
    }
}
