package com.ead.dispatch.sample.presentation.characters

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.popBackStack
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.sample.presentation.characters.components.AiCharacterForm
import com.ead.dispatch.sample.presentation.characters.components.CharacterFooter
import com.ead.dispatch.sample.presentation.characters.components.CharacterHeader
import com.ead.dispatch.sample.presentation.characters.components.CharacterSectionHeader
import com.ead.dispatch.sample.presentation.characters.components.ManualCharacterForm
import com.ead.dispatch.sample.presentation.characters.components.rememberCharacterScreenStyles
import com.ead.dispatch.sample.presentation.characters.event.CharacterEvent
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.sample.presentation.characters.state.CharacterFields
import com.ead.dispatch.sample.presentation.characters.util.CharacterUIMode
import com.ead.dispatch.sample.navigation.CharacterRoute
import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey

@Dispatchable
fun CharacterScreen(backStack: NavBackStack<NavKey>, route: CharacterRoute) {
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    val viewModel = viewModel<CharacterViewModel>()

    val uiState by viewModel.uiState.collectAsState()

    val styles = rememberCharacterScreenStyles()

    var showPhysicalFields by remember { mutableStateOf(false) }

    val fields = if (uiState.mode == CharacterUIMode.MANUAL) {
        if (showPhysicalFields) {
            CharacterFields.manual.filter { it.key in PHYSICAL_KEYS }
        } else {
            CharacterFields.manual.filter { it.key !in PHYSICAL_KEYS }
        }
    } else {
        CharacterFields.automatic
    }

    DisposableEffect(Unit) {
        val dispose = keyboardInterceptor.register { event ->
            when (event.key) {
                "Escape", "Esc" -> {
                    backStack.popBackStack()
                    true
                }
                "S", "s" -> if (event.ctrl) {
                    viewModel.onEvent(CharacterEvent.OnSave)
                    true
                } else {
                    false
                }
                "D", "d" -> if (event.ctrl) {
                    viewModel.onEvent(CharacterEvent.OnRequestDelete)
                    true
                } else {
                    false
                }
                "X", "x" -> if (event.ctrl) {
                    viewModel.onEvent(CharacterEvent.OnRequestDelete)
                    true
                } else {
                    false
                }
                "Delete" -> {
                    viewModel.onEvent(CharacterEvent.OnRequestDelete)
                    true
                }
                "Y", "y" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(CharacterEvent.OnConfirmDelete)
                    true
                } else {
                    false
                }
                "N", "n" -> if (uiState.confirmDelete) {
                    viewModel.onEvent(CharacterEvent.OnCancelDelete)
                    true
                } else {
                    false
                }
                "Tab" -> if (event.shift) {
                    viewModel.onEvent(CharacterEvent.OnToggleMode)
                    true
                } else {
                    false
                }
                "P", "p" -> if (event.ctrl && uiState.mode == CharacterUIMode.MANUAL) {
                    showPhysicalFields = !showPhysicalFields
                    true
                } else {
                    false
                }
                else -> false
            }
        }

        onDispose {
            dispose()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            CharacterHeader(
                title = if (uiState.mode == CharacterUIMode.MANUAL) {
                    "Character (Manual)"
                } else {
                    "Character (AI Prompt)"
                },
                styles = styles,
            )
        }
        item { Spacer(Modifier.height(1)) }

        if (uiState.isLoading) {
            item {
                CharacterFooter(
                    text = "Loading character...",
                    styles = styles,
                )
            }
            return@LazyColumn
        }

        val error = uiState.error
        if (error != null) {
            item {
                CharacterFooter(
                    text = "Error: $error",
                    styles = styles,
                )
            }
            return@LazyColumn
        }

        if (uiState.confirmDelete) {
            item {
                CharacterFooter(
                    text = "Delete this character? Press Y to confirm or N to cancel.",
                    styles = styles,
                )
            }
            item { Spacer(Modifier.height(1)) }
        }

        if (uiState.mode == CharacterUIMode.MANUAL) {
            item {
                CharacterSectionHeader(
                    title = if (showPhysicalFields) "Physical" else "Profile",
                    hint = "Tab next field. Shift+Tab switches mode. Ctrl+P toggles physical fields. Ctrl+S save. Ctrl+D delete. Esc back.",
                    styles = styles,
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                ManualCharacterForm(
                    fields = fields,
                    values = uiState.values,
                    styles = styles,
                    onValueChange = { key, text ->
                        viewModel.onEvent(CharacterEvent.OnFieldChanged(key, text))
                    }
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                CharacterFooter(
                    text = uiState.status ?: "Ctrl+S save · Ctrl+D delete · Ctrl+P physical · Esc back",
                    styles = styles,
                )
            }
        } else {
            item {
                AiCharacterForm(
                    fields = fields,
                    values = uiState.values,
                    styles = styles,
                    onValueChange = { key, text ->
                        viewModel.onEvent(CharacterEvent.OnFieldChanged(key, text))
                    }
                )
            }
            item { Spacer(Modifier.height(1)) }
            item {
                CharacterFooter(
                    text = uiState.status ?: "Ctrl+S save · Ctrl+D delete · Esc back",
                    styles = styles
                )
            }
        }
    }
}

private val PHYSICAL_KEYS = setOf(
    CharacterFieldKey.APPEARANCE,
    CharacterFieldKey.HEIGHT,
    CharacterFieldKey.BUILD,
    CharacterFieldKey.HAIR,
    CharacterFieldKey.EYES,
    CharacterFieldKey.SKIN_TONE,
    CharacterFieldKey.DISTINGUISHING_MARKS,
    CharacterFieldKey.STYLE_NOTES,
)
