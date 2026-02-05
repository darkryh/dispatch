package com.ead.dispatch.sample.presentation.library.events

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
import com.ead.dispatch.sample.navigation.EventEditorRoute
import com.ead.dispatch.sample.presentation.editor.components.AiEditorForm
import com.ead.dispatch.sample.presentation.editor.components.EditorFooter
import com.ead.dispatch.sample.presentation.editor.components.EditorHeader
import com.ead.dispatch.sample.presentation.editor.components.EditorSectionHeader
import com.ead.dispatch.sample.presentation.editor.components.ManualEditorForm
import com.ead.dispatch.sample.presentation.editor.components.rememberEditorScreenStyles
import com.ead.dispatch.sample.presentation.editor.model.EditorAiFields
import com.ead.dispatch.sample.presentation.editor.model.EditorMode
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn

@Dispatchable
fun EventEditorScreen(backStack: NavBackStack<NavKey>, route: EventEditorRoute) {
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<EventEditorViewModel>()
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
                    viewModel.onEvent(EventEditorEvent.OnSave)
                    true
                } else false
                "D", "d" -> if (event.ctrl) {
                    viewModel.onEvent(EventEditorEvent.OnRequestDelete)
                    true
                } else false
                "X", "x" -> if (event.ctrl) {
                    viewModel.onEvent(EventEditorEvent.OnRequestDelete)
                    true
                } else false
                "Delete" -> {
                    viewModel.onEvent(EventEditorEvent.OnRequestDelete)
                    true
                }
                "Y", "y" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(EventEditorEvent.OnConfirmDelete)
                    true
                } else false
                "N", "n" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(EventEditorEvent.OnCancelDelete)
                    true
                } else false
                "Tab" -> if (event.shift) {
                    viewModel.onEvent(EventEditorEvent.OnToggleMode)
                    true
                } else false
                "G", "g" -> if (event.ctrl && uiState.mode == EditorMode.AUTOMATIC) {
                    viewModel.onEvent(EventEditorEvent.OnGenerateAiDraft)
                    true
                } else false
                "A", "a" -> if (event.ctrl && uiState.mode == EditorMode.AUTOMATIC) {
                    viewModel.onEvent(EventEditorEvent.OnApplyAiDraft)
                    true
                } else false
                "R", "r" -> if (event.ctrl && uiState.mode == EditorMode.AUTOMATIC) {
                    viewModel.onEvent(EventEditorEvent.OnRegenerateAiDraft)
                    true
                } else false
                "Q", "q" -> if (event.ctrl && uiState.mode == EditorMode.AUTOMATIC) {
                    viewModel.onEvent(EventEditorEvent.OnToggleAiMode)
                    true
                } else false
                else -> false
            }
        }
        onDispose { dispose() }
    }

    val manualFields = EventEditorFields.manual
    val automaticFields = EditorAiFields.fields

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            EditorHeader(
                title = if (uiState.mode == EditorMode.MANUAL) "Event (Manual)" else "Event (AI Prompt)",
                styles = styles,
            )
        }
        item { Spacer(Modifier.height(1)) }

        if (uiState.isLoading) {
            item { EditorFooter(text = "Loading event...", styles = styles) }
            return@LazyColumn
        }

        val error = uiState.error
        if (error != null) {
            item { EditorFooter(text = "Error: $error", styles = styles) }
            return@LazyColumn
        }

        if (uiState.confirmDelete) {
            item {
                EditorFooter(text = "Delete this event? Press Y to confirm or N to cancel.", styles = styles)
            }
            item { Spacer(Modifier.height(1)) }
        }

        if (uiState.mode == EditorMode.MANUAL) {
            item {
                EditorSectionHeader(
                    title = "Profile",
                    hint = "Tab next field. Shift+Tab switches mode. Ctrl+S save. Ctrl+D delete. Esc back.",
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
                        viewModel.onEvent(EventEditorEvent.OnFieldChanged(key, text))
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
        } else {
            val modeLabel = uiState.aiMode.name.lowercase().replaceFirstChar { it.uppercase() }
            item {
                EditorSectionHeader(
                    title = "AI Prompt ($modeLabel)",
                    hint = "Ctrl+G generate · Ctrl+A apply · Ctrl+R regenerate · Ctrl+Q mode · Shift+Tab manual · Esc back",
                    styles = styles,
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                AiEditorForm(
                    fields = automaticFields,
                    values = uiState.values,
                    styles = styles,
                    onValueChange = { key, text ->
                        viewModel.onEvent(EventEditorEvent.OnFieldChanged(key, text))
                    },
                )
            }
            val draft = uiState.aiDraft
            if (draft != null) {
                item { Spacer(Modifier.height(1)) }
                item {
                    EditorSectionHeader(
                        title = "AI Draft",
                        hint = "Ctrl+A apply · Ctrl+R regenerate",
                        styles = styles,
                    )
                }
                item { Spacer(Modifier.height(1)) }
                item { EventAIDraftPreview(draft = draft, styles = styles) }
            }
            item { Spacer(Modifier.height(1)) }
            item {
                EditorFooter(
                    text = uiState.status ?: when {
                        uiState.isGenerating -> "Generating draft..."
                        uiState.aiError != null -> "AI error: ${uiState.aiError}"
                        else -> "Ctrl+G generate · Ctrl+A apply · Ctrl+R regenerate · Ctrl+Q mode · Esc back"
                    },
                    styles = styles,
                )
            }
        }
    }
}
