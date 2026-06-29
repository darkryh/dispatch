# View models & MVI reference

Package: `com.ead.dispatch.viewmodel`. Module: `dispatch-viewmodel`.

A view model holds a screen's state and logic, surviving recomposition and scoped to a navigation
entry. Dispatch provides a base `ViewModel` plus MVI-flavored subclasses, helpers for adapting
`StateFlow` to Compose, and composables for obtaining a view model.

## ViewModel

```kotlin
abstract class ViewModel : Closeable {
    val viewModelScope: CoroutineScope
    val isCleared: Boolean
    protected open fun onCleared()
    fun clear()
    override fun close()
    fun addCloseable(closeable: Closeable)
    protected fun launch(context: CoroutineContext = Dispatchers.Default, block: suspend CoroutineScope.() -> Unit): Job
    protected fun <T> MutableStateFlow<T>.asViewModelStateFlow(): StateFlow<T>
}
```

The base class. `viewModelScope` is a `SupervisorJob`-backed scope cancelled when the view model is
cleared. Override `onCleared()` to release resources, or register them with `addCloseable`.

## State and MVI base classes

Pick the base class that matches what your screen needs. State `S`, intent `I`, and effect `E` are
your own types — typically a `data class` state and `sealed interface` intents/effects.

### StateViewModel — observable state only

```kotlin
abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    protected val _state: MutableStateFlow<S>
    val state: StateFlow<S>
    val currentState: S
    protected fun updateState(transform: (S) -> S)
    protected fun setState(newState: S)
}
```

### MviViewModel — state + intents

```kotlin
abstract class MviViewModel<S, I>(initialState: S) : StateViewModel<S>(initialState) {
    fun sendIntent(intent: I)
    protected abstract suspend fun handleIntent(intent: I)
}
```

Intents are processed in arrival order on a single collector coroutine. Override `handleIntent` to
react; call `updateState` / `setState` to change state.

### SideEffectViewModel — state + one-time effects

```kotlin
abstract class SideEffectViewModel<S, E>(initialState: S) : StateViewModel<S>(initialState) {
    val sideEffect: SharedFlow<E>
    protected fun emitSideEffect(effect: E)
}
```

### FullMviViewModel — state + intents + effects

```kotlin
abstract class FullMviViewModel<S, I, E>(initialState: S) : ViewModel() {
    protected val _state: MutableStateFlow<S>
    val state: StateFlow<S>
    val currentState: S
    val sideEffect: SharedFlow<E>
    fun sendIntent(intent: I)
    protected fun updateState(transform: (S) -> S)
    protected fun setState(newState: S)
    protected fun emitSideEffect(effect: E)
    protected abstract suspend fun handleIntent(intent: I)
}
```

**Example**

```kotlin
data class CounterState(val count: Int = 0)
sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Reset : CounterIntent
}

class CounterViewModel : MviViewModel<CounterState, CounterIntent>(CounterState()) {
    override suspend fun handleIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> updateState { it.copy(count = it.count + 1) }
            CounterIntent.Reset -> updateState { it.copy(count = 0) }
        }
    }
}
```

## Obtaining a view model in a screen

```kotlin
@Composable inline fun <reified T : ViewModel> viewModel(key: String = /* call-site key */, noinline factory: () -> T): T
@Composable inline fun <reified T : ViewModel> viewModel(): T
```

`viewModel()` returns a view model from the current `LocalViewModelProvider` (set by `NavDisplay`),
falling back to an app-lifetime store. The no-arg overload constructs `T` via its no-arg
constructor; pass a `factory` lambda when the view model has dependencies and you are not using Koin.

```kotlin
@Composable
fun CounterScreen(viewModel: CounterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Text("Count: ${state.count}")
    KeyBindings { on(Key.char('+')) { viewModel.sendIntent(CounterIntent.Increment) } }
}
```

With Koin, `viewModel()` resolves the view model from the container — see the
[Koin reference](koin.md#connecting-koin-to-navigation).

## Consuming side effects

```kotlin
@Composable fun <T> SharedFlow<T>.collectSideEffect(handler: (T) -> Unit)
```

Collects a one-time effect stream in a `LaunchedEffect` and invokes `handler` for each emission:

```kotlin
viewModel.sideEffect.collectSideEffect { effect ->
    when (effect) {
        ChatEffect.ScrollToBottom -> scrollState.scrollToBottom()
    }
}
```

## StateFlow ↔ Compose helpers

```kotlin
fun <T> MutableStateFlow<T>.asMutableState(): MutableState<T>
fun <T> StateFlow<T>.asState(): State<T>
fun <T, R> StateFlow<T>.mapState(scope: CoroutineScope, transform: (T) -> R): StateFlow<R>
fun <T1, T2, R> combineStates(flow1: StateFlow<T1>, flow2: StateFlow<T2>, scope: CoroutineScope, transform: (T1, T2) -> R): StateFlow<R>
fun <T1, T2, T3, R> combineStates(flow1: StateFlow<T1>, flow2: StateFlow<T2>, flow3: StateFlow<T3>, scope: CoroutineScope, transform: (T1, T2, T3) -> R): StateFlow<R>
fun <T> StateFlow<T>.debounce(timeoutMillis: Long, scope: CoroutineScope): StateFlow<T>
fun <T> StateFlow<T>.filterState(scope: CoroutineScope, predicate: (T) -> Boolean): StateFlow<T>
fun <T, K> StateFlow<T>.distinctByKey(scope: CoroutineScope, keySelector: (T) -> K): StateFlow<T>
```

## Result wrappers

Two `sealed` wrappers for async data:

```kotlin
sealed class LoadingState<out T> {
    object Loading
    data class Success<T>(val data: T)
    data class Error(val message: String, val cause: Throwable? = null)
    // isLoading, isSuccess, isError; getOrNull(); getOrDefault(default)
}

sealed class Resource<out T> {
    object Idle
    object Loading
    data class Success<T>(val data: T)
    data class Error(val message: String, val cause: Throwable? = null)
    // isIdle, isLoading, isSuccess, isError; getOrNull()
}
```

## Factories and providers

You rarely touch these directly — `NavDisplay` and Koin wire them — but they are the extension
points for custom view-model creation.

```kotlin
interface ViewModelFactory { fun <T : ViewModel> create(modelClass: KClass<T>): T }
interface SavedStateViewModelFactory : ViewModelFactory { fun <T : ViewModel> create(modelClass: KClass<T>, savedStateHandle: SavedStateHandle): T }

class DefaultViewModelFactory : ViewModelFactory             // reflection; needs a no-arg constructor
class LambdaViewModelFactory(creators: Map<KClass<out ViewModel>, () -> ViewModel>) : ViewModelFactory
fun viewModelFactory(builder: LambdaViewModelFactory.Builder.() -> Unit): LambdaViewModelFactory

class ViewModelProvider(factory: ViewModelFactory = DefaultViewModelFactory(), savedStateHandle: SavedStateHandle? = null)
val LocalViewModelProvider: ProvidableCompositionLocal<ViewModelProvider?>

@Composable fun ViewModelProviderScope(factory: ViewModelFactory = DefaultViewModelFactory(), savedStateHandle: SavedStateHandle? = null, content: @Composable () -> Unit)
@Composable inline fun <reified T : ViewModel> rememberViewModel(key: String = /* type name */, noinline factory: () -> T): T
```

## See also

- [Use view models and MVI](../how-to/use-viewmodels.md) — a task-focused guide.
- [Navigation](navigation.md) — view models are scoped to navigation entries.
- [Koin DI](koin.md) — resolve view models with their dependencies.
