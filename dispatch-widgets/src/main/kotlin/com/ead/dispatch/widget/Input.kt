package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.modifier.focusable
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.composableWidget
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Whitespace

/**
 * A text input field with built-in keyboard handling.
 *
 * Example:
 * ```kotlin
 * var text by remember { mutableStateOf("") }
 * TextField(
 *     value = text,
 *     onValueChange = { text = it },
 *     placeholder = "Enter text...",
 *     onSubmit = { submittedText ->
 *         // handle submitted text
 *     }
 * )
 * ```
 *
 * This is a pure display widget: it does not handle keyboard input. [onValueChange]
 * is accepted only so the value-based form mirrors the [TextField] (state) and
 * [InputTextField] signatures; this overload never invokes it. Use [InputTextField]
 * for interactive editing.
 *
 * @param value Current text value.
 * @param onValueChange Display-only no-op kept for signature symmetry; never invoked here.
 * @param modifier Modifiers to apply.
 * @param icon Leading icon/prefix that is always shown (e.g. a prompt like `"> "`).
 * @param placeholder Placeholder text when empty.
 * @param enabled Whether the field is enabled.
 * @param singleLine Whether to restrict to a single line.
 * @param showCursor Whether to show a cursor at the end of text.
 * @param cursorChar Character to use for the cursor.
 * @param maxLines Optional maximum number of lines to render (null = no limit within constraints).
 * @param cursorPosition Cursor position (character index) within [value].
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    cursorPosition: Int = value.length,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
) {
    val terminal = LocalTerminal.current
    composableWidget("TextField") {
        TextFieldMeasurable(
            value = value,
            icon = icon,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = singleLine,
            modifier = modifier,
            showCursor = showCursor && enabled,
            cursorChar = cursorChar,
            cursorPosition = cursorPosition,
            maxLines = maxLines,
            terminal = terminal,
            textStyle = textStyle,
            placeholderStyle = placeholderStyle,
            iconStyle = iconStyle,
        )
    }
}

/**
 * Measurable for TextField.
 */
