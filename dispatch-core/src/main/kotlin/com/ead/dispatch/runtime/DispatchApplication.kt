package com.ead.dispatch.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SegmentedPlaceable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
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
    val runtimeEngine = DispatchRuntimeEngine(args)

    runBlocking {
        val exitCode = runtimeEngine.run(content)
        if (exitCode != 0) {
            exitProcess(exitCode)
        }
    }
}

/**
 * Builder for DispatchApplication.
 */
internal class DispatchRuntimeEngine(
    private val args: Array<String>,
) {
    private val config = DispatchConfig()
    private lateinit var terminal: Terminal

    @Volatile
    private var initialized = false

    @Volatile
    private var exitRequested = false

    @Volatile
    private var exitCode = 0

    private lateinit var uiScope: CoroutineScope
    private lateinit var backgroundScope: CoroutineScope
    private lateinit var appJob: Job
    private lateinit var uiDispatcher: CoroutineDispatcher
    private lateinit var recomposer: Recomposer
    private lateinit var snapshotManager: DispatchSnapshotManager
    private lateinit var composition: Composition
    private lateinit var recomposerJob: Job
    private lateinit var compositionRoot: LayoutNode
    private lateinit var renderer: TerminalRenderer
    private lateinit var resizeCoordinator: ResizeCoordinator
    private lateinit var renderPipeline: RenderPipeline
    private lateinit var frameScheduler: FrameScheduler

    private val rootMeasurable = AtomicReference<Measurable?>(null)
    private val parsedFlags = mutableSetOf<String>()
    private val parsedArguments = mutableMapOf<String, String>()
    private var dispatchArgs = DispatchArgs(emptyList(), emptySet(), emptyMap())

    @Volatile
    private var keyEventHandler: ((KeyboardEvent) -> Unit)? = null

    @Volatile
    private var mouseEventHandler: ((MouseEvent) -> Unit)? = null

    private val keyboardInterceptor = KeyboardInterceptor()
    private val focusRegistry = FocusRegistry()
    private val exitPromptState = ExitPromptState()
    private var exitResetJob: Job? = null

    /**
     * Active area height hint (from config).
     */
    private var activeAreaHeight: Int = 4

    private var activeUIBlock: (@Composable () -> Unit)? = null
    private val terminalWidthState = mutableIntStateOf(40)
    private val terminalHeightState = mutableIntStateOf(10)

    private lateinit var dispatchScopeInstance: DispatchScope
    private var lastWindowTitleApplied: String? = null
    private var lastWindowTitleAppliedAtNanos: Long = 0L
    private var exitArmDeadlineNanos: Long = 0L

    suspend fun run(content: DispatchScope.() -> Unit): Int {
        initializeScopes()
        try {
            executeApplication(content)
        } finally {
            teardown()
            ViewModelStore.clear()
            appJob.cancelAndJoin()
            config.runExitActions()
        }
        return exitCode
    }

    /**
     * Release engine resources on every exit path (including early returns and throws before the
     * terminal session starts). Idempotent: [onBeforeShutdown] may have already torn parts down.
     */
    private suspend fun teardown() {
        withContext(NonCancellable) {
            cancelExitPromptReset()
            keyEventHandler = null
            mouseEventHandler = null
            if (!initialized) return@withContext
            if (::composition.isInitialized) {
                runCatching { composition.dispose() }
            }
            if (::recomposer.isInitialized) {
                runCatching { recomposer.close() }
            }
            if (::recomposerJob.isInitialized) {
                runCatching { recomposerJob.cancelAndJoin() }
            }
            if (::snapshotManager.isInitialized) {
                runCatching { snapshotManager.close() }
            }
        }
    }

    private fun initializeScopes() {
        uiDispatcher = Dispatchers.Default.limitedParallelism(1)
        appJob = SupervisorJob()
        uiScope = CoroutineScope(uiDispatcher + appJob + DispatchFrameClock)
        snapshotManager = DispatchSnapshotManager(uiScope)
        backgroundScope = CoroutineScope(Dispatchers.Default + appJob)
        initialized = true
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
        renderer = TerminalRenderer(terminal)
        renderer.hideCursor()
    }

    private fun configureApplication(content: DispatchScope.() -> Unit): Boolean {
        val scope = DispatchScopeImpl()
        dispatchScopeInstance = scope
        recomposer = Recomposer(uiScope.coroutineContext)
        resizeCoordinator = ResizeCoordinator(
            requestRecomposition = {
                if (::frameScheduler.isInitialized) frameScheduler.requestFrame()
            }
        )
        resizeCoordinator.registerSignalHandler()
        renderPipeline = RenderPipeline(renderer)

        scope.content()
        parseArguments()

        if ("version" in parsedFlags) {
            terminal.println("${config.name} ${config.version}")
            return false
        }

        activeAreaHeight = config.activeAreaHeight
        frameScheduler =
            FrameScheduler(
                scope = uiScope,
                targetFps = config.targetFps,
                onFrame = { composeAndRender() },
            )
        initializeComposition()
        return true
    }

    private fun initializeComposition() {
        val block = activeUIBlock ?: return
        val t = terminal
        compositionRoot = LayoutNode("CompositionRoot")
        compositionRoot.setDelegate(CompositionRootMeasurable(compositionRoot))
        rootMeasurable.set(compositionRoot)
        composition = Composition(
            DispatchNodeApplier(compositionRoot) { frameScheduler.requestFrame() },
            recomposer,
        )
        composition.setContent {
            CompositionLocalProvider(
                LocalDispatchScope provides dispatchScopeInstance,
                LocalDispatchArgs provides dispatchArgs,
                LocalDispatchConfig provides config,
                LocalDispatchContext provides DispatchContext(dispatchScopeInstance, dispatchArgs, config),
                LocalTerminal provides t,
                LocalTerminalWidth provides terminalWidthState.intValue,
                LocalTerminalHeight provides terminalHeightState.intValue,
                LocalTheme provides config.theme,
                LocalKeyboardInterceptor provides keyboardInterceptor,
                LocalFocusRegistry provides focusRegistry,
                LocalExitPromptState provides exitPromptState,
            ) {
                block()
            }
        }
        recomposerJob = uiScope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    private suspend fun runTerminalSession() {
        if (activeUIBlock == null) return
        TerminalSessionCoordinator(
            terminal = terminal,
            config = config,
            uiScope = uiScope,
            backgroundScope = backgroundScope,
            uiDispatcher = uiDispatcher,
            frameScheduler = frameScheduler,
            renderer = renderer,
            resizeCoordinator = resizeCoordinator,
        ).run(
            onInputLoop = { readEvent -> runInputLoop(readEvent) },
            onFrameComposeAndRender = { composeAndRender() },
            shouldExit = { exitRequested },
            onBeforeShutdown = {
                cancelExitPromptReset()
                if (::composition.isInitialized) composition.dispose()
                recomposer.close()
                if (::recomposerJob.isInitialized) recomposerJob.cancelAndJoin()
                if (::snapshotManager.isInitialized) snapshotManager.close()
            },
        )
    }

    private suspend fun cancelExitPromptReset() {
        val pendingJob = exitResetJob
        exitResetJob = null
        pendingJob?.cancelAndJoin()
        exitPromptState.isArmed = false
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
                            handleKeyboardEvent(event, readResult.eventTimestampNanos)
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
            val event = readEvent()
            InputReadResult(
                event = event,
                eventTimestampNanos = if (event != null) System.nanoTime() else 0L,
                consecutiveErrors = 0,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val nextErrors = consecutiveErrors + 1
            val backoff = (10L * nextErrors).coerceAtMost(200L)
            delay(backoff)
            InputReadResult(
                event = null,
                eventTimestampNanos = 0L,
                consecutiveErrors = nextErrors,
            )
        }

    private fun reportError(error: Throwable) {
        if (!::terminal.isInitialized) return
        terminal.println("Error: ${error.message}")
        terminal.println(error.stackTraceToString())
    }

    private fun handleKeyboardEvent(
        event: KeyboardEvent,
        eventTimestampNanos: Long,
    ): Boolean {
        when (resolveExitAction(event, eventTimestampNanos)) {
            ExitAction.Exit -> {
                disarmExitPrompt()
                exitRequested = true
                return true
            }
            ExitAction.Arm -> {
                armExitPrompt(eventTimestampNanos)
                return false
            }
            ExitAction.None -> {
                Unit
            }
        }

        val consumed = keyboardInterceptor.tryIntercept(event)
        if (!consumed) {
            keyEventHandler?.invoke(event)
        }
        return false
    }

    private fun resolveExitAction(
        event: KeyboardEvent,
        eventTimestampNanos: Long,
    ): ExitAction {
        if (!shouldHandleExit(event)) return ExitAction.None
        if (shouldExitOnExitKey(
                requireExitDoublePress = config.requireExitDoublePress,
                exitPromptArmed = exitPromptState.isArmed,
                armDeadlineNanos = exitArmDeadlineNanos,
                eventTimestampNanos = eventTimestampNanos,
            )
        ) {
            return ExitAction.Exit
        }
        return ExitAction.Arm
    }

    private fun armExitPrompt(eventTimestampNanos: Long) {
        exitPromptState.isArmed = true
        exitArmDeadlineNanos = computeExitArmDeadlineNanos(eventTimestampNanos, config.exitTimeoutOnDoublePress)
        exitResetJob?.cancel()
        exitResetJob =
            if (config.exitTimeoutOnDoublePress.isPositive() && !config.exitTimeoutOnDoublePress.isInfinite()) {
                uiScope.launch {
                    delay(config.exitTimeoutOnDoublePress)
                    exitPromptState.isArmed = false
                    exitArmDeadlineNanos = 0L
                }
            } else {
                null
            }
    }

    private fun disarmExitPrompt() {
        exitPromptState.isArmed = false
        exitArmDeadlineNanos = 0L
        exitResetJob?.cancel()
        exitResetJob = null
    }

    private fun handleMouseEvent(event: MouseEvent) {
        mouseEventHandler?.invoke(event)
    }

    private fun composeAndRender() {
        applyWindowTitleIfNeeded()
        resizeCoordinator.updateIfNeeded(terminal)
        terminalWidthState.intValue = resizeCoordinator.width.coerceAtLeast(40)
        terminalHeightState.intValue = resizeCoordinator.height.coerceAtLeast(10)
        focusRegistry.sync(compositionRoot)
        renderPipeline.render(
            measurable = rootMeasurable.get(),
            terminalWidth = resizeCoordinator.width,
            terminalHeight = resizeCoordinator.height,
            activeAreaHeight = activeAreaHeight,
            forceRewrite = resizeCoordinator.consumePendingReset(),
        )
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

    private fun applyWindowTitleIfNeeded() {
        val t = terminal
        val title = config.windowTitle ?: config.name
        if (title.isNullOrBlank()) return
        val now = System.nanoTime()
        if (!shouldApplyWindowTitle(
                enforceWindowTitle = config.enforceWindowTitle,
                lastWindowTitleApplied = lastWindowTitleApplied,
                requestedWindowTitle = title,
                nowNanos = now,
                lastAppliedAtNanos = lastWindowTitleAppliedAtNanos,
            )
        ) {
            return
        }

        val safeTitle = title.replace("\u001b", "").replace("\u0007", "")
        t.rawPrint("\u001b]0;$safeTitle\u0007")
        t.rawPrint("\u001b]2;$safeTitle\u0007")
        lastWindowTitleApplied = title
        lastWindowTitleAppliedAtNanos = now
    }

    private inner class DispatchScopeImpl : DispatchScope {
        override val terminal: Terminal get() = this@DispatchRuntimeEngine.terminal
        override val theme: DispatchTheme get() = config.theme
        override val args: Array<String> get() = this@DispatchRuntimeEngine.args
        override val terminalWidth: Int get() = resizeCoordinator.width.coerceAtLeast(40)
        override val terminalHeight: Int get() = resizeCoordinator.height.coerceAtLeast(10)

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
            renderPipeline.reset()
            frameScheduler.requestFrame()
        }

        override fun onKeyEvent(handler: (KeyboardEvent) -> Unit) {
            keyEventHandler = handler
        }

        override fun onMouseEvent(handler: (MouseEvent) -> Unit) {
            mouseEventHandler = handler
        }

        override fun content(block: @Composable () -> Unit) {
            activeUIBlock = block
        }
    }
}

private class CompositionRootMeasurable(
    private val root: LayoutNode,
) : Measurable {
    override val modifier: Modifier = Modifier

    override fun measure(constraints: Constraints): Placeable {
        val lines = mutableListOf<String>()
        var width = constraints.minWidth
        var remainingHeight = constraints.maxHeight
        for (child in root.children) {
            if (remainingHeight == 0) break
            val childConstraints = Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = remainingHeight,
            )
            val placeable = child.measure(childConstraints)
            lines += placeable.lines
            width = maxOf(width, placeable.width)
            if (remainingHeight != Int.MAX_VALUE) {
                remainingHeight = (remainingHeight - placeable.height).coerceAtLeast(0)
            }
        }
        val height = constraints.constrainHeight(lines.size)
        return SimplePlaceable(
            width = constraints.constrainWidth(width),
            height = height,
            lines = lines.take(height),
        )
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

internal fun viewportScrollingLines(
    scrollingLines: List<String>,
    activeLines: List<String>,
    terminalHeight: Int,
): List<String> {
    val availableRows = (terminalHeight - activeLines.size).coerceAtLeast(0)
    if (availableRows == 0) return emptyList()
    return scrollingLines.takeLast(availableRows)
}

internal fun viewportLineCount(
    scrollingLines: List<String>,
    activeLines: List<String>,
    terminalHeight: Int,
): Int = viewportScrollingLines(scrollingLines, activeLines, terminalHeight).size + activeLines.size

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

private fun isValidSegmentHeights(
    segmentHeights: List<Int>,
    totalLines: Int,
): Boolean = segmentHeights.isNotEmpty() && segmentHeights.sum() == totalLines

private fun splitSegmentedContent(
    allLines: List<String>,
    segmentHeights: List<Int>,
    activeAreaHeight: Int,
    @Suppress("UNUSED_PARAMETER") committedLineCount: Int,
): Pair<List<String>, List<String>> {
    val selection = selectActiveSegmentWindow(segmentHeights, activeAreaHeight)
    val segmentStartLine = segmentHeights.take(selection.activeStartSegmentIndex).sum()
    val preferredActiveStartLine = (segmentStartLine + selection.clipFromStartSegment).coerceIn(0, allLines.size)
    val activeStartLine = preferredActiveStartLine

    var scrollingLineCount = segmentStartLine
    if (selection.clipFromStartSegment > 0) {
        scrollingLineCount = activeStartLine
    }

    return allLines.take(scrollingLineCount.coerceIn(0, activeStartLine)) to allLines.drop(activeStartLine)
}

private fun splitUnsegmentedContent(
    allLines: List<String>,
    activeAreaHeight: Int,
    @Suppress("UNUSED_PARAMETER") committedLineCount: Int,
): Pair<List<String>, List<String>> {
    val activeLineCount = minOf(activeAreaHeight, allLines.size)
    val activeStartLine = allLines.size - activeLineCount
    return allLines.take(activeStartLine) to allLines.drop(activeStartLine)
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
    val eventTimestampNanos: Long,
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

private const val WINDOW_TITLE_REAPPLY_INTERVAL_NANOS: Long = 1_000_000_000L

internal fun shouldApplyWindowTitle(
    enforceWindowTitle: Boolean,
    lastWindowTitleApplied: String?,
    requestedWindowTitle: String,
    nowNanos: Long,
    lastAppliedAtNanos: Long,
): Boolean {
    if (lastWindowTitleApplied != requestedWindowTitle) return true
    if (!enforceWindowTitle) return false
    if (lastAppliedAtNanos <= 0L) return true
    return nowNanos - lastAppliedAtNanos >= WINDOW_TITLE_REAPPLY_INTERVAL_NANOS
}

internal fun shouldExitOnExitKey(
    requireExitDoublePress: Boolean,
    exitPromptArmed: Boolean,
    armDeadlineNanos: Long,
    eventTimestampNanos: Long,
): Boolean {
    if (!requireExitDoublePress) return true
    if (!exitPromptArmed) return false
    if (armDeadlineNanos <= 0L || eventTimestampNanos <= 0L) return true
    return eventTimestampNanos <= armDeadlineNanos
}

internal fun computeExitArmDeadlineNanos(
    eventTimestampNanos: Long,
    timeout: kotlin.time.Duration,
): Long {
    if (eventTimestampNanos <= 0L) return 0L
    if (!timeout.isPositive()) return Long.MAX_VALUE
    if (timeout.isInfinite()) return Long.MAX_VALUE
    val timeoutNanos = timeout.inWholeNanoseconds
    val maxBase = Long.MAX_VALUE - timeoutNanos
    if (eventTimestampNanos >= maxBase) return Long.MAX_VALUE
    return eventTimestampNanos + timeoutNanos
}
