# Theme your app

This guide shows how to set a theme, use its styles in widgets, and define your own palette. It
assumes a working app.

A theme is a set of named [Mordant](https://github.com/ajalt/mordant) `TextStyle`s — `primary`,
`muted`, `success`, `error`, and so on. You set one in `config`, and read it in composition.

## Choose a built-in theme

Set `config.theme` to one of the presets:

```kotlin
config {
    theme = DispatchTheme.Dark    // the default
    // theme = DispatchTheme.Light
    // theme = DispatchTheme.Minimal
}
```

## Use theme styles in widgets

Read the current theme with `LocalTheme.current` and pass a style to a widget's `style` argument.

```kotlin
import com.ead.dispatch.runtime.LocalTheme

@Composable
fun Status(ok: Boolean) {
    val theme = LocalTheme.current
    Text(if (ok) "Ready" else "Failed", style = if (ok) theme.success else theme.error)
}
```

Available styles: `primary`, `secondary`, `muted`, `accent`, `success`, `warning`, `error`, `info`,
`code`, `link`, `border`, `cursor`, `selection`.

## Combine with attributes

Compose a color style with a text attribute using `+`:

```kotlin
import com.ead.dispatch.theme.DispatchTextStyle

Text("Important", style = theme.error + DispatchTextStyle.Bold)
```

`DispatchTextStyle` provides `Bold`, `Dim`, `Italic`, `Underline`, `Inverse`, and `Strikethrough`.

## Define a custom palette

`DispatchTheme` is a data class. Start from a preset and `copy` the styles you want to change, or
build one from scratch. Colors come from Mordant's `rgb`:

```kotlin
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

val brand = DispatchTheme.Dark.copy(
    // `rgb(...)` already returns a TextStyle; combine styles with `+` to add attributes like bold.
    primary = rgb("#7AA2F7") + TextStyle(bold = true),
    accent = rgb("#BB9AF7"),
    success = rgb("#9ECE6A"),
    error = rgb("#F7768E"),
)

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config { theme = brand }
        content { App() }
    }
```

Every widget that reads `LocalTheme` now uses your palette.

## Related

- [Theme reference](../reference/theme.md) — every style field and the built-in presets.
- [Widgets reference](../reference/widgets.md) — widgets that take a `style` argument.
