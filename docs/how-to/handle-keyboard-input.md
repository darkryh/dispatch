# Handle keyboard input

This guide shows how to react to keys in a Dispatch app: declaring shortcuts, inspecting key events,
consuming keys so widgets don't also see them, and ordering handlers by priority. It assumes you can
build and run an app.

Interactive widgets (text fields, lists, the command palette) already handle the keys they need. Use
the techniques here for your own shortcuts and navigation.

## Declare shortcuts with KeyBindings

`KeyBindings` is the simplest way to bind keys. It registers while the composable is in the tree and
unregisters when it leaves.

```kotlin
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.input.ctrl
import io.github.darkryh.dispatch.runtime.KeyBindings

KeyBindings {
    on(Key.char('n'), "new") { create() }
    on(ctrl('s'), "save") { save() }
    on(Key.Escape, "back") { navigator.popBackStack() }
}
```

Bind a bare `Key`, or a `KeyStroke` built with `ctrl(...)`, `alt(...)`, `shift(...)`, or
`Key.X.ctrl`.

## Inspect a key event directly

For dynamic handling — say, routing arrow keys to a custom list — register on the keyboard
interceptor and inspect the typed event. Convert the raw event with `asKeyEvent()`.

```kotlin
import io.github.darkryh.dispatch.input.asKeyEvent
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import androidx.compose.runtime.DisposableEffect

val interceptor = LocalKeyboardInterceptor.current
DisposableEffect(interceptor) {
    val dispose = interceptor.register { raw ->
        val event = raw.asKeyEvent()
        when (event.key) {
            Key.ArrowUp -> { moveUp(); true }
            Key.ArrowDown -> { moveDown(); true }
            else -> false
        }
    }
    onDispose { dispose() }
}
```

A handler returns:

- `true` to **consume** the key — lower-priority handlers and widgets won't see it.
- `false` to **pass it on**.

`register` returns a dispose lambda; call it in `onDispose` so the handler is removed when the
composable leaves.

## Read text and modifiers

`KeyEvent` exposes the typed key, the character, and modifier flags.

```kotlin
interceptor.register { raw ->
    val event = raw.asKeyEvent()
    when {
        event.isChar('q') -> { quit(); true }
        event.ctrl && event.isChar('a') -> { selectAll(); true }
        event.isText -> { append(event.char!!); true }   // printable input
        else -> false
    }
}
```

## Order handlers by priority

When several handlers could match the same key, give the one that should win a higher `priority`.
Higher priority — then most recently registered — runs first.

```kotlin
// A global command palette that should always win
interceptor.register(priority = 1000) { raw ->
    if (raw.asKeyEvent().matches(ctrl('p'))) { openPalette(); true } else false
}

// A screen-local back handler, lower priority
interceptor.register(priority = 5) { raw ->
    if (raw.asKeyEvent().key == Key.Escape) navigator.popBackStack() else false
}
```

`KeyBindings` also takes a `priority` parameter for the same effect.

## Configure the quit key

The key that exits the app is set in `config`, not through a binding:

```kotlin
config {
    exitKeys(ExitKeyBinding.ctrl("C"))
    requireExitDoublePress = true                 // press twice to quit
    exitTimeoutOnDoublePress = 1_500.milliseconds // window for the second press
}
```

## Related

- [Keyboard input reference](../reference/input.md) — `Key`, `KeyEvent`, `KeyStroke`, and the
  interceptor.
- [Application & configuration](../reference/application.md#exitkeybinding) — exit-key options.
