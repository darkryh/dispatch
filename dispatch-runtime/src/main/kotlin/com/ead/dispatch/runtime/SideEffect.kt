package com.ead.dispatch.runtime

import com.ead.dispatch.state.remember
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Schedule an effect to run after composition completes.
 *
 * The effect runs every time the composition is executed.
 * Use [LaunchedEffect] for effects that should run in a coroutine.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Counter(count: Int) {
 *     SideEffect {
 *         // perform non-render side effects here
 *     }
 * }
 * ```
 */
fun SideEffect(effect: () -> Unit) {
    // Register effect to run after composition
    EffectRunner.registerSideEffect(effect)
}

/**
 * Launch a coroutine that runs as long as the composition is active.
 *
 * The coroutine is cancelled when the composition leaves the tree,
 * or when any of the keys change.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun DataLoader(userId: String) {
 *     var data by remember { mutableStateOf<Data?>(null) }
 *
 *     LaunchedEffect(userId) {
 *         data = loadData(userId)
 *     }
 *
 *     if (data != null) {
 *         DataContent(data)
 *     } else {
 *         LoadingIndicator()
 *     }
 * }
 * ```
 *
 * @param key1 Key that triggers effect restart when changed.
 * @param block The coroutine to launch.
 */
fun LaunchedEffect(key1: Any?, block: suspend CoroutineScope.() -> Unit) {
    val recomposer = Recomposer.current
    val effectState = remember(key1) { LaunchedEffectState(key1, recomposer) }

    if (effectState.key != key1) {
        effectState.cancel()
        effectState.key = key1
    }

    if (!effectState.isRunning) {
        effectState.launch(block)
    }
}

/**
 * Launch a coroutine when entering the composition.
 *
 * The coroutine runs once when the composition is first created.
 *
 * @param block The coroutine to launch.
 */
fun LaunchedEffect(block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(Unit, block)
}

/**
 * Launch a coroutine with multiple keys.
 *
 * The coroutine restarts when any key changes.
 */
fun LaunchedEffect(key1: Any?, key2: Any?, block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(key1 to key2, block)
}

/**
 * Launch a coroutine with multiple keys.
 */
fun LaunchedEffect(key1: Any?, key2: Any?, key3: Any?, block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(Triple(key1, key2, key3), block)
}

/**
 * Launch a coroutine with vararg keys.
 */
fun LaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(keys.toList(), block)
}

/**
 * State holder for [LaunchedEffect].
 */
internal class LaunchedEffectState(var key: Any?, private val recomposer: Recomposer?) {
    private var job: Job? = null
    private val scope = dispatchCoroutineScope()

    val isRunning: Boolean get() = job?.isActive == true

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        val context = if (recomposer != null) {
            Recomposer.currentRecomposerLocal.asContextElement(recomposer)
        } else {
            EmptyCoroutineContext
        }
        job = scope.launch(context, block = block)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun dispose() {
        cancel()
        scope.cancel()
    }
}

/**
 * Register an effect that runs when leaving the composition.
 *
 * Use this for cleanup that needs to happen when a composable
 * is removed from the tree.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun EventListener() {
 *     DisposableEffect(Unit) {
 *         val listener = registerListener()
 *
 *         onDispose {
 *             unregisterListener(listener)
 *         }
 *     }
 * }
 * ```
 *
 * @param key1 Key that triggers effect restart when changed.
 * @param effect The effect that returns a dispose callback.
 */
fun DisposableEffect(key1: Any?, effect: DisposableEffectScope.() -> DisposableEffectResult) {
    val effectState = remember { DisposableEffectState(key1) }

    if (effectState.key != key1) {
        effectState.dispose()
        effectState.key = key1
        effectState.effect = null
    }

    if (effectState.effect == null) {
        val scope = DisposableEffectScope()
        effectState.effect = scope.effect()
    }
}

/**
 * Register an effect that runs once on composition.
 */
fun DisposableEffect(effect: DisposableEffectScope.() -> DisposableEffectResult) {
    DisposableEffect(Unit, effect)
}

/**
 * Scope for [DisposableEffect].
 */
class DisposableEffectScope {
    /**
     * Register a dispose callback.
     */
    fun onDispose(onDisposeCallback: () -> Unit): DisposableEffectResult {
        return DisposableEffectResult(onDisposeCallback)
    }
}

/**
 * Result of a [DisposableEffect].
 */
class DisposableEffectResult(
    internal val onDispose: () -> Unit
)

/**
 * State holder for [DisposableEffect].
 */
internal class DisposableEffectState(var key: Any?) {
    var effect: DisposableEffectResult? = null

    fun dispose() {
        effect?.onDispose?.invoke()
        effect = null
    }
}

/**
 * Manages side effect execution.
 */
object EffectRunner {
    private val pendingEffects = ThreadLocal<MutableList<() -> Unit>>()

    /**
     * Register a side effect to run after composition.
     */
    fun registerSideEffect(effect: () -> Unit) {
        val effects = pendingEffects.get() ?: mutableListOf<() -> Unit>().also {
            pendingEffects.set(it)
        }
        effects.add(effect)
    }

    /**
     * Run all pending side effects.
     */
    fun runPendingEffects() {
        val effects = pendingEffects.get() ?: return
        pendingEffects.remove()

        effects.forEach { effect ->
            try {
                effect()
            } catch (e: Exception) {
                System.err.println("Side effect error: ${e.message}")
            }
        }
    }

    /**
     * Clear pending effects without running them.
     */
    fun clearPendingEffects() {
        pendingEffects.remove()
    }
}