internal class TextFieldMeasurable(
    private val value: String,
    private val icon: String,
    private val placeholder: String,
    private val enabled: Boolean,
    private val singleLine: Boolean,
    override val modifier: Modifier,
    private val showCursor: Boolean = true,
    private val cursorChar: String = "█",
    private val cursorPosition: Int = value.length,
    private val maxLines: Int? = null,
    private val terminal: com.github.ajalt.mordant.terminal.Terminal,
    private val textStyle: TextStyle? = null,
    private val placeholderStyle: TextStyle? = null,
    private val iconStyle: TextStyle? = null,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val isPlaceholder = value.isEmpty()
        val baseText = if (isPlaceholder) placeholder else value
        val textForRender = baseText
        val cursorToken = cursorChar.ifEmpty { "█" }

        val iconWidth =
            if (icon.isEmpty()) {
                0
            } else {
                // Measure the display width in terminal cells (handles wide/combining chars).
                com.github.ajalt.mordant.widgets
                    .Text(
                        icon,
                        whitespace = Whitespace.PRE,
                    ).render(terminal, width = 1_000)
                    .width
            }

        val renderWidth =
            if (modifiedConstraints.hasBoundedWidth) {
                modifiedConstraints.maxWidth
            } else {
                (iconWidth + textForRender.length).coerceAtLeast(10)
            }

        val contentWidth = (renderWidth - iconWidth).coerceAtLeast(1)

        val renderedContent =
            terminal.render(
                textForRender,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD, // break long words to new lines
                width = contentWidth,
            )

        var contentLines = renderedContent.lines()

        val cursorVisual =
            if (showCursor && !isPlaceholder) {
                val clampedCursor = cursorPosition.coerceIn(0, baseText.length)
                computeCursorVisual(baseText, clampedCursor, contentWidth)
            } else {
                null
            }

        // Apply line limit, keeping cursor visible. `singleLine` implies a 1-line window.
        val maxByParam = if (singleLine) 1 else maxLines?.coerceAtLeast(1)
        var cursorVisualInWindow = cursorVisual
        if (maxByParam != null && contentLines.size > maxByParam) {
            val cursorLine = cursorVisual?.line ?: if (showCursor && isPlaceholder) 0 else (contentLines.size - 1)
            val halfWindow = maxByParam / 2
            var start = (cursorLine - halfWindow).coerceAtLeast(0)
            var end = start + maxByParam
            if (end > contentLines.size) {
                end = contentLines.size
                start = (end - maxByParam).coerceAtLeast(0)
            }
            contentLines = contentLines.subList(start, end)
            cursorVisualInWindow = cursorVisual?.let { it.copy(line = (it.line - start).coerceAtLeast(0)) }
        }

        if (cursorVisualInWindow != null) {
            contentLines =
                applyCursorOverlay(
                    lines = contentLines,
                    cursor = cursorVisualInWindow,
                    cursorChar = cursorToken,
                    contentWidth = contentWidth,
                )
        } else if (showCursor && isPlaceholder) {
            contentLines =
                applyPlaceholderCursorPrefix(
                    lines = contentLines,
                    cursorChar = cursorToken,
                    contentWidth = contentWidth,
                )
        }

        val contentTextStyle = if (isPlaceholder) placeholderStyle else textStyle
        val styledContentLines =
            if (contentTextStyle == null) {
                contentLines
            } else {
                contentLines.mapIndexed { index, line ->
                    if (isPlaceholder && showCursor && cursorToken.isNotEmpty() && index == 0 && line.startsWith(cursorToken)) {
                        cursorToken + contentTextStyle.invoke(line.removePrefix(cursorToken))
                    } else {
                        contentTextStyle.invoke(line)
                    }
                }
            }

        val lines =
            if (iconWidth == 0) {
                styledContentLines
            } else {
                val styledIcon = iconStyle?.invoke(icon) ?: icon
                val indent = " ".repeat(iconWidth)
                val styledIndent = iconStyle?.invoke(indent) ?: indent
                styledContentLines.mapIndexed { index, line ->
                    if (index == 0) styledIcon + line else styledIndent + line
                }
            }

        // For input fields, don't constrain by viewport maxHeight - use natural height
        // This allows the active area system to handle scrolling
        val height = lines.size.coerceAtLeast(1)

        return SimplePlaceable(
            width = modifiedConstraints.constrainWidth(renderWidth),
            height = height,
            lines = lines,
        )
    }

    private data class CursorVisual(
        val line: Int,
        val col: Int,
    )

    private fun computeCursorVisual(
        text: String,
        cursorPos: Int,
        width: Int,
    ): CursorVisual {
        val safePos = cursorPos.coerceIn(0, text.length)
        val prefixText = text.substring(0, safePos)
        val marker = CURSOR_MARKER
        val suffixSpan = spanSuffixFrom(text, safePos)
        val renderedPrefix =
            terminal.render(
                prefixText + marker + suffixSpan,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD,
                width = width,
            )
        val prefixLines = renderedPrefix.lines()
        val markerLineIndex =
            prefixLines.indexOfFirst { it.contains(marker) }.let { index ->
                if (index == -1) prefixLines.lastIndex.coerceAtLeast(0) else index
            }
        val markerLine = prefixLines.getOrNull(markerLineIndex).orEmpty()
        val markerCol =
            markerLine.indexOf(marker).let { index ->
                if (index == -1) markerLine.length else index
            }

        return CursorVisual(line = markerLineIndex, col = markerCol)
    }

    private fun applyCursorOverlay(
        lines: List<String>,
        cursor: CursorVisual,
        cursorChar: String,
        contentWidth: Int,
    ): List<String> {
        val mutable = lines.toMutableList()
        while (mutable.size <= cursor.line) mutable.add("")

        val lineIndex = cursor.line.coerceIn(0, mutable.lastIndex)
        val colIndex = cursor.col.coerceIn(0, (contentWidth - 1).coerceAtLeast(0))
        val rawLine = mutable[lineIndex]

        val paddedLine = rawLine.padEnd(colIndex + 1)
        val safeChar = cursorChar.ifEmpty { "█" }
        val head = paddedLine.substring(0, colIndex)
        val tail = if (colIndex + 1 <= paddedLine.length) paddedLine.substring(colIndex + 1) else ""
        mutable[lineIndex] = head + safeChar + tail

        return mutable
    }

    private fun applyPlaceholderCursorPrefix(
        lines: List<String>,
        cursorChar: String,
        contentWidth: Int,
    ): List<String> {
        val mutable = lines.toMutableList()
        if (mutable.isEmpty()) {
            return listOf(cursorChar)
        }

        val firstLine = mutable.first()
        val withCursor = cursorChar + firstLine
        mutable[0] =
            if (withCursor.length > contentWidth) {
                withCursor.take(contentWidth)
            } else {
                withCursor
            }
        return mutable
    }
}

/** Minimal mutable cell for sharing the latest composed value with lazy editor providers. */
private class Holder<T>(
    var value: T,
)

/**
 * Input state for managing text field state.
 */
