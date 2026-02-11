package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SegmentedPlaceable
import com.ead.dispatch.render.TerminalRenderer
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.viewmodel.ViewModelStore
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import sun.misc.Signal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.set
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

/**
 * Entry point for a Dispatch CLI application.
 */
@Suppress("ktlint:standard:function-naming")
fun DispatchApplication(
    args: Array<String> = emptyArray(),
    content: DispatchScope.() -> Unit,
) {
    val builder = DispatchApplicationBuilder(args)

    runBlocking {
        val exitCode = builder.run(content)
        if (exitCode != 0) {
            exitProcess(exitCode)
        }
    }
}

/**
 * Builder for DispatchApplication.
 */
internal class DispatchApplicationBuilder(
    private val args: Array<String>,
) {
    private val config = DispatchConfig()
    private var terminal: Terminal? = null

    @Volatile
    private var exitRequested = false

    @Volatile
    private var exitCode = 0

    private lateinit var uiScope: CoroutineScope
    private lateinit var backgroundScope: CoroutineScope
    private lateinit var appJob: Job
    private lateinit var uiDispatcher: CoroutineDispatcher
    private lateinit var composer: Composer
    private lateinit var recomposer: Recomposer
    private lateinit var renderer: TerminalRenderer
    private lateinit var frameScheduler: FrameScheduler

    private val rootMeasurable = AtomicReference<Measurable?>(null)
    private val parsedFlags = mutableSetOf<String>()
    private val parsedArguments = mutableMapOf<String, String>()
    private var dispatchArgs = DispatchArgs(emptyList(), emptySet(), emptyMap())

    private val keyEventHandlers = CopyOnWriteArrayList<(KeyboardEvent) -> Unit>()

    @Volatile
    private var mouseEventHandler: ((MouseEvent) -> Unit)? = null

    private val scrollingContentTracker = ScrollingContentTracker()
    private val keyboardInterceptor = KeyboardInterceptor()
    private val focusRegistry = FocusRegistry()
    private val exitPromptState = ExitPromptState()
    private var exitResetJob: Job? = null
    private var lastTerminalWidth: Int = 0
    private var lastTerminalHeight: Int = 0
    private var pendingResizeReset: Boolean = false
    private var resizeHandlerRegistered: Boolean = false

    /**
     * Active area height hint (from config).
     */
    private var activeAreaHeight: Int = 4

    private val renderLock = ReentrantLock()

    private var activeUIBlock: (@Dispatchable () -> Unit)? = null

    @Volatile
    private var sizeDirty: Boolean = true

    private lateinit var dispatchScopeInstance: DispatchScope
    private val compositionScopeToken = Any()
    private var lastWindowTitleApplied: String? = null

    suspend fun run(content: DispatchScope.() -> Unit): Int {
        initializeScopes()
        try {
            executeApplication(content)
        } finally {
            ViewModelStore.clear()
            appJob.cancel()
        }
        return exitCode
    }

    private fun initializeScopes() {
        uiDispatcher = Dispatchers.Default.limitedParallelism(1)
        appJob = SupervisorJob()
        uiScope = CoroutineScope(uiDispatcher + appJob)
        backgroundScope = CoroutineScope(Dispatchers.Default + appJob)
    }

    private suspend fun executeApplication(content: DispatchScope.() -> Unit) {
        val failure = runCatching { startApplication(content) }.exceptionOrNull() ?: return
        if (failure is CancellationException) return
        reportError(failure)
        exitCode = 1
    }

    private suspend fun startApplication(content: DispatchScope.() -> Unit) {
        initializeTerminal()
        if (!configureApplication(content)) return
        runTerminalSession()
    }

    private fun initializeTerminal() {
        val shadowColor = rgb("#24218c")
        terminal =
            Terminal(
                theme =
                    Theme {
                        styles["hr.rule"] = shadowColor
                        styles["panel.border"] = shadowColor
                    },
                ansiLevel = AnsiLevel.TRUECOLOR,
            )
        renderer = TerminalRenderer(terminal!!)
        renderer.hideCursor()
    }

    private fun configureApplication(content: DispatchScope.() -> Unit): Boolean {
        val scope = DispatchScopeImpl()
        dispatchScopeInstance = scope
        composer = Composer()
        recomposer = Recomposer(uiScope)
        registerResizeHandler()

        scope.content()
        parseArguments()

        if ("version" in parsedFlags) {
            terminal?.println("${config.name} ${config.version}")
            return false
        }

        activeAreaHeight = config.activeAreaHeight
        frameScheduler =
            FrameScheduler(
                scope = uiScope,
                targetFps = config.targetFps,
                onFrame = { composeAndRender() },
            )
        return true
    }

    private suspend fun runTerminalSession() {
        terminal!!.enterRawMode(config.mouseTracking).use { rawMode ->
            val initialSize = terminal!!.updateSize()
            lastTerminalWidth = initialSize.width
            lastTerminalHeight = initialSize.height
            sizeDirty = false

            val inputJob =
                backgroundScope.launch(Dispatchers.IO) {
                    runInputLoop {
                        rawMode.readEventOrNull(50.milliseconds)
                    }
                }

            frameScheduler.start()
            recomposer.registerComposition(compositionScopeToken) { frameScheduler.requestFrame() }
            composeAndRender()
            frameScheduler.markFrame()
            val recomposerJob = recomposer.start()
            awaitExitRequest()

            renderer.clearActiveArea()
            inputJob.cancelAndJoin()
            frameScheduler.stop()
            recomposer.stop()
            recomposerJob.cancelAndJoin()
            renderer.showCursor()
        }
    }

    private suspend fun awaitExitRequest() {
        while (!exitRequested && uiScope.isActive) {
            delay(50)
        }
    }

    private suspend fun runInputLoop(readEvent: suspend () -> Any?) {
        var consecutiveErrors = 0
        while (coroutineContext.isActive && !exitRequested) {
            val readResult = readInputEvent(readEvent, consecutiveErrors)
            consecutiveErrors = readResult.consecutiveErrors
            val event = readResult.event ?: continue

            val shouldExitInputLoop =
                withContext(uiDispatcher) {
                    when (event) {
                        is KeyboardEvent -> {
                            handleKeyboardEvent(event)
                        }
                        is MouseEvent -> {
                            handleMouseEvent(event)
                            false
                        }
                        else -> {
                            false
                        }
                    }
                }

            if (shouldExitInputLoop) {
                return
            }
        }
    }

    private suspend fun readInputEvent(
        readEvent: suspend () -> Any?,
        consecutiveErrors: Int,
    ): InputReadResult =
        try {
            InputReadResult(
                event = readEvent(),
                consecutiveErrors = 0,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val nextErrors = consecutiveErrors + 1
            val backoff = (10L * nextErrors).coerceAtMost(200L)
            delay(backoff)
            InputReadResult(event = null, consecutiveErrors = nextErrors)
        }

    private fun reportError(error: Throwable) {
        terminal?.println("Error: ${error.message}")
        terminal?.println(error.stackTraceToString())
    }

    private fun handleKeyboardEvent(event: KeyboardEvent): Boolean {
        when (resolveExitAction(event)) {
            ExitAction.Exit -> {
                exitRequested = true
                return true
            }
            ExitAction.Arm -> {
                armExitPrompt()
                return false
            }
            ExitAction.None -> {
                Unit
            }
        }

        val consumed = keyboardInterceptor.tryIntercept(event)
        if (!consumed) {
            keyEventHandlers.forEach { handler -> handler(event) }
        }
        recomposer.requestRecomposition()
        return false
    }

    private fun resolveExitAction(event: KeyboardEvent): ExitAction {
        if (!shouldHandleExit(event)) return ExitAction.None
        if (!config.requireExitDoublePress || exitPromptState.isArmed) return ExitAction.Exit
        return ExitAction.Arm
    }

    private fun armExitPrompt() {
        exitPromptState.isArmed = true
        exitResetJob?.cancel()
        exitResetJob =
            uiScope.launch {
                delay(config.exitTimeoutOnDoublePress)
                exitPromptState.isArmed = false
                recomposer.requestRecomposition()
            }
        recomposer.requestRecomposition()
    }

    private fun handleMouseEvent(event: MouseEvent) {
        mouseEventHandler?.invoke(event)
        recomposer.requestRecomposition()
    }

    private fun composeAndRender() {
        applyWindowTitleIfNeeded()
        updateTerminalSizeIfNeeded()
        composeActiveUI()
        renderActiveArea()
    }

    private fun composeActiveUI() {
        val block = activeUIBlock ?: return
        val t = terminal ?: return

        withComposer(composer) {
            Recomposer.withRecomposer(recomposer) {
                Recomposer.withScope(compositionScopeToken) {
                    composer.startComposition()

                    try {
                        CompositionLocalProvider(
                            LocalDispatchScope provides dispatchScopeInstance,
                            LocalDispatchArgs provides dispatchArgs,
                            LocalDispatchConfig provides config,
                            LocalDispatchContext provides DispatchContext(dispatchScopeInstance, dispatchArgs, config),
                            LocalTerminal provides t,
                            LocalTerminalWidth provides lastTerminalWidth.coerceAtLeast(40),
                            LocalTerminalHeight provides lastTerminalHeight.coerceAtLeast(10),
                            LocalTheme provides config.theme,
                            LocalKeyboardInterceptor provides keyboardInterceptor,
                            LocalFocusRegistry provides focusRegistry,
                            LocalExitPromptState provides exitPromptState,
                        ) {
                            block()
                        }
                    } finally {
                        composer.endComposition()
                        val rootNode = composer.getRootNode()
                        rootMeasurable.set(rootNode)
                        // Ensure focus order reflects the latest composed layout tree.
                        focusRegistry.sync(rootNode)
                        EffectRunner.runPendingEffects()
                    }
                }
            }
        }
    }

    private fun renderActiveArea() {
        renderLock.lock()
        try {
            val measurable = rootMeasurable.get() ?: return
            val width = lastTerminalWidth.coerceAtLeast(40)

            // UNCONSTRAINED height - let content be as tall as needed
            val constraints =
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = Int.MAX_VALUE, // Unconstrained!
                )

            val placeable = measurable.measure(constraints)

            // Split content into scrolling (history) and active (input) portions
            val (scrollingLines, activeLines) =
                splitContentForRendering(
                    placeable,
                    activeAreaHeight,
                    scrollingContentTracker.committedLineCount,
                )

            if (pendingResizeReset) {
                renderer.clearScreen(clearScrollback = true)
                scrollingContentTracker.reset()
                pendingResizeReset = false
            }

            val update = scrollingContentTracker.consume(scrollingLines)
            if (update.reset) {
                renderer.clearScreen(clearScrollback = true)
            }
            if (update.lines.isNotEmpty()) {
                renderer.appendScrollingContent(update.lines)
            }

            // Update the active area (input/status at bottom)
            renderer.updateActiveArea(activeLines)
        } finally {
            renderLock.unlock()
        }
    }

    private fun parseArguments() {
        val parsed = parseDispatchArguments(args, config)
        parsedFlags.clear()
        parsedFlags.addAll(parsed.flags)
        parsedArguments.clear()
        parsedArguments.putAll(parsed.arguments)
        dispatchArgs =
            DispatchArgs(
                rawArgs = args.toList(),
                flags = parsed.flags.toSet(),
                arguments = parsed.arguments.toMap(),
            )
    }

    private fun shouldHandleExit(event: KeyboardEvent): Boolean {
        val predicate = config.exitKeyPredicate
        if (predicate != null) {
            return predicate(event)
        }
        val bindings = config.exitKeyBindings
        if (bindings.isEmpty()) return false
        return bindings.any { it.matches(event) }
    }

    private fun updateTerminalSizeIfNeeded() {
        val t = terminal ?: return
        if (!sizeDirty) return
        val size = t.updateSize()
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = sizeDirty,
                previousWidth = lastTerminalWidth,
                previousHeight = lastTerminalHeight,
                currentWidth = size.width,
                currentHeight = size.height,
            )
        lastTerminalWidth = update.width
        lastTerminalHeight = update.height
        if (update.reset) {
            pendingResizeReset = true
        }
        sizeDirty = update.dirty
    }

    private fun registerResizeHandler() {
        if (resizeHandlerRegistered) return
        try {
            Signal.handle(Signal("WINCH")) {
                sizeDirty = true
                recomposer.requestRecomposition()
            }
            resizeHandlerRegistered = true
        } catch (_: Throwable) {
            // No-op when signals aren't supported.
        }
    }

    private fun applyWindowTitleIfNeeded() {
        val t = terminal ?: return
        val title = config.windowTitle ?: config.name
        if (title.isNullOrBlank()) return
        if (!config.enforceWindowTitle && lastWindowTitleApplied == title) return

        val safeTitle = title.replace("\u001b", "").replace("\u0007", "")
        t.rawPrint("\u001b]0;$safeTitle\u0007")
        t.rawPrint("\u001b]2;$safeTitle\u0007")
        lastWindowTitleApplied = title
    }

    private inner class DispatchScopeImpl : DispatchScope {
        override val terminal: Terminal get() = this@DispatchApplicationBuilder.terminal!!
        override val theme: DispatchTheme get() = config.theme
        override val args: Array<String> get() = this@DispatchApplicationBuilder.args
        override val terminalWidth: Int get() = lastTerminalWidth.coerceAtLeast(40)
        override val terminalHeight: Int get() = lastTerminalHeight.coerceAtLeast(10)

        override fun config(block: DispatchConfig.() -> Unit) = config.block()

        override fun exit(code: Int) {
            exitCode = code
            exitRequested = true
        }

        override fun hasFlag(name: String) = name in parsedFlags

        override fun getArgument(name: String) = parsedArguments[name]

        override fun launch(block: suspend CoroutineScope.() -> Unit) = backgroundScope.launch(block = block)

        override fun clearScreen(clearScrollback: Boolean) {
            renderer.clearScreen(clearScrollback)
            scrollingContentTracker.reset()
            recomposer.requestRecomposition()
        }

        override fun onKeyEvent(handler: (KeyboardEvent) -> Unit) {
            keyEventHandlers.clear()
            keyEventHandlers.add(handler)
        }

        override fun onMouseEvent(handler: (MouseEvent) -> Unit) {
            mouseEventHandler = handler
        }

        override fun renderer(block: @Dispatchable () -> Unit) {
            activeUIBlock = block
        }
    }
}

