# Theme reference

Package: `com.ead.dispatch.theme`. Module: `dispatch-runtime`.

A theme is a palette of named [Mordant](https://github.com/ajalt/mordant) `TextStyle`s. You set one
in `config { theme = … }`, and read it in composition with `LocalTheme.current`.

## DispatchTheme

```kotlin
data class DispatchTheme(
    val primary: TextStyle,
    val secondary: TextStyle,
    val muted: TextStyle,
    val accent: TextStyle,
    val success: TextStyle,
    val warning: TextStyle,
    val error: TextStyle,
    val info: TextStyle,
    val code: TextStyle,
    val link: TextStyle,
    val border: TextStyle,
    val cursor: TextStyle,
    val selection: TextStyle,
)
```

Each field is a `TextStyle` (color plus attributes). Use them as the `style` argument to widgets:

```kotlin
val theme = LocalTheme.current
Text("Saved", style = theme.success)
Text("Heads up", style = theme.warning)
```

There are also convenience methods that wrap a string in the style's ANSI codes, for when you need a
pre-styled `String`:

```kotlin
fun primary(text: String): String
fun secondary(text: String): String
fun muted(text: String): String
fun accent(text: String): String
fun success(text: String): String
fun warning(text: String): String
fun error(text: String): String
fun info(text: String): String
```

### Built-in themes

```kotlin
DispatchTheme.Dark      // the default
DispatchTheme.Light
DispatchTheme.Minimal
```

```kotlin
config {
    theme = DispatchTheme.Light
}
```

### A custom theme

`DispatchTheme` is a plain data class — build one with `copy` from a preset, or construct it fully.
`TextStyle`, `TextColors.rgb`, and `TextStyles` come from Mordant:

```kotlin
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

val brand = DispatchTheme.Dark.copy(
    primary = TextStyle(rgb("#7AA2F7"), bold = true),
    accent = TextStyle(rgb("#BB9AF7")),
)

config { theme = brand }
```

## DispatchTextStyle

A set of attribute-only styles you can combine with colors:

```kotlin
object DispatchTextStyle {
    val Default       // TextStyle()
    val Bold          // TextStyle(bold = true)
    val Dim           // TextStyle(dim = true)
    val Italic        // TextStyle(italic = true)
    val Underline     // TextStyle(underline = true)
    val Inverse       // TextStyle(inverse = true)
    val Strikethrough // TextStyle(strikethrough = true)
}
```

`TextStyle`s compose with `+`:

```kotlin
Text("Important", style = theme.error + DispatchTextStyle.Bold)
```

## Composition local

```kotlin
// com.ead.dispatch.runtime.LocalTheme (note: the runtime package, not com.ead.dispatch.theme)
val LocalTheme: ProvidableCompositionLocal<DispatchTheme>   // default DispatchTheme.Dark
```

`LocalTheme` is provided from `config.theme`. Read it anywhere in your tree:

```kotlin
val theme = LocalTheme.current
```

## See also

- [Application & configuration](application.md#dispatchconfig) — where you set the theme.
- [Theme your app](../how-to/theme-your-app.md) — a task-focused guide.