class TextFieldState(
    initialValue: String = "",
) {
    /**
     * Current text value.
     */
    var value: String by mutableStateOf(initialValue)

    /**
     * Cursor position (character index).
     */
    var cursorPosition: Int by mutableStateOf(initialValue.length)

    /**
     * Selection start (if any).
     */
    var selectionStart: Int? by mutableStateOf(null)

    /**
     * Selection end (if any).
     */
    var selectionEnd: Int? by mutableStateOf(null)

    /**
     * Whether the field has focus.
     */
    var hasFocus: Boolean by mutableStateOf(false)

    /**
     * Insert text at cursor position.
     */
    fun insert(text: String) {
        val before = value.substring(0, cursorPosition.coerceIn(0, value.length))
        val after = value.substring(cursorPosition.coerceIn(0, value.length))
        value = before + text + after
        cursorPosition += text.length
        clearSelection()
    }

    /**
     * Delete character before cursor (backspace).
     */
    fun deleteBackward() {
        if (hasSelection()) {
            deleteSelection()
        } else if (cursorPosition > 0) {
            val before = value.substring(0, cursorPosition - 1)
            val after = value.substring(cursorPosition)
            value = before + after
            cursorPosition--
        }
    }

    /**
     * Delete character at cursor (delete).
     */
    fun deleteForward() {
        if (hasSelection()) {
            deleteSelection()
        } else if (cursorPosition < value.length) {
            val before = value.substring(0, cursorPosition)
            val after = value.substring(cursorPosition + 1)
            value = before + after
        }
    }

    /**
     * Move cursor left.
     */
    fun moveCursorLeft() {
        cursorPosition = (cursorPosition - 1).coerceAtLeast(0)
        clearSelection()
    }

    /**
     * Move cursor right.
     */
    fun moveCursorRight() {
        cursorPosition = (cursorPosition + 1).coerceAtMost(value.length)
        clearSelection()
    }

    /**
     * Move cursor to start.
     */
    fun moveCursorToStart() {
        cursorPosition = 0
        clearSelection()
    }

    /**
     * Move cursor to end.
     */
    fun moveCursorToEnd() {
        cursorPosition = value.length
        clearSelection()
    }

    /**
     * Check if there's a selection.
     */
    fun hasSelection(): Boolean = selectionStart != null && selectionEnd != null && selectionStart != selectionEnd

    /**
     * Clear selection.
     */
    fun clearSelection() {
        selectionStart = null
        selectionEnd = null
    }

    /**
     * Delete selected text.
     */
    fun deleteSelection() {
        if (!hasSelection()) return

        val start = minOf(selectionStart!!, selectionEnd!!)
        val end = maxOf(selectionStart!!, selectionEnd!!)

        val before = value.substring(0, start)
        val after = value.substring(end)
        value = before + after
        cursorPosition = start
        clearSelection()
    }

    /**
     * Clear all text.
     */
    fun clear() {
        value = ""
        cursorPosition = 0
        clearSelection()
    }
}

/**
 * Remember a text field state.
 */
@Composable
fun rememberTextFieldState(initialValue: String = ""): TextFieldState = remember { TextFieldState(initialValue) }

class InputHistoryIndexState {
    var index: Int by mutableStateOf(-1)
    var draft: String? by mutableStateOf(null)
}

@Composable
fun rememberInputHistoryIndexState(): InputHistoryIndexState = remember { InputHistoryIndexState() }

/**
 * A text field that uses TextFieldState for full control.
 */
@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
) {
    TextField(
        value = state.value,
        onValueChange = { state.value = it },
        modifier = modifier,
        icon = icon,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = singleLine,
        showCursor = showCursor,
        cursorChar = cursorChar,
        cursorPosition = state.cursorPosition,
        textStyle = textStyle,
        placeholderStyle = placeholderStyle,
        iconStyle = iconStyle,
    )
}

/**
 * A password input field.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "Password",
    enabled: Boolean = true,
    maskChar: Char = '•',
) {
    InputTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        icon = icon,
        placeholder = placeholder,
        enabled = enabled,
        maxLines = 1,
        maskChar = maskChar,
    )
}

/**
 * A text input field with automatic keyboard handling.
 *
 * This is a convenience wrapper around [TextField] that automatically handles
 * keyboard events (Enter, Backspace, printable characters) via [DispatchScope].
 *
 * Example:
 * ```kotlin
 * var text by remember { mutableStateOf("") }
 * InputTextField(
 *     value = text,
 *     onValueChange = { text = it },
 *     icon = "> ",
 *     onSubmit = { submittedText ->
 *         processMessage(submittedText)
 *         text = "" // Clear after submit
 *     }
 * )
 * ```
 *
 * @param value Current text value.
 * @param onValueChange Callback when text changes (for all keystrokes).
 * @param modifier Modifiers to apply.
 * @param icon Leading icon/prefix that is always shown (e.g. a prompt like `"> "`).
 * @param placeholder Placeholder text when empty.
 * @param enabled Whether the field accepts input.
 * @param onSubmit Callback when Enter is pressed. Receives the current text value.
 * @param showCursor Whether to show a cursor at the end of text.
 * @param cursorChar Character to use for the cursor.
 */
