package com.ead.dispatch.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Base class for ViewModels that manage UI state and business logic.
 *
 * ViewModels survive configuration changes (like terminal resize) and provide
 * a clean separation between UI and business logic.
 *
 * Example:
 * ```kotlin
 * class CounterViewModel : ViewModel() {
 *     private val _count = MutableStateFlow(0)
 *     val count: StateFlow<Int> = _count.asStateFlow()
 *
 *     fun increment() {
 *         _count.value++
 *     }
 *
 *     fun decrement() {
 *         _count.value--
 *     }
 * }
 *
 * @Composable
 * fun CounterScreen(viewModel: CounterViewModel = viewModel()) {
 *     val count by viewModel.count.collectAsState()
 *     Column {
 *         Text("Count: $count")
 *         Button("Increment") { viewModel.increment() }
 *         Button("Decrement") { viewModel.decrement() }
 *     }
 * }
 * ```
 */
abstract class ViewModel : Closeable {
    /**
     * The coroutine scope for this ViewModel.
     *
     * Cancelled when the ViewModel is cleared/closed.
     */
    val viewModelScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("ViewModel")
    )

    /**
     * Whether this ViewModel has been cleared.
     */
    private val _isCleared = AtomicBoolean(false)

    /**
     * Check if the ViewModel has been cleared.
     */
    val isCleared: Boolean get() = _isCleared.get()

    /**
     * Closeable resources to clean up when the ViewModel is cleared.
     */
    private val closeables = mutableListOf<Closeable>()

    /**
     * Called when this ViewModel is no longer used and will be destroyed.
     *
     * Override this to clean up resources.
     */
    protected open fun onCleared() {}

    /**
     * Clear this ViewModel.
     */
    fun clear() {
        if (_isCleared.compareAndSet(false, true)) {
            closeables.forEach {
                try {
                    it.close()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
            closeables.clear()
            viewModelScope.cancel()
            onCleared()
        }
    }

    override fun close() {
        clear()
    }

    /**
     * Add a closeable to be closed when the ViewModel is cleared.
     */
    fun addCloseable(closeable: Closeable) {
        if (isCleared) {
            closeable.close()
        } else {
            closeables.add(closeable)
        }
    }

    /**
     * Launch a coroutine in the ViewModel scope.
     */
    protected fun launch(
        context: CoroutineContext = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(context, block = block)
    }

    /**
     * Create a state flow that's automatically collected in the viewModelScope.
     */
    protected fun <T> MutableStateFlow<T>.asViewModelStateFlow(): StateFlow<T> =
        this.stateIn(viewModelScope, SharingStarted.Eagerly, this.value)
}

/**
 * A ViewModel with a single UI state.
 */
abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    /**
     * Internal mutable state.
     */
    protected val _state = MutableStateFlow(initialState)

    /**
     * The current UI state.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * Current state value.
     */
    val currentState: S get() = _state.value

    /**
     * Update state using a transform function.
     */
    protected fun updateState(transform: (S) -> S) {
        _state.update(transform)
    }

    /**
     * Set state to a new value.
     */
    protected fun setState(newState: S) {
        _state.value = newState
    }
}

/**
 * A ViewModel that follows MVI (Model-View-Intent) pattern.
 */
abstract class MviViewModel<S, I>(initialState: S) : StateViewModel<S>(initialState) {
    /**
     * Intent channel for processing user actions.
     */
    private val _intents = MutableSharedFlow<I>(extraBufferCapacity = 64)

    init {
        // Process intents
        viewModelScope.launch {
            _intents.collect { intent ->
                handleIntent(intent)
            }
        }
    }

    /**
     * Send an intent to be processed.
     */
    fun sendIntent(intent: I) {
        viewModelScope.launch {
            _intents.emit(intent)
        }
    }

    /**
     * Handle an intent.
     */
    protected abstract suspend fun handleIntent(intent: I)
}

/**
 * Side effects that should be handled once (like showing a toast, navigation).
 */
abstract class SideEffectViewModel<S, E>(initialState: S) : StateViewModel<S>(initialState) {
    /**
     * Channel for one-time side effects.
     */
    private val _sideEffect = MutableSharedFlow<E>(extraBufferCapacity = 64)

    /**
     * Side effects flow.
     */
    val sideEffect: SharedFlow<E> = _sideEffect.asSharedFlow()

    /**
     * Emit a side effect.
     */
    protected fun emitSideEffect(effect: E) {
        viewModelScope.launch {
            _sideEffect.emit(effect)
        }
    }
}

/**
 * Full MVI ViewModel with state, intents, and side effects.
 */
abstract class FullMviViewModel<S, I, E>(initialState: S) : ViewModel() {
    protected val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()
    val currentState: S get() = _state.value

    private val _intents = MutableSharedFlow<I>(extraBufferCapacity = 64)
    private val _sideEffect = MutableSharedFlow<E>(extraBufferCapacity = 64)
    val sideEffect: SharedFlow<E> = _sideEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            _intents.collect { intent ->
                handleIntent(intent)
            }
        }
    }

    fun sendIntent(intent: I) {
        viewModelScope.launch {
            _intents.emit(intent)
        }
    }

    protected fun updateState(transform: (S) -> S) {
        _state.update(transform)
    }

    protected fun setState(newState: S) {
        _state.value = newState
    }

    protected fun emitSideEffect(effect: E) {
        viewModelScope.launch {
            _sideEffect.emit(effect)
        }
    }

    protected abstract suspend fun handleIntent(intent: I)
}

/**
 * Loading state wrapper.
 */
sealed class LoadingState<out T> {
    object Loading : LoadingState<Nothing>()
    data class Success<T>(val data: T) : LoadingState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : LoadingState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default
}

/**
 * Resource wrapper for async data.
 */
sealed class Resource<out T> {
    object Idle : Resource<Nothing>()
    object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()

    val isIdle: Boolean get() = this is Idle
    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
}
