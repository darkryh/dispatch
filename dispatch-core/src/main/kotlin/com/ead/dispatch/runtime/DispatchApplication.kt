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
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import sun.misc.Signal
import kotlin.collections.set
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

/**
 * Entry point for a Dispatch CLI application.
 */
fun DispatchApplication(
    args: Array<String> = emptyArray(),
    content: DispatchScope.() -> Unit
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
    private val args: Array<String>
) {
    private val config = DispatchConfig()
    private var terminal: Terminal? = null

    @Volatile
    private var exitRequested = false

    @Volatile
    private var exitCode = 0

    private lateinit var appScope: CoroutineScope
    private lateinit var composer: Composer
    private lateinit var recomposer: Recomposer
    private lateinit var renderer: TerminalRenderer

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
        appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val shadowColor = rgb("#24218c")

        try {
            terminal = Terminal(
                theme = Theme {
                    styles["hr.rule"] = shadowColor
                    styles["panel.border"] = shadowColor
                },
                ansiLevel = AnsiLevel.TRUECOLOR
            )
            renderer = TerminalRenderer(terminal!!,)
            renderer.hideCursor()
            val scope = DispatchScopeImpl()
            dispatchScopeInstance = scope
            composer = Composer()
            recomposer = Recomposer(appScope)
            registerResizeHandler()

            scope.content()

            parseArguments()

            // Note: --help is no longer handled by the library.
            // Applications should implement their own help screen via navigation.

            if ("version" in parsedFlags) {
                terminal?.println("${config.name} ${config.version}")
                return 0
            }

            activeAreaHeight = config.activeAreaHeight

            terminal!!.enterRawMode(config.mouseTracking).use { rawMode ->
                val initialSize = terminal!!.updateSize()
                lastTerminalWidth = initialSize.width
                lastTerminalHeight = initialSize.height
                sizeDirty = false
                // Start input handling in raw mode before the first render so early keypresses
                // (especially Enter) aren't echoed into the UI and don't desync the renderer.
                val inputJob = appScope.launch(Dispatchers.IO) {
                    var consecutiveErrors = 0
                    inputLoop@ while (isActive && !exitRequested) {
                        val event = try {
                            rawMode.readEventOrNull(50.milliseconds)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            consecutiveErrors += 1
                            val backoff = (10L * consecutiveErrors).coerceAtMost(200L)
                            delay(backoff)
                            continue
                        } ?: continue

                        consecutiveErrors = 0
                        when (event) {
                            is KeyboardEvent -> {
                                if (shouldHandleExit(event)) {
                                    if (!config.requireExitDoublePress) {
                                        exitRequested = true
                                        return@launch
                                    }

                                    if (exitPromptState.isArmed) {
                                        exitRequested = true
                                        return@launch
                                    }

                                    exitPromptState.isArmed = true
                                    exitResetJob?.cancel()
                                    exitResetJob = appScope.launch {
                                        delay(config.exitTimeoutOnDoublePress)
                                        exitPromptState.isArmed = false
                                        recomposer.requestRecomposition()
                                    }
                                    recomposer.requestRecomposition()
                                    continue@inputLoop
                                }
                                val consumed = keyboardInterceptor.tryIntercept(event)
                                if (!consumed) {
                                    keyEventHandlers.forEach { handler -> handler(event) }
                                }
                                recomposer.requestRecomposition()
                            }
                            is MouseEvent -> {
                                mouseEventHandler?.invoke(event)
                                recomposer.requestRecomposition()
                            }
                        }
                    }
                }

                recomposer.registerComposition { composeAndRender() }
                // Render once before starting the recomposer loop to avoid concurrent initial renders.
                composeAndRender()
                val recomposerJob = recomposer.start()

                while (!exitRequested && appScope.isActive) {
                    delay(50)
                }

                renderer.clearActiveArea()
                inputJob.cancelAndJoin()
                recomposer.stop()
                recomposerJob.cancelAndJoin()
                renderer.showCursor()
            }

        } catch (_: CancellationException) {
            // Normal
        } catch (e: Exception) {

            terminal?.println("Error: ${e.message}")
            e.printStackTrace()
            exitCode = 1

        } finally {
            ViewModelStore.clear()
            appScope.cancel()
        }

        return exitCode
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
                    composer.setMeasurableCollector { measurable ->
                        rootMeasurable.set(measurable)
                    }

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
                        composer.setMeasurableCollector(null)
                        composer.endComposition()
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
            val constraints = Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = 0,
                maxHeight = Int.MAX_VALUE, // Unconstrained!
            )

            val placeable = measurable.measure(constraints)

            // Split content into scrolling (history) and active (input) portions
            val (scrollingLines, activeLines) = splitContentForRendering(
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
        dispatchArgs = DispatchArgs(
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
        val update = computeTerminalSizeUpdate(
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
        override fun launch(block: suspend CoroutineScope.() -> Unit) = appScope.launch(block = block)

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

    if (placeable is SegmentedPlaceable &&
        placeable.segmentHeights.isNotEmpty() &&
        placeable.segmentHeights.sum() == allLines.size
    ) {
        val segments = placeable.segmentHeights
        var remaining = activeAreaHeight
        var activeStartSegmentIndex = segments.lastIndex
        var clipFromStartSegment = 0

        for (index in segments.lastIndex downTo 0) {
            val height = segments[index]
            if (height <= remaining) {
                remaining -= height
                activeStartSegmentIndex = index
                if (remaining == 0) break
            } else {
                activeStartSegmentIndex = index
                clipFromStartSegment = height - remaining
                remaining = 0
                break
            }
        }

        val segmentStartLine = segments.take(activeStartSegmentIndex).sum()
        val activeStartLine = (segmentStartLine + clipFromStartSegment).coerceIn(0, allLines.size)
        var scrollingLineCount = segmentStartLine
        if (clipFromStartSegment > 0 && committedLineCount == 0) {
            scrollingLineCount = activeStartLine
        }
        scrollingLineCount = scrollingLineCount.coerceAtLeast(committedLineCount).coerceAtMost(allLines.size)
        val activeStartForRender = maxOf(activeStartLine, scrollingLineCount)
        return allLines.take(scrollingLineCount) to allLines.drop(activeStartForRender)
    }

    val activeLineCount = minOf(activeAreaHeight, allLines.size)
    val activeStartLine = allLines.size - activeLineCount
    var scrollingLineCount = activeStartLine.coerceAtLeast(committedLineCount).coerceAtMost(allLines.size)
    val activeStartForRender = maxOf(activeStartLine, scrollingLineCount)
    return allLines.take(scrollingLineCount) to allLines.drop(activeStartForRender)
}

internal fun shouldResetForResize(
    previousWidth: Int,
    previousHeight: Int,
    currentWidth: Int,
    currentHeight: Int,
): Boolean {
    return previousWidth != currentWidth || previousHeight != currentHeight
}

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