/**
 * Split content into scrolling portion (messages/history) and active area (input/status).
 *
 * The active area is at most [activeAreaHeight] lines.
 *
 * If the measured content provides segment metadata, split on segment boundaries so multi-line
 * widgets don't get split across scrollback vs. the active area. When the active area is too small
 * to fit full segments, clip from the top of the first active segment instead of pushing those
 * lines into scrollback.
 */
internal fun splitContentForRendering(
    placeable: Placeable,
    activeAreaHeight: Int,
    committedLineCount: Int = 0,
): Pair<List<String>, List<String>> {
    val allLines = placeable.lines
    if (allLines.isEmpty() || activeAreaHeight <= 0) return allLines to emptyList()

    if (placeable is SegmentedPlaceable && isValidSegmentHeights(placeable.segmentHeights, allLines.size)) {
        return splitSegmentedContent(
            allLines = allLines,
            segmentHeights = placeable.segmentHeights,
            activeAreaHeight = activeAreaHeight,
            committedLineCount = committedLineCount,
        )
    }

    return splitUnsegmentedContent(
        allLines = allLines,
        activeAreaHeight = activeAreaHeight,
        committedLineCount = committedLineCount,
    )
}

internal fun shouldResetForResize(
    previousWidth: Int,
    previousHeight: Int,
    currentWidth: Int,
    currentHeight: Int,
): Boolean = previousWidth != currentWidth || previousHeight != currentHeight

