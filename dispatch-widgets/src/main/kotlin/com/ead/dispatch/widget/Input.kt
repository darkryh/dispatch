package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.composableWidget
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.github.ajalt.mordant.input.KeyboardEvent
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
 * @param value Current text value.
 * @param onValueChange Callback when text changes.
 * @param modifier Modifiers to apply.
 * @param icon Leading icon/prefix that is always shown (e.g. a prompt like `"> "`).
 * @param placeholder Placeholder text when empty.
 * @param enabled Whether the field is enabled.
 * @param singleLine Whether to restrict to a single line.
 * @param onSubmit Callback when Enter is pressed. Receives the current text value.
 * @param showCursor Whether to show a cursor at the end of text.
 * @param cursorChar Character to use for the cursor.
 * @param maxLines Optional maximum number of lines to render (null = no limit within constraints).
 * @param cursorPosition Cursor position (character index) within [value].
 */
@Dispatchable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
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

        val iconWidth = if (icon.isEmpty()) {
            0
        } else {
            // Measure the display width in terminal cells (handles wide/combining chars).
            com.github.ajalt.mordant.widgets.Text(
                icon,
                whitespace = Whitespace.PRE,
            ).render(terminal, width = 1_000).width
        }

        val renderWidth = if (modifiedConstraints.hasBoundedWidth) {
            modifiedConstraints.maxWidth
        } else {
            (iconWidth + textForRender.length).coerceAtLeast(10)
        }

        val contentWidth = (renderWidth - iconWidth).coerceAtLeast(1)

        val renderedContent = terminal.render(
            textForRender,
            whitespace = Whitespace.PRE_WRAP,
            overflowWrap = OverflowWrap.BREAK_WORD, // break long words to new lines
            width = contentWidth,
        )

        var contentLines = renderedContent.lines()

        val cursorVisual = if (showCursor && !isPlaceholder) {
            val clampedCursor = cursorPosition.coerceIn(0, baseText.length)
            computeCursorVisual(baseText, clampedCursor, contentWidth)
        } else null

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
            contentLines = applyCursorOverlay(
                lines = contentLines,
                cursor = cursorVisualInWindow,
                cursorChar = cursorToken,
                contentWidth = contentWidth,
            )
        } else if (showCursor && isPlaceholder) {
            contentLines = applyPlaceholderCursorPrefix(
                lines = contentLines,
                cursorChar = cursorToken,
                contentWidth = contentWidth,
            )
        }

        val contentTextStyle = if (isPlaceholder) placeholderStyle else textStyle
        val styledContentLines = if (contentTextStyle == null) {
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

        val lines = if (iconWidth == 0) {
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

    private data class CursorVisual(val line: Int, val col: Int)

    private fun computeCursorVisual(text: String, cursorPos: Int, width: Int): CursorVisual {
        val safePos = cursorPos.coerceIn(0, text.length)
        val prefixText = text.substring(0, safePos)
        val marker = CURSOR_MARKER
        val suffixSpan = spanSuffixFrom(text, safePos)
        val renderedPrefix = terminal.render(
            prefixText + marker + suffixSpan,
            whitespace = Whitespace.PRE_WRAP,
            overflowWrap = OverflowWrap.BREAK_WORD,
            width = width,
        )
        val prefixLines = renderedPrefix.lines()
        val markerLineIndex = prefixLines.indexOfFirst { it.contains(marker) }.let { index ->
            if (index == -1) prefixLines.lastIndex.coerceAtLeast(0) else index
        }
        val markerLine = prefixLines.getOrNull(markerLineIndex).orEmpty()
        val markerCol = markerLine.indexOf(marker).let { index ->
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
        mutable[0] = if (withCursor.length > contentWidth) {
            withCursor.take(contentWidth)
        } else {
            withCursor
        }
        return mutable
    }
}

private fun List<String>.padEnd(target: Int): List<String> {
    if (size >= target) return this
    return this + List(target - size) { "" }
}

/**
 * Input state for managing text field state.
 */
class TextFieldState(initialValue: String = "") {
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
    fun hasSelection(): Boolean =
        selectionStart != null && selectionEnd != null && selectionStart != selectionEnd

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
@Dispatchable
fun rememberTextFieldState(initialValue: String = ""): TextFieldState {
    return remember { TextFieldState(initialValue) }
}

class InputHistoryIndexState {
    var index: Int by mutableStateOf(-1)
    var draft: String? by mutableStateOf(null)
}

@Dispatchable
fun rememberInputHistoryIndexState(): InputHistoryIndexState {
    return remember { InputHistoryIndexState() }
}

/**
 * A text field that uses TextFieldState for full control.
 */
@Dispatchable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
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
        onSubmit = onSubmit,
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
@Dispatchable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "Password",
    enabled: Boolean = true,
    maskChar: Char = '•',
) {
    val terminal = LocalTerminal.current
    val maskedValue = maskChar.toString().repeat(value.length)
    composableWidget("PasswordField") {
        TextFieldMeasurable(
            value = maskedValue,
            icon = icon,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = true,
            modifier = modifier,
            terminal = terminal,
        )
    }
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
@Dispatchable
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
) {
    val onValueChangeCallback = com.ead.dispatch.runtime.rememberCallback(onValueChange)
    val onSubmitCallback = com.ead.dispatch.runtime.rememberCallback(onSubmit)
    val onCursorPositionChangeCallback = com.ead.dispatch.runtime.rememberCallback(onCursorPositionChange)

    val terminal = LocalTerminal.current
    val terminalWidth = LocalTerminalWidth.current
    val iconWidth = remember(icon) {
        if (icon.isEmpty()) {
            0
        } else {
            com.github.ajalt.mordant.widgets.Text(
                icon,
                whitespace = Whitespace.PRE,
            ).render(terminal, width = 1_000).width
        }
    }
    val contentWidth = (terminalWidth - iconWidth).coerceAtLeast(1)

    // Keep local value and cursor so we can handle edits even between recompositions.
    var latestValue by remember { mutableStateOf(value) }
    var cursorPositionState by remember { mutableStateOf(cursorPosition ?: value.length) }
    val pasteTracker = remember { PasteTracker() }
    val resolvedHistoryIndexState = historyIndexState ?: remember { InputHistoryIndexState() }
    if (resolvedHistoryIndexState.index < 0 || resolvedHistoryIndexState.index > historyItems.size) {
        resolvedHistoryIndexState.index = historyItems.size
    }
    if (historyItems.isNotEmpty()) {
        val idx = resolvedHistoryIndexState.index
        if (idx in 0 until historyItems.size && latestValue != historyItems[idx]) {
            resolvedHistoryIndexState.index = historyItems.size
            resolvedHistoryIndexState.draft = latestValue
        }
    }

    // Track external value changes without making them the source of truth for rendering.
    // This avoids dropping keystrokes when `value` is backed by an async flow collector.
    val externalValueTracker = remember { ExternalValueTracker(value) }
    val externalCursorTracker = remember { ExternalValueTracker(cursorPosition ?: value.length) }
    if (value != externalValueTracker.value) {
        externalValueTracker.value = value
        if (value != latestValue) {
            latestValue = value
            cursorPositionState = (cursorPosition ?: value.length).coerceIn(0, value.length)
        } else {
            cursorPositionState = cursorPositionState.coerceIn(0, value.length)
        }
    }
    if (cursorPosition != null && cursorPosition != externalCursorTracker.value) {
        externalCursorTracker.value = cursorPosition
        cursorPositionState = cursorPosition.coerceIn(0, latestValue.length)
    }

    // Set up keyboard handling via DispatchScope.
    // Always register a handler so we don't keep a stale handler when `enabled` flips to false.
    val scope = dispatchScope()
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val focusRegistry = LocalFocusRegistry.current
    val focusToken = remember { Any() }

    val isFocused = enabled && focusRegistry.isFocused(focusToken)

    fun updateCursorPosition(nextPosition: Int) {
        val bounded = nextPosition.coerceIn(0, latestValue.length)
        if (cursorPositionState != bounded) {
            cursorPositionState = bounded
            onCursorPositionChangeCallback?.invoke(bounded)
        }
    }

    fun insertText(text: String) {
        if (text.isEmpty()) return
        if (historyItems.isNotEmpty() && resolvedHistoryIndexState.index < historyItems.size) {
            resolvedHistoryIndexState.draft = latestValue
            resolvedHistoryIndexState.index = historyItems.size
        }
        val result = applyInsertion(latestValue, cursorPositionState, text)
        latestValue = result.value
        updateCursorPosition(result.cursorPosition)
        onValueChangeCallback(latestValue)
    }

    DisposableEffect(Triple(enabled, keyboardInterceptor, historyItems)) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val disposeFocus = focusRegistry.register(focusToken)
        val dispose = scope.addKeyEventHandler { event ->
            // Check if any interceptor wants to handle this event first
            // (e.g., CommandPalette intercepting Arrow keys when visible)
            if (keyboardInterceptor.tryIntercept(event)) {
                return@addKeyEventHandler  // Event was consumed by interceptor
            }

            if (!focusRegistry.isFocused(focusToken)) {
                return@addKeyEventHandler
            }
            if (!focusRegistry.claimEvent(event)) {
                return@addKeyEventHandler
            }

            if (event.key == "Tab" && !event.shift && !event.ctrl && !event.alt) {
                focusRegistry.focusNext()
                return@addKeyEventHandler
            }

            if (event.shift && (event.key == "Q" || event.key == "q")) {
                focusRegistry.focusPrevious()
                return@addKeyEventHandler
            }

            cursorPositionState = cursorPositionState.coerceIn(0, latestValue.length)
            when (event.key) {
                "PasteStart" -> {
                    pasteTracker.increment()
                    return@addKeyEventHandler
                }
                "PasteEnd" -> {
                    pasteTracker.decrement()
                    return@addKeyEventHandler
                }
                "Enter" -> {
                    // Shift+Enter inserts a newline (when supported by the terminal).
                    if (event.shift || pasteTracker.isActive) {
                        insertText("\n")
                        return@addKeyEventHandler
                    }

                    val submit = onSubmitCallback
                    if (submit != null) {
                        val text = latestValue
                        if (text.isNotBlank()) {
                            submit(text)
                            latestValue = ""
                            updateCursorPosition(0)
                            onValueChangeCallback("")
                        }
                    }
                }
                "Backspace" -> {
                    val currentValue = latestValue
                    val safeCursor = cursorPositionState.coerceIn(0, currentValue.length)
                    if (safeCursor > 0 && currentValue.isNotEmpty()) {
                        val before = currentValue.substring(0, safeCursor - 1)
                        val after = currentValue.substring(safeCursor)
                        latestValue = before + after
                        if (historyItems.isNotEmpty() && resolvedHistoryIndexState.index < historyItems.size) {
                            resolvedHistoryIndexState.draft = latestValue
                            resolvedHistoryIndexState.index = historyItems.size
                        }
                        updateCursorPosition(safeCursor - 1)
                        onValueChangeCallback(latestValue)
                    } else {
                        updateCursorPosition(safeCursor)
                    }
                }
                "Delete" -> {
                    val currentValue = latestValue
                    val safeCursor = cursorPositionState.coerceIn(0, currentValue.length)
                    if (safeCursor < currentValue.length && currentValue.isNotEmpty()) {
                        val before = currentValue.substring(0, safeCursor)
                        val after = currentValue.substring(safeCursor + 1)
                        latestValue = before + after
                        if (historyItems.isNotEmpty() && resolvedHistoryIndexState.index < historyItems.size) {
                            resolvedHistoryIndexState.draft = latestValue
                            resolvedHistoryIndexState.index = historyItems.size
                        }
                        onValueChangeCallback(latestValue)
                    } else {
                        updateCursorPosition(safeCursor)
                    }
                }
                "ArrowLeft" -> {
                    val safeCursor = cursorPositionState.coerceIn(0, latestValue.length)
                    updateCursorPosition((safeCursor - 1).coerceAtLeast(0))
                }
                "ArrowRight" -> {
                    val safeCursor = cursorPositionState.coerceIn(0, latestValue.length)
                    updateCursorPosition((safeCursor + 1).coerceAtMost(latestValue.length))
                }
                "ArrowUp" -> {
                    if (historyItems.isNotEmpty() && cursorPositionState == 0) {
                        if (resolvedHistoryIndexState.index == historyItems.size) {
                            resolvedHistoryIndexState.draft = latestValue
                        }
                        resolvedHistoryIndexState.index =
                            (resolvedHistoryIndexState.index - 1).coerceAtLeast(0)
                        val previous = historyItems[resolvedHistoryIndexState.index]
                        latestValue = previous
                        updateCursorPosition(previous.length)
                        onValueChangeCallback(latestValue)
                        return@addKeyEventHandler
                    }
                    val info = cursorLineInfo(terminal, latestValue, cursorPositionState, contentWidth)
                    if (info.line == 0 && cursorPositionState > 0) {
                        updateCursorPosition(0)
                        return@addKeyEventHandler
                    }
                    updateCursorPosition(
                        moveCursorVertical(
                        terminal = terminal,
                        text = latestValue,
                        cursorPosition = cursorPositionState,
                        direction = -1,
                        wrapWidth = contentWidth,
                        )
                    )
                }
                "ArrowDown" -> {
                    if (historyItems.isNotEmpty() && cursorPositionState == latestValue.length) {
                        if (resolvedHistoryIndexState.index < historyItems.size) {
                            resolvedHistoryIndexState.index =
                                (resolvedHistoryIndexState.index + 1).coerceAtMost(historyItems.size)
                            val next = if (resolvedHistoryIndexState.index == historyItems.size) {
                                resolvedHistoryIndexState.draft ?: ""
                            } else {
                                historyItems[resolvedHistoryIndexState.index]
                            }
                            latestValue = next
                            updateCursorPosition(next.length)
                            onValueChangeCallback(latestValue)
                            return@addKeyEventHandler
                        }
                    }
                    val info = cursorLineInfo(terminal, latestValue, cursorPositionState, contentWidth)
                    if (info.line == info.maxLine && cursorPositionState < latestValue.length) {
                        updateCursorPosition(latestValue.length)
                        return@addKeyEventHandler
                    }
                    updateCursorPosition(
                        moveCursorVertical(
                        terminal = terminal,
                        text = latestValue,
                        cursorPosition = cursorPositionState,
                        direction = 1,
                        wrapWidth = contentWidth,
                        )
                    )
                }
                "Home" -> {
                    updateCursorPosition(0)
                }
                "End" -> {
                    updateCursorPosition(latestValue.length)
                }
                else -> {
                    // Handle printable characters or multi-codepoint text
                    val text = textFromKeyEvent(event)
                    if (text != null) {
                        insertText(text)
                    }
                }
            }
        }

        onDispose {
            dispose()
            disposeFocus()
        }
    }

    // Render the text field
    TextField(
        value = latestValue,
        onValueChange = onValueChangeCallback,
        modifier = modifier,
        icon = icon,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = false, // allow wrapping so height grows with content
        onSubmit = onSubmit,
        showCursor = showCursor && isFocused,
        cursorChar = cursorChar,
        cursorPosition = cursorPositionState,
        maxLines = maxLines,
        textStyle = textStyle,
        placeholderStyle = placeholderStyle,
        iconStyle = iconStyle,
    )
}

@Dispatchable
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
    )
}

