package com.ead.dispatch.viewmodel

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.dispatchCoroutineScope
import com.ead.dispatch.runtime.rememberCallback
import com.ead.dispatch.state.State
import com.ead.dispatch.state.MutableState
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Collect a StateFlow as Dispatch State.
 *
 * This bridges Kotlin coroutines StateFlow with Dispatch's reactive state system.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun MyScreen(viewModel: MyViewModel) {
 *     val count by viewModel.count.collectAsState()
 *     Text("Count: $count")
 * }
 * ```
 */
@Dispatchable
fun <T> StateFlow<T>.collectAsState(): State<T> {
    val initial = this.value
    val state = remember { mutableStateOf(initial) }

    val flow = this
    val recomposer = com.ead.dispatch.runtime.Recomposer.current
    DisposableEffect(flow) {
        val scope = dispatchCoroutineScope()
        scope.launch {
            flow.collect { value ->
                state.value = value
                recomposer?.requestRecomposition()
            }
        }
        onDispose { scope.cancel() }
    }

    return state
}

/**
 * Collect a StateFlow with an initial value.
 */
@Dispatchable
fun <T> StateFlow<T>.collectAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }

    val flow = this
    val recomposer = com.ead.dispatch.runtime.Recomposer.current
    DisposableEffect(flow) {
        val scope = dispatchCoroutineScope()
        scope.launch {
            flow.collect { value ->
                state.value = value
                recomposer?.requestRecomposition()
            }
        }
        onDispose { scope.cancel() }
    }

    return state
}

/**
 * Collect a Flow as State.
 *
 * Unlike StateFlow, Flow requires an initial value since it may not have
 * a current value.
 */
@Dispatchable
fun <T> Flow<T>.collectAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }

    val flow = this
    val recomposer = com.ead.dispatch.runtime.Recomposer.current
    DisposableEffect(flow) {
        val scope = dispatchCoroutineScope()
        scope.launch {
            flow.collect { value ->
                state.value = value
                recomposer?.requestRecomposition()
            }
        }
        onDispose { scope.cancel() }
    }

    return state
}

/**
 * Collect a SharedFlow as State with an initial value.
 */
@Dispatchable
fun <T> SharedFlow<T>.collectAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }

    val flow = this
    val recomposer = com.ead.dispatch.runtime.Recomposer.current
    DisposableEffect(flow) {
        val scope = dispatchCoroutineScope()
        scope.launch {
            flow.collect { value ->
                state.value = value
                recomposer?.requestRecomposition()
            }
        }
        onDispose { scope.cancel() }
    }

    return state
}

/**
 * Collect side effects from a SharedFlow.
 *
 * This is useful for one-time events like navigation or showing messages.
 */
@Dispatchable
fun <T> SharedFlow<T>.collectSideEffect(handler: (T) -> Unit) {
    val flow = this
    val latestHandler = rememberCallback(handler)
    DisposableEffect(flow) {
        val scope = dispatchCoroutineScope()
        scope.launch {
            flow.collect { effect ->
                latestHandler(effect)
            }
        }
        onDispose { scope.cancel() }
    }
}

/**
 * Create a StateFlow-backed MutableState.
 *
 * Changes to the state will be reflected in the StateFlow.
 */
fun <T> MutableStateFlow<T>.asMutableState(): MutableState<T> {
    val flow = this
    return object : MutableState<T> {
        override var value: T
            get() = flow.value
            set(newValue) {
                flow.value = newValue
            }

        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }
}

/**
 * Create a read-only State from a StateFlow.
 */
fun <T> StateFlow<T>.asState(): State<T> {
    val flow = this
    return object : State<T> {
        override val value: T get() = flow.value
    }
}

/**
 * Map a StateFlow to another StateFlow.
 */
fun <T, R> StateFlow<T>.mapState(
    scope: CoroutineScope,
    transform: (T) -> R
): StateFlow<R> {
    return this.map { transform(it) }
        .stateIn(scope, SharingStarted.Eagerly, transform(this.value))
}

/**
 * Combine two StateFlows.
 */
fun <T1, T2, R> combineStates(
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    scope: CoroutineScope,
    transform: (T1, T2) -> R
): StateFlow<R> {
    return combine(flow1, flow2) { v1, v2 -> transform(v1, v2) }
        .stateIn(scope, SharingStarted.Eagerly, transform(flow1.value, flow2.value))
}

/**
 * Combine three StateFlows.
 */
fun <T1, T2, T3, R> combineStates(
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    scope: CoroutineScope,
    transform: (T1, T2, T3) -> R
): StateFlow<R> {
    return combine(flow1, flow2, flow3) { v1, v2, v3 -> transform(v1, v2, v3) }
        .stateIn(scope, SharingStarted.Eagerly, transform(flow1.value, flow2.value, flow3.value))
}

/**
 * Debounce state updates.
 */
@OptIn(FlowPreview::class)
fun <T> StateFlow<T>.debounce(
    timeoutMillis: Long,
    scope: CoroutineScope
): StateFlow<T> {
    return this.debounce(timeoutMillis)
        .stateIn(scope, SharingStarted.Eagerly, this.value)
}

/**
 * Filter state updates.
 */
fun <T> StateFlow<T>.filterState(
    scope: CoroutineScope,
    predicate: (T) -> Boolean
): StateFlow<T> {
    return this.filter { predicate(it) }
        .stateIn(scope, SharingStarted.Eagerly, this.value)
}

/**
 * Distinct state updates by key.
 */
fun <T, K> StateFlow<T>.distinctByKey(
    scope: CoroutineScope,
    keySelector: (T) -> K
): StateFlow<T> {
    return this.distinctUntilChangedBy(keySelector)
        .stateIn(scope, SharingStarted.Eagerly, this.value)
}