@Composable
fun InputTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
    cursorPosition: Int? = null,
    onCursorPositionChange: ((Int) -> Unit)? = null,
    historyItems: List<String> = emptyList(),
    historyIndexState: InputHistoryIndexState? = null,
    maskChar: Char? = null,
) {
    val onValueChangeCallback =
        com.ead.dispatch.runtime
            .rememberCallback(onValueChange)
    val onSubmitCallback =
        com.ead.dispatch.runtime
            .rememberCallback(onSubmit)
    val onCursorPositionChangeCallback =
        com.ead.dispatch.runtime
            .rememberCallback(onCursorPositionChange)

    val terminal = LocalTerminal.current
    val terminalWidth = LocalTerminalWidth.current
    val iconWidth =
        remember(icon) {
            if (icon.isEmpty()) {
                0
            } else {
                com.github.ajalt.mordant.widgets
                    .Text(
                        icon,
                        whitespace = Whitespace.PRE,
                    ).render(terminal, width = 1_000)
                    .width
            }
        }
    val contentWidth = (terminalWidth - iconWidth).coerceAtLeast(1)

    // Plain (non-snapshot) holders so the editor's lazy providers always read the latest
    // composed values without re-wrapping closures or triggering recomposition.
    val historyItemsHolder = remember { Holder(historyItems) }
    historyItemsHolder.value = historyItems
    val contentWidthHolder = remember { Holder(contentWidth) }
    contentWidthHolder.value = contentWidth

    // Keep local value and cursor so we can handle edits even between recompositions.
    var latestValue by remember { mutableStateOf(value) }
    var cursorPositionState by remember { mutableStateOf(cursorPosition ?: value.length) }
    val pasteTracker = remember { PasteTracker() }
    val pasteHeuristic = remember { PasteHeuristic(inputNowNanos) }
    val resolvedHistoryIndexState = historyIndexState ?: remember { InputHistoryIndexState() }
    syncHistoryState(historyItems, latestValue, resolvedHistoryIndexState)

    // Track external value changes without making them the source of truth for rendering.
    // This avoids dropping keystrokes when `value` is backed by an async flow collector.
    // Reconciliation runs in a SideEffect so it does not mutate snapshot state during the
    // composable body, which would otherwise amplify recomposition.
    val externalValueTracker = remember { ExternalValueTracker(value) }
    val externalCursorTracker = remember { ExternalValueTracker(cursorPosition ?: value.length) }
    androidx.compose.runtime.SideEffect {
        if (value != externalValueTracker.value) {
            externalValueTracker.value = value
            if (!externalValueTracker.acknowledge(value) && value != latestValue) {
                externalValueTracker.clearPending()
                latestValue = value
                cursorPositionState = (cursorPosition ?: value.length).coerceIn(0, value.length)
            } else {
                cursorPositionState = cursorPositionState.coerceIn(0, latestValue.length)
            }
        }
        if (cursorPosition != null && cursorPosition != externalCursorTracker.value) {
            externalCursorTracker.value = cursorPosition
            if (!externalCursorTracker.acknowledge(cursorPosition)) {
                externalCursorTracker.clearPending()
                cursorPositionState = cursorPosition.coerceIn(0, latestValue.length)
            }
        }
    }

    // Set up keyboard handling via KeyboardInterceptor.
    // Always register a handler so we don't keep a stale handler when `enabled` flips to false.
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val focusRegistry = LocalFocusRegistry.current
    val focusToken = remember { Any() }
    val focusableModifier = if (enabled) modifier.focusable(focusToken) else modifier

    val isFocused = enabled && focusRegistry.isFocused(focusToken)

    val editor =
        remember(resolvedHistoryIndexState) {
            InputEditor(
                getValue = { latestValue },
                setValue = { latestValue = it },
                getCursor = { cursorPositionState },
                setCursor = { cursorPositionState = it },
                historyIndexState = resolvedHistoryIndexState,
                pasteTracker = pasteTracker,
                pasteHeuristic = pasteHeuristic,
            )
        }
    // Stable wrappers: the underlying callbacks are already rememberCallback'd, so these
    // closures never need to be reallocated. Re-wrapping them every frame would defeat that.
    val editorOnValueChange =
        remember(editor, externalValueTracker, onValueChangeCallback) {
            { nextValue: String ->
                externalValueTracker.expect(nextValue)
                onValueChangeCallback(nextValue)
            }
        }
    val editorOnCursorPositionChange =
        remember(editor, externalCursorTracker, onCursorPositionChangeCallback) {
            { nextPosition: Int ->
                externalCursorTracker.expect(nextPosition)
                onCursorPositionChangeCallback?.invoke(nextPosition)
                Unit
            }
        }
    val historyItemsProvider = remember(historyItemsHolder) { { historyItemsHolder.value } }
    val contentWidthProvider = remember(contentWidthHolder) { { contentWidthHolder.value } }
    editor.updateDependencies(
        onValueChange = editorOnValueChange,
        onSubmit = onSubmitCallback,
        onCursorPositionChange = editorOnCursorPositionChange,
        historyItems = historyItemsProvider,
        terminal = terminal,
        contentWidth = contentWidthProvider,
    )

    // Keyed only on inputs that affect the registration itself. The editor reads
    // `historyItems` lazily via its provider, so a fresh list identity each frame must
    // NOT tear down and re-register the interceptor (that would churn the handler closure
    // and risk a stale handler under input bursts).
    DisposableEffect(enabled, focusRegistry, keyboardInterceptor) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val dispose =
            keyboardInterceptor.register(priority = -1) { event ->
                if (!focusRegistry.isFocused(focusToken)) {
                    return@register false
                }
                if (!focusRegistry.claimEvent(event)) {
                    return@register false
                }

                if (event.key == "Tab" && !event.shift && !event.ctrl && !event.alt) {
                    focusRegistry.focusNext()
                    return@register true
                }

                if (event.shift && (event.key == "Q" || event.key == "q")) {
                    focusRegistry.focusPrevious()
                    return@register true
                }

                editor.handleKeyEvent(event)
                true
            }

        onDispose {
            dispose()
        }
    }

    // Render the text field
    TextField(
        value = maskChar?.toString()?.repeat(latestValue.length) ?: latestValue,
        onValueChange = onValueChangeCallback,
        modifier = focusableModifier,
        icon = icon,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = false, // allow wrapping so height grows with content
        showCursor = showCursor && isFocused,
        cursorChar = cursorChar,
        cursorPosition = cursorPositionState,
        maxLines = maxLines,
        textStyle = textStyle,
        placeholderStyle = placeholderStyle,
        iconStyle = iconStyle,
    )
}

