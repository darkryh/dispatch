package io.github.darkryh.dispatch.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
     * Lock guarding [closeables] and the clear transition so that
     * [addCloseable] and [clear] cannot race (check-then-act on the
     * cleared flag must be atomic with respect to the list mutation).
     */
    private val lock = Any()

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
            val toClose = synchronized(lock) {
                val snapshot = closeables.toList()
                closeables.clear()
                snapshot
            }
            toClose.forEach {
                try {
                    it.close()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
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
        // Decide whether to register or close immediately inside the lock so the
        // cleared flag and the closeables list stay consistent versus clear().
        val closeNow = synchronized(lock) {
            if (isCleared) {
                true
            } else {
                closeables.add(closeable)
                false
            }
        }
        if (closeNow) {
            closeable.close()
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
     * Intent channel for processing user actions. Unlimited so [sendIntent] can enqueue
     * synchronously in call order without dropping or allocating a coroutine per intent.
     */
    private val _intents = Channel<I>(Channel.UNLIMITED)

    init {
        // Process intents in strict arrival order on the single collector coroutine.
        viewModelScope.launch {
            for (intent in _intents) {
                handleIntent(intent)
            }
        }
    }

    /**
     * Send an intent to be processed. trySend enqueues in call order, never suspends, and (with an
     * unlimited channel) never drops — replacing a per-call `launch { emit }` that could reorder
     * intents under a multi-threaded dispatcher.
     */
    fun sendIntent(intent: I) {
        _intents.trySend(intent)
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

    // Unlimited channel: ordered, non-dropping, no per-intent coroutine. See MviViewModel.
    private val _intents = Channel<I>(Channel.UNLIMITED)
    private val _sideEffect = MutableSharedFlow<E>(extraBufferCapacity = 64)
    val sideEffect: SharedFlow<E> = _sideEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            for (intent in _intents) {
                handleIntent(intent)
            }
        }
    }

    fun sendIntent(intent: I) {
        _intents.trySend(intent)
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
