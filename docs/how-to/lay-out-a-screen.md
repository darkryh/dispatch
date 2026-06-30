# Lay out a screen

This guide covers arranging widgets with Dispatch's layout containers: stacking, rows, weighting,
spacing, alignment, and a full-screen frame. It assumes you can already build and run an app — if
not, start with [Getting started](../getting-started.md).

All sizes are terminal cells: widths in columns, heights in rows.

## Stack widgets vertically

`Column` places children top to bottom.

```kotlin
Column {
    Text("Title", style = theme.primary)
    Text("Subtitle", style = theme.muted)
    Text("Body")
}
```

## Place widgets side by side

`Row` places children left to right.

```kotlin
Row {
    Text("Name:")
    Spacer(Modifier.width(1))
    Text(name)
}
```

## Add spacing

Use `Spacer` for one-off gaps, or an arrangement for even spacing between every child.

```kotlin
// Fixed gaps between specific items
Column {
    Text("A")
    Spacer(Modifier.height(1))
    Text("B")
}

// Even spacing between all children
Row(horizontalArrangement = Arrangement.spacedBy(2)) {
    Chip("one"); Chip("two"); Chip("three")
}
```

Other arrangements: `Arrangement.SpaceBetween`, `SpaceAround`, `SpaceEvenly`, `Center`, and the
axis-specific `Top`/`Bottom`/`Start`/`End`.

## Divide space proportionally

Give a child `Modifier.weight(...)` to claim a share of the leftover space on the main axis. This
works when the container's size is bounded (for example inside a `fillMaxWidth` row).

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.weight(0.62f)) { preview() }
    Spacer(Modifier.fillMaxWidth(0.02f))
    Box(modifier = Modifier.weight(0.38f)) { controls() }
}
```

Fixed-plus-flexible is a common pattern — a fixed sidebar and a flexible body:

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.width(20)) { sidebar() }
    Box(modifier = Modifier.weight(1f)) { content() }
}
```

## Stack and align with Box

`Box` overlays children; `contentAlignment` positions them.

```kotlin
Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Text("centered")
}
```

To align a single child differently from the box, use the scoped `align` modifier:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Text("top-left")
    Text("bottom-right", modifier = Modifier.then(align(Alignment.BottomEnd)))
}
```

## Wrap items that overflow

`FlowRow` wraps to a new line when the row runs out of width — useful for tags or chips.

```kotlin
FlowRow(horizontalArrangement = Arrangement.spacedBy(1)) {
    tags.forEach { Chip(it) }
}
```

## Frame a full screen

`TerminalScreen` gives you a header and footer that take their natural height and a body that fills
the rest of the viewport.

```kotlin
TerminalScreen(
    header = { Text("My App", style = theme.primary) },
    footer = { KeyHintBar(listOf(KeyHint("Ctrl+C", "quit"))) },
) {
    Text("Body fills the remaining rows")
}
```

For a styled frame with a title, key hints, and Escape-to-go-back built in, use the
[`Scaffold` widget](../reference/widgets.md#scaffold) instead.

## Imports

```kotlin
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Box
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Alignment
import io.github.darkryh.dispatch.layout.TerminalScreen
import io.github.darkryh.dispatch.layout.align
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.fillMaxSize
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.weight
```

## Related

- [Layout reference](../reference/layout.md) — every container, alignment, and arrangement.
- [Modifiers reference](../reference/modifiers.md) — size, padding, and border modifiers.
- [Widgets reference](../reference/widgets.md) — what to put inside your layout.
