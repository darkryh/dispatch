# Keyboard input reference

Packages: `io.github.darkryh.dispatch.input`, `io.github.darkryh.dispatch.runtime`. Module: `dispatch-runtime`.

Dispatch has two layers for the keyboard:

- A **typed key API** (`Key`, `KeyEvent`, `KeyStroke`) — the recommended way to inspect and match
  keys.
- A **declarative binding API** (`KeyBindings`) layered over a **`KeyboardInterceptor`** — how you
  register handlers from inside composition.

Interactive widgets (text fields, lists, menus, the command palette) already consume the keys they
need through the interceptor. Use the APIs here to add your own shortcuts and navigation.

## Key

```kotlin
sealed interface Key {
    enum class Named : Key {
        Enter, Escape, Tab, Backspace, Delete, Insert,
        ArrowUp, ArrowDown, ArrowLeft, ArrowRight,
        Home, End, PageUp, PageDown,
        PasteStart, PasteEnd,
        F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
        Unknown,
    }
    @JvmInline value class Char(val char: kotlin.Char) : Key

    companion object {
        val Enter; val Escape; val Tab; val Backspace; val Delete; val Insert
        val ArrowUp; val ArrowDown; val ArrowLeft; val ArrowRight
        val Home; val End; val PageUp; val PageDown
        val PasteStart; val PasteEnd
        val Space                 // = Char(' ')
        fun char(c: kotlin.Char): Key
    }
}
```

The companion provides aliases so you can write `Key.Enter`, `Key.ArrowUp`, or `Key.char('q')`.

## KeyEvent

```kotlin
@JvmInline value class KeyEvent {
    val raw: KeyboardEvent
    val key: Key
    val char: Char?
    val ctrl: Boolean
    val alt: Boolean
    val shift: Boolean
    val isText: Boolean
    fun isChar(c: Char, ignoreCase: Boolean = true): Boolean
    fun matches(stroke: KeyStroke): Boolean
}

fun KeyboardEvent.asKeyEvent(): KeyEvent
fun keyEvent(key: String, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false): KeyEvent
```

A typed view over a Mordant `KeyboardEvent`. Convert a raw event with `asKeyEvent()`; build one in
tests with `keyEvent(...)`. `isText` is true for printable character input.

```kotlin
interceptor.register { raw ->
    val event = raw.asKeyEvent()
    when (event.key) {
        Key.ArrowUp -> { moveUp(); true }
        Key.ArrowDown -> { moveDown(); true }
        else -> false
    }
}
```

## KeyStroke

```kotlin
data class KeyStroke(
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    fun matches(event: KeyEvent): Boolean
    fun label(): String
}
```

A key plus modifiers. Build one with the extension helpers:

```kotlin
val Key.ctrl: KeyStroke
val Key.alt: KeyStroke
val Key.shift: KeyStroke
fun Key.stroke(): KeyStroke
fun ctrl(c: Char): KeyStroke   // Ctrl + char
fun alt(c: Char): KeyStroke    // Alt + char
fun shift(c: Char): KeyStroke  // Shift + char
```

```kotlin
val save = ctrl('s')
val quit = Key.Escape.stroke()
```

## KeyBindings

```kotlin
@Composable
fun KeyBindings(
    enabled: Boolean = true,
    priority: Int = 0,
    block: KeyBindingsScope.() -> Unit,
)

class KeyBindingsScope {
    fun on(stroke: KeyStroke, description: String = "", action: () -> Unit)
    fun on(key: Key, description: String = "", action: () -> Unit)
    fun bindings(): List<KeyBinding>
    fun handle(event: KeyboardEvent): Boolean
}

data class KeyBinding(val stroke: KeyStroke, val description: String = "", val action: () -> Unit)
```

The declarative way to register shortcuts from composition. Bindings register on the active
`KeyboardInterceptor` and unregister when the composable leaves. Higher `priority` runs first.

```kotlin
KeyBindings {
    on(ctrl('s'), "save") { save() }
    on(Key.Escape, "back") { navigator.popBackStack() }
}
```

## KeyboardInterceptor

```kotlin
class KeyboardInterceptor(private val parent: KeyboardInterceptor? = null) {
    fun register(handler: (KeyboardEvent) -> Boolean): () -> Unit
    fun register(priority: Int, handler: (KeyboardEvent) -> Boolean): () -> Unit
    fun tryIntercept(event: KeyboardEvent): Boolean
    fun hasInterceptors(): Boolean
    fun reset()
}
```

The lower-level mechanism `KeyBindings` builds on. A handler returns `true` to **consume** the event
(stopping lower-priority handlers and widgets from seeing it) or `false` to pass it on. `register`
returns a dispose lambda. Higher priority — then most recently registered — runs first.

Get the active interceptor from `LocalKeyboardInterceptor.current`, and register inside a
`DisposableEffect` so the handler is removed when the composable leaves:

```kotlin
val interceptor = LocalKeyboardInterceptor.current
DisposableEffect(interceptor) {
    val dispose = interceptor.register(priority = 100) { raw ->
        if (raw.asKeyEvent().key == Key.Escape) { onBack(); true } else false
    }
    onDispose { dispose() }
}
```

For an app-wide raw hook outside composition, see
[`DispatchScope.onKeyEvent`](application.md#dispatchscope).

## See also

- [Handle keyboard input](../how-to/handle-keyboard-input.md) — a task-focused guide.
- [Application & configuration](application.md#exitkeybinding) — exit-key configuration.
