package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

data class DecisionOption(
    val label: String,
)

sealed interface DecisionSelection {
    data class Option(
        val option: DecisionOption,
    ) : DecisionSelection

    data class Custom(
        val text: String,
    ) : DecisionSelection
}

data class DecisionPromptTextStyles(
    val question: TextStyle? = rgb("#FFFFFF"),
    val option: TextStyle? = rgb("#FFFFFF"),
    val selectedOption: TextStyle? = rgb("#FFFFFF") + TextStyle(bold = true),
    val prefix: TextStyle? = rgb("#6F7279"),
    val selectedPrefix: TextStyle? = rgb("#00BFFF"),
    val placeholder: TextStyle? = rgb("#898D92"),
    val customText: TextStyle? = rgb("#FFFFFF"),
)

class DecisionPromptState(
    initialSelectedIndex: Int = 0,
    initialCustomText: String = "",
) {
    var selectedIndex: Int by mutableStateOf(initialSelectedIndex)
    var customText: String by mutableStateOf(initialCustomText)

    internal fun clampSelection(optionsCount: Int) {
        selectedIndex = clampDecisionSelectionIndex(selectedIndex, optionsCount)
    }

    internal fun moveUp(optionsCount: Int) {
        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
        clampSelection(optionsCount)
    }

    internal fun moveDown(optionsCount: Int) {
        selectedIndex = (selectedIndex + 1).coerceAtMost(optionsCount)
        clampSelection(optionsCount)
    }
}

@Composable
fun rememberDecisionPromptState(
    initialSelectedIndex: Int = 0,
    initialCustomText: String = "",
): DecisionPromptState =
    remember(
        DecisionPromptStateKey(
            initialSelectedIndex = initialSelectedIndex,
            initialCustomText = initialCustomText,
        ),
    ) {
        DecisionPromptState(
            initialSelectedIndex = initialSelectedIndex,
            initialCustomText = initialCustomText,
        )
    }

private data class DecisionPromptStateKey(
    val initialSelectedIndex: Int,
    val initialCustomText: String,
)

@Composable
fun DecisionPrompt(
    question: String,
    options: List<DecisionOption>,
    onSubmit: (DecisionSelection) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "type a custom response…",
    visible: Boolean = true,
    enabled: Boolean = true,
    selectionIndicator: String = "> ",
    unselectedIndicator: String = "  ",
    state: DecisionPromptState = rememberDecisionPromptState(),
    textStyles: DecisionPromptTextStyles = DecisionPromptTextStyles(),
) {
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    val optionsCount = options.size
    state.clampSelection(optionsCount)
    val customIndex = optionsCount
    val isCustomSelected = state.selectedIndex == customIndex
    val interactive = visible && enabled

    DisposableEffect(listOf(interactive, optionsCount, keyboardInterceptor)) {
        if (!interactive) {
            return@DisposableEffect onDispose {}
        }

        val interceptorDispose =
            keyboardInterceptor.register { event ->
                handleSelectorKeyEvent(
                    event,
                    SelectorKeyBindings(
                        onMoveUp = { state.moveUp(optionsCount) },
                        onMoveDown = { state.moveDown(optionsCount) },
                        onConfirm = {
                            if (state.selectedIndex == customIndex) {
                                val custom = state.customText.trim()
                                if (custom.isEmpty()) {
                                    return@SelectorKeyBindings false
                                }
                                onSubmit(DecisionSelection.Custom(custom))
                                state.customText = ""
                                true
                            } else {
                                val option =
                                    options.getOrNull(state.selectedIndex)
                                        ?: return@SelectorKeyBindings false
                                onSubmit(DecisionSelection.Option(option))
                                true
                            }
                        },
                        onBackspace = {
                            if (state.selectedIndex != customIndex) return@SelectorKeyBindings false
                            if (state.customText.isNotEmpty()) {
                                state.customText = state.customText.dropLast(1)
                            }
                            true
                        },
                        onCharacter = { char ->
                            if (state.selectedIndex != customIndex) return@SelectorKeyBindings false
                            if (char.isISOControl()) return@SelectorKeyBindings false
                            state.customText += char
                            true
                        },
                        onTab = {
                            state.selectedIndex = customIndex
                            true
                        },
                    ),
                )
            }

        onDispose { interceptorDispose() }
    }

    if (!visible) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Text(
                text = question,
                style = textStyles.question,
            )
        }

        options.forEachIndexed { index, option ->
            val selected = state.selectedIndex == index
            Row {
                Text(
                    text = if (selected) selectionIndicator else unselectedIndicator,
                    style = if (selected) textStyles.selectedPrefix else textStyles.prefix,
                )
                Text(
                    text = "${decisionOptionMarker(index)}) ${option.label}",
                    style = if (selected) textStyles.selectedOption else textStyles.option,
                )
            }
        }

        Row {
            Text(
                text = if (isCustomSelected) selectionIndicator else unselectedIndicator,
                style = if (isCustomSelected) textStyles.selectedPrefix else textStyles.prefix,
            )
            Spacer(modifier = Modifier.width(1))
            val hasCustomText = state.customText.isNotEmpty()
            val renderedCustom =
                when {
                    hasCustomText && isCustomSelected -> "${state.customText}█"
                    hasCustomText -> state.customText
                    else -> placeholder
                }
            Text(
                text = renderedCustom,
                style =
                    when {
                        hasCustomText -> textStyles.customText
                        else -> textStyles.placeholder
                    },
            )
        }
    }
}

internal fun decisionOptionMarker(index: Int): String {
    require(index >= 0) { "Index must be >= 0" }
    return if (index < 26) {
        ('a'.code + index).toChar().toString()
    } else {
        (index + 1).toString()
    }
}

internal fun clampDecisionSelectionIndex(
    selectedIndex: Int,
    optionsCount: Int,
): Int {
    require(optionsCount >= 0) { "optionsCount must be >= 0" }
    return selectedIndex.coerceIn(0, optionsCount)
}