internal data class TerminalSizeUpdate(
    val width: Int,
    val height: Int,
    val reset: Boolean,
    val dirty: Boolean,
)

internal fun computeTerminalSizeUpdate(
    sizeDirty: Boolean,
    previousWidth: Int,
    previousHeight: Int,
    currentWidth: Int,
    currentHeight: Int,
): TerminalSizeUpdate {
    if (!sizeDirty) {
        return TerminalSizeUpdate(
            width = previousWidth,
            height = previousHeight,
            reset = false,
            dirty = false,
        )
    }

    val reset = shouldResetForResize(previousWidth, previousHeight, currentWidth, currentHeight)
    return TerminalSizeUpdate(
        width = currentWidth,
        height = currentHeight,
        reset = reset,
        dirty = false,
    )
}

internal fun applyResizeSyncIfNeeded(
    pendingResizeReset: Boolean,
    scrollingLines: List<String>,
    activeLines: List<String>,
    tracker: ScrollingContentTracker,
    updateActiveArea: (List<String>) -> Unit,
): Boolean {
    if (!pendingResizeReset) return false
    tracker.sync(scrollingLines)
    updateActiveArea(activeLines)
    return true
}

private fun isValidSegmentHeights(
    segmentHeights: List<Int>,
    totalLines: Int,
): Boolean = segmentHeights.isNotEmpty() && segmentHeights.sum() == totalLines