private fun moveCursorVertical(
    terminal: com.github.ajalt.mordant.terminal.Terminal,
    text: String,
    cursorPosition: Int,
    direction: Int,
    wrapWidth: Int,
): Int {
    if (direction == 0) return cursorPosition.coerceIn(0, text.length)

    val width = wrapWidth.coerceAtLeast(1)
    val clampedCursor = cursorPosition.coerceIn(0, text.length)
    val cache = HashMap<Int, CursorVisual>()
    val marker = CURSOR_MARKER

    fun visualAt(pos: Int): CursorVisual {
        val safePos = pos.coerceIn(0, text.length)
        return cache.getOrPut(safePos) {
            val prefix = text.substring(0, safePos)
            val suffixSpan = spanSuffixFrom(text, safePos)
            val rendered = terminal.render(
                prefix + marker + suffixSpan,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD,
                width = width,
            )
            val lines = rendered.lines()
            val markerLineIndex = lines.indexOfFirst { it.contains(marker) }.let { index ->
                if (index == -1) lines.lastIndex.coerceAtLeast(0) else index
            }
            val markerLine = lines.getOrNull(markerLineIndex).orEmpty()
            val markerCol = markerLine.indexOf(marker).let { index ->
                if (index == -1) markerLine.length else index
            }

            CursorVisual(line = markerLineIndex, col = markerCol)
        }
    }

    val current = visualAt(clampedCursor)
    val maxLine = visualAt(text.length).line
    val targetLine = (current.line + direction).coerceIn(0, maxLine)
    if (targetLine == current.line) return clampedCursor
    val desiredCol = current.col

    fun lowerBoundLine(target: Int): Int {
        var low = 0
        var high = text.length + 1 // exclusive
        while (low < high) {
            val mid = (low + high) / 2
            val line = visualAt(mid.coerceAtMost(text.length)).line
            if (line < target) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low.coerceIn(0, text.length)
    }

    val start = lowerBoundLine(targetLine)
    val endExclusive = lowerBoundLine(targetLine + 1).coerceAtMost(text.length)
    if (start >= endExclusive) return clampedCursor

    var low = start
    var high = endExclusive
    while (low < high) {
        val mid = (low + high) / 2
        val v = visualAt(mid)
        when {
            v.line > targetLine -> high = mid
            v.col <= desiredCol -> low = mid + 1
            else -> high = mid
        }
    }

    return (low - 1).coerceIn(start, endExclusive - 1)
}

private data class CursorVisual(val line: Int, val col: Int)

private data class CursorLineInfo(val line: Int, val maxLine: Int)

private fun cursorLineInfo(
    terminal: com.github.ajalt.mordant.terminal.Terminal,
    text: String,
    cursorPosition: Int,
    wrapWidth: Int,
): CursorLineInfo {
    val width = wrapWidth.coerceAtLeast(1)
    val clampedCursor = cursorPosition.coerceIn(0, text.length)
    val cache = HashMap<Int, CursorVisual>()
    val marker = CURSOR_MARKER

    fun visualAt(pos: Int): CursorVisual {
        val safePos = pos.coerceIn(0, text.length)
        return cache.getOrPut(safePos) {
            val prefix = text.substring(0, safePos)
            val suffixSpan = spanSuffixFrom(text, safePos)
            val rendered = terminal.render(
                prefix + marker + suffixSpan,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD,
                width = width,
            )
            val lines = rendered.lines()
            val markerLineIndex = lines.indexOfFirst { it.contains(marker) }.let { index ->
                if (index == -1) lines.lastIndex.coerceAtLeast(0) else index
            }
            val markerLine = lines.getOrNull(markerLineIndex).orEmpty()
            val markerCol = markerLine.indexOf(marker).let { index ->
                if (index == -1) markerLine.length else index
            }
            CursorVisual(line = markerLineIndex, col = markerCol)
        }
    }

    val current = visualAt(clampedCursor)
    val maxLine = visualAt(text.length).line
    return CursorLineInfo(line = current.line, maxLine = maxLine)
}

private class ExternalValueTracker<T>(var value: T)

private class PasteTracker {
    private var depth = 0
    val isActive: Boolean get() = depth > 0

    fun increment() {
        depth += 1
    }

    fun decrement() {
        depth = (depth - 1).coerceAtLeast(0)
    }
}

internal data class TextInsertResult(val value: String, val cursorPosition: Int)

internal fun applyInsertion(value: String, cursorPosition: Int, text: String): TextInsertResult {
    val safeCursor = cursorPosition.coerceIn(0, value.length)
    val before = value.substring(0, safeCursor)
    val after = value.substring(safeCursor)
    val nextValue = before + text + after
    return TextInsertResult(nextValue, safeCursor + text.length)
}

private fun textFromKeyEvent(event: KeyboardEvent): String? {
    if (event.ctrl || event.alt) return null
    val key = event.key
    if (key.isEmpty()) return null
    if (key in NON_TEXT_KEYS) return null
    if (isFunctionKey(key)) return null
    if (key.any { it.isISOControl() }) return null
    return key
}

private fun isFunctionKey(key: String): Boolean {
    if (key.length < 2 || key[0] != 'F') return false
    return key.drop(1).all { it.isDigit() }
}

private val NON_TEXT_KEYS = setOf(
    "ArrowDown",
    "ArrowLeft",
    "ArrowRight",
    "ArrowUp",
    "Alt",
    "Backspace",
    "CapsLock",
    "Clear",
    "Compose",
    "Control",
    "Dead",
    "Delete",
    "End",
    "Enter",
    "Escape",
    "Home",
    "Insert",
    "Meta",
    "NumLock",
    "PageDown",
    "PageUp",
    "PasteEnd",
    "PasteStart",
    "Pause",
    "PrintScreen",
    "Process",
    "ScrollLock",
    "Shift",
    "Tab",
    "Unidentified",
)

/**
 * Cursor marker used for measurement only.
 *
 * Use a control character (cell width = 0 in Mordant) so it doesn't affect wrapping decisions.
 */
private const val CURSOR_MARKER: String = "\u0001"

/**
 * Return the suffix of the "current span" starting at [start], using the same span boundaries as
 * Mordant's text parser (whitespace vs. non-whitespace, plus hard-break characters).
 *
 * Including this suffix when measuring cursor position prevents incorrect wrapping when the cursor
 * is inside a word that would otherwise be wrapped as a whole.
 */
private fun spanSuffixFrom(text: String, start: Int): String {
    if (start >= text.length) return ""

    val type = cursorSpanType(text[start])
    if (type == 1) return text[start].toString()

    var end = start + 1
    while (end < text.length && cursorSpanType(text[end]) == type) {
        end += 1
    }
    return text.substring(start, end)
}

private fun cursorSpanType(c: Char): Int {
    // Mirror Mordant's Parsing.splitWords types:
    // 0 = carriage return, 1 = always-break chunk, 2 = whitespace, 3 = word
    return when {
        c == '\r' -> 0
        c == '\n' || c == '\t' || c == '\u0085' || c == '\u2028' -> 1
        c.isWhitespace() -> 2
        else -> 3
    }
}