@Composable
fun InputTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
    historyItems: List<String> = emptyList(),
    historyIndexState: InputHistoryIndexState? = null,
    maskChar: Char? = null,
) {
    InputTextField(
        value = state.value,
        onValueChange = { state.value = it },
        modifier = modifier,
        icon = icon,
        placeholder = placeholder,
        enabled = enabled,
        onSubmit = onSubmit,
        showCursor = showCursor,
        cursorChar = cursorChar,
        maxLines = maxLines,
        textStyle = textStyle,
        placeholderStyle = placeholderStyle,
        iconStyle = iconStyle,
        cursorPosition = state.cursorPosition,
        onCursorPositionChange = { state.cursorPosition = it },
        historyItems = historyItems,
        historyIndexState = historyIndexState,
        maskChar = maskChar,
    )
}

private fun syncHistoryState(
    historyItems: List<String>,
    latestValue: String,
    historyIndexState: InputHistoryIndexState,
) {
    if (historyIndexState.index < 0 || historyIndexState.index > historyItems.size) {
        historyIndexState.index = historyItems.size
    }
    if (historyItems.isNotEmpty()) {
        val idx = historyIndexState.index
        if (idx in 0 until historyItems.size && latestValue != historyItems[idx]) {
            historyIndexState.index = historyItems.size
            historyIndexState.draft = latestValue
        }
    }
}

private class ExternalValueTracker<T>(
    var value: T,
) {
    private val pending = ArrayDeque<T>()

    fun expect(next: T) {
        pending.addLast(next)
    }

    fun acknowledge(observed: T): Boolean {
        val match = pending.indexOf(observed)
        if (match < 0) return false
        repeat(match + 1) { pending.removeFirst() }
        return true
    }

    fun clearPending() {
        pending.clear()
    }
}
