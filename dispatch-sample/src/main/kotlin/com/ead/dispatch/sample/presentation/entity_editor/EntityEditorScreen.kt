package com.ead.dispatch.sample.presentation.entity_editor

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
import com.ead.dispatch.sample.navigation.EntityEditorRoute
import com.ead.dispatch.sample.presentation.entity_editor.components.AutomaticEntityForm
import com.ead.dispatch.sample.presentation.entity_editor.components.EntityFooter
import com.ead.dispatch.sample.presentation.entity_editor.components.EntityHeader
import com.ead.dispatch.sample.presentation.entity_editor.components.EntitySectionHeader
import com.ead.dispatch.sample.presentation.entity_editor.components.ManualEntityForm
import com.ead.dispatch.sample.presentation.entity_editor.components.rememberEntityScreenStyles
import com.ead.dispatch.sample.presentation.entity_editor.event.EntityEditorEvent
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityEditorFields
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityEditorMode
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn

@Dispatchable
fun EntityEditorScreen(backStack: NavBackStack<NavKey>, route: EntityEditorRoute) {
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<EntityEditorViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val styles = rememberEntityScreenStyles()

    val entityTitle = uiState.type?.title ?: "Entity"
    val manualFields = uiState.type?.let { EntityEditorFields.manualFieldsFor(it) } ?: emptyList()
    val automaticFields = EntityEditorFields.automatic

    DisposableEffect(Unit) {
        val dispose = keyboardInterceptor.register { event ->
            when (event.key) {
                "Escape", "Esc" -> {
                    backStack.popBackStack()
                    true
                }
                "S", "s" -> if (event.ctrl) {
                    viewModel.onEvent(EntityEditorEvent.OnSave)
                    true
                } else {
                    false
                }
                "D", "d" -> if (event.ctrl) {
                    viewModel.onEvent(EntityEditorEvent.OnRequestDelete)
                    true
                } else {
                    false
                }
                "X", "x" -> if (event.ctrl) {
                    viewModel.onEvent(EntityEditorEvent.OnRequestDelete)
                    true
                } else {
                    false
                }
                "Delete" -> {
                    viewModel.onEvent(EntityEditorEvent.OnRequestDelete)
                    true
                }
                "Y", "y" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(EntityEditorEvent.OnConfirmDelete)
                    true
                } else {
                    false
                }
                "N", "n" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(EntityEditorEvent.OnCancelDelete)
                    true
                } else {
                    false
                }
                "Tab" -> if (event.shift) {
                    viewModel.onEvent(EntityEditorEvent.OnToggleMode)
                    true
                } else {
                    false
                }
                else -> false
            }
        }

        onDispose { dispose() }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            EntityHeader(
                title = if (uiState.mode == EntityEditorMode.MANUAL) {
                    "${entityTitle} (Manual)"
                } else {
                    "${entityTitle} (AI Prompt)"
                },
                styles = styles,
            )
        }
        item { Spacer(Modifier.height(1)) }

        if (uiState.isLoading) {
            item {
                EntityFooter(
                    text = "Loading ${entityTitle.lowercase()}...",
                    styles = styles,
                )
            }
            return@LazyColumn
        }

        val error = uiState.error
        if (error != null) {
            item {
                EntityFooter(
                    text = "Error: $error",
                    styles = styles,
                )
            }
            return@LazyColumn
        }

        if (uiState.confirmDelete) {
            item {
                EntityFooter(
                    text = "Delete this ${entityTitle.lowercase()}? Press Y to confirm or N to cancel.",
                    styles = styles,
                )
            }
            item { Spacer(Modifier.height(1)) }
        }

        if (uiState.mode == EntityEditorMode.MANUAL) {
            item {
                EntitySectionHeader(
                    title = "Profile",
                    hint = "Tab next field. Shift+Tab switches mode. Ctrl+S save. Ctrl+D delete. Esc back.",
                    styles = styles,
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                ManualEntityForm(
                    fields = manualFields,
                    values = uiState.values,
                    styles = styles,
                    onValueChange = { key, text ->
                        viewModel.onEvent(EntityEditorEvent.OnFieldChanged(key, text))
                    }
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                EntityFooter(
                    text = uiState.status ?: "Ctrl+S save · Ctrl+D delete · Esc back",
                    styles = styles,
                )
            }
        } else {
            item {
                EntitySectionHeader(
                    title = "AI Prompt",
                    hint = "UI only · Shift+Tab manual · Esc back",
                    styles = styles,
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                AutomaticEntityForm(
                    fields = automaticFields,
                    values = uiState.values,
                    styles = styles,
                    onValueChange = { key, text ->
                        viewModel.onEvent(EntityEditorEvent.OnFieldChanged(key, text))
                    }
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                EntityFooter(
                    text = uiState.status ?: "AI generation not wired yet · Shift+Tab manual · Esc back",
                    styles = styles,
                )
            }
        }
    }
}
