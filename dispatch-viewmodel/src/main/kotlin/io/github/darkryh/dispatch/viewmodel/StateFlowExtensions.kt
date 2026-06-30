@file:Suppress("UnusedImports") // detekt mis-flags the flow `debounce` import (shadowed by a local extension)

package io.github.darkryh.dispatch.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun <T> SharedFlow<T>.collectSideEffect(handler: (T) -> Unit) {
    val latestHandler = rememberUpdatedState(handler)
    LaunchedEffect(this) {
        collect { effect -> latestHandler.value(effect) }
    }
}

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

fun <T> StateFlow<T>.asState(): State<T> {
    val flow = this
    return object : State<T> {
        override val value: T get() = flow.value
    }
}

fun <T, R> StateFlow<T>.mapState(
    scope: CoroutineScope,
    transform: (T) -> R,
): StateFlow<R> = map(transform).stateIn(scope, SharingStarted.Eagerly, transform(value))

fun <T1, T2, R> combineStates(
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    scope: CoroutineScope,
    transform: (T1, T2) -> R,
): StateFlow<R> =
    combine(flow1, flow2, transform).stateIn(
        scope,
        SharingStarted.Eagerly,
        transform(flow1.value, flow2.value),
    )

fun <T1, T2, T3, R> combineStates(
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    scope: CoroutineScope,
    transform: (T1, T2, T3) -> R,
): StateFlow<R> =
    combine(flow1, flow2, flow3, transform).stateIn(
        scope,
        SharingStarted.Eagerly,
        transform(flow1.value, flow2.value, flow3.value),
    )

@OptIn(FlowPreview::class)
fun <T> StateFlow<T>.debounce(
    timeoutMillis: Long,
    scope: CoroutineScope,
): StateFlow<T> = debounce(timeoutMillis).stateIn(scope, SharingStarted.Eagerly, value)

fun <T> StateFlow<T>.filterState(
    scope: CoroutineScope,
    predicate: (T) -> Boolean,
): StateFlow<T> = filter(predicate).stateIn(scope, SharingStarted.Eagerly, value)

fun <T, K> StateFlow<T>.distinctByKey(
    scope: CoroutineScope,
    keySelector: (T) -> K,
): StateFlow<T> = distinctUntilChangedBy(keySelector).stateIn(scope, SharingStarted.Eagerly, value)