private fun splitSegmentedContent(
    allLines: List<String>,
    segmentHeights: List<Int>,
    activeAreaHeight: Int,
    committedLineCount: Int,
): Pair<List<String>, List<String>> {
    val selection = selectActiveSegmentWindow(segmentHeights, activeAreaHeight)
    val segmentStartLine = segmentHeights.take(selection.activeStartSegmentIndex).sum()
    val activeStartLine = (segmentStartLine + selection.clipFromStartSegment).coerceIn(0, allLines.size)

    var scrollingLineCount = segmentStartLine
    if (selection.clipFromStartSegment > 0 && committedLineCount == 0) {
        scrollingLineCount = activeStartLine
    }
    scrollingLineCount =
        scrollingLineCount
            .coerceAtLeast(committedLineCount)
            .coerceAtMost(allLines.size)

    val activeStartForRender = maxOf(activeStartLine, scrollingLineCount)
    return allLines.take(scrollingLineCount) to allLines.drop(activeStartForRender)
}

private fun splitUnsegmentedContent(
    allLines: List<String>,
    activeAreaHeight: Int,
    committedLineCount: Int,
): Pair<List<String>, List<String>> {
    val activeLineCount = minOf(activeAreaHeight, allLines.size)
    val activeStartLine = allLines.size - activeLineCount
    val scrollingLineCount =
        activeStartLine
            .coerceAtLeast(committedLineCount)
            .coerceAtMost(allLines.size)
    val activeStartForRender = maxOf(activeStartLine, scrollingLineCount)
    return allLines.take(scrollingLineCount) to allLines.drop(activeStartForRender)
}

private fun selectActiveSegmentWindow(
    segmentHeights: List<Int>,
    activeAreaHeight: Int,
): ActiveSegmentSelection {
    var remaining = activeAreaHeight
    var index = segmentHeights.lastIndex
    var activeStartSegmentIndex = segmentHeights.lastIndex
    var clipFromStartSegment = 0

    while (index >= 0 && remaining > 0) {
        val height = segmentHeights[index]
        activeStartSegmentIndex = index
        if (height <= remaining) {
            remaining -= height
        } else {
            clipFromStartSegment = height - remaining
            remaining = 0
        }
        index--
    }

    return ActiveSegmentSelection(
        activeStartSegmentIndex = activeStartSegmentIndex,
        clipFromStartSegment = clipFromStartSegment,
    )
}

private data class InputReadResult(
    val event: Any?,
    val consecutiveErrors: Int,
)

private data class ActiveSegmentSelection(
    val activeStartSegmentIndex: Int,
    val clipFromStartSegment: Int,
)

private enum class ExitAction {
    None,
    Arm,
    Exit,
}
