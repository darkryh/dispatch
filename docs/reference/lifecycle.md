# Lifecycle reference

Package: `io.github.darkryh.dispatch.lifecycle`. Module: `dispatch-lifecycle`.

Each navigation entry owns a lifecycle. The lifecycle module is small: a state enum, an owner
interface, and an observable registry. Navigation drives these for you; read them when you need to
react to an entry being started, stopped, or destroyed.

## LifecycleState

```kotlin
enum class LifecycleState { INITIALIZED, STARTED, STOPPED, DESTROYED }
```

The four states an entry moves through. There is no separate event type — transitions are
state-based.

## LifecycleOwner

```kotlin
interface LifecycleOwner {
    val lifecycle: LifecycleRegistry
}
```

Implemented by `NavBackStackEntry`. Obtain the current owner from `LocalLifecycleOwner.current` (see
the [navigation reference](navigation.md#composition-locals)).

## LifecycleRegistry

```kotlin
class LifecycleRegistry(initialState: LifecycleState = LifecycleState.INITIALIZED) {
    var currentState: LifecycleState   // private set
    fun moveTo(state: LifecycleState)
    fun addObserver(observer: (LifecycleState) -> Unit): AutoCloseable
    fun removeObserver(observer: (LifecycleState) -> Unit)
}
```

An observable state holder. `moveTo` transitions and notifies observers; on `DESTROYED` it clears
all observers. `addObserver` returns an `AutoCloseable` that removes the observer.

```kotlin
val owner = LocalLifecycleOwner.current
DisposableEffect(owner) {
    val handle = owner?.lifecycle?.addObserver { state ->
        if (state == LifecycleState.STOPPED) pause()
    }
    onDispose { handle?.close() }
}
```

## See also

- [Navigation](navigation.md) — where entries and their lifecycles are created.
- [View models & MVI](viewmodel.md) — view models are cleared when their entry is destroyed.
