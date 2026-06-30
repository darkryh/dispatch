# Layout reference

Package: `io.github.darkryh.dispatch.layout`. Modules: `dispatch-layout`, `dispatch-runtime`.

Layout in Dispatch mirrors Jetpack Compose: containers measure and place children, alignment and
arrangement control positioning, and `weight` distributes leftover space. Everything is measured in
terminal cells — widths in columns, heights in rows.

## Contents

- [Column](#column) · [Row](#row) · [Box](#box) · [Spacer](#spacer)
- [Flow layouts](#flow-layouts) — `FlowRow`, `FlowColumn`
- [Banner layouts](#banner-layouts) — `BannerLayout`, `HeaderLayout`, `FooterLayout`
- [TerminalScreen](#terminalscreen)
- [Alignment](#alignment) · [Arrangement](#arrangement) · [Per-child alignment](#per-child-alignment)
- [Custom layouts](#custom-layouts) — `Layout`, `MeasurePolicy`, `Constraints`

---

## Column

```kotlin
@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
)
```

Places children top to bottom. When height is bounded, children can claim leftover rows with
[`Modifier.weight`](modifiers.md#size).

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(1)) {
    Text("Title", style = theme.primary)
    Text("Body")
}
```

`ColumnScope` is the content receiver; it adds `align` (see [per-child alignment](#per-child-alignment))
and exposes `horizontalAlignment`.

## Row

```kotlin
@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
)
```

Places children side by side. When width is bounded, children can claim leftover columns with
`Modifier.weight`.

```kotlin
Row {
    Text("Name:")
    Spacer(Modifier.width(1))
    Text(name, modifier = Modifier.weight(1f))
}
```

## Box

```kotlin
@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment.Alignment2D = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
)
```

Stacks children on top of one another; later children draw over earlier ones. `contentAlignment`
positions children within the box.

```kotlin
Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Text("centered")
}
```

## Spacer

```kotlin
@Composable
fun Spacer(modifier: Modifier = Modifier)
```

An empty node, sized by its modifier. Use it for gaps: `Spacer(Modifier.width(2))` or
`Spacer(Modifier.height(1))`.

## Flow layouts

```kotlin
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    content: @Composable FlowRowScope.() -> Unit,
)

@Composable
fun FlowColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    maxItemsInEachColumn: Int = Int.MAX_VALUE,
    content: @Composable FlowColumnScope.() -> Unit,
)
```

`FlowRow` lays children left to right and wraps to a new line on overflow or after
`maxItemsInEachRow` items. `FlowColumn` is the transpose. Useful for chip clouds and tag lists.

## Banner layouts

```kotlin
@Composable
fun BannerLayout(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
)

@Composable
fun HeaderLayout(modifier: Modifier = Modifier, header: @Composable () -> Unit, body: @Composable () -> Unit)

@Composable
fun FooterLayout(modifier: Modifier = Modifier, footer: @Composable () -> Unit, body: @Composable () -> Unit)
```

A three-section layout: the header and footer take their intrinsic height; the body fills the rows
in between. `HeaderLayout` and `FooterLayout` are convenience wrappers with one fixed section.

## TerminalScreen

```kotlin
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

The root frame for one terminal viewport: the header and footer take intrinsic height, and the
`content` column fills the remaining rows. This is the layout primitive; for a styled screen frame
with key hints and Escape-to-back, see the [`Scaffold` widget](widgets.md#scaffold).

```kotlin
TerminalScreen(
    header = { Text("My App", style = theme.primary) },
    footer = { KeyHintBar(listOf(KeyHint("Ctrl+C", "quit"))) },
) {
    Text("Body content")
}
```

## Alignment

`object Alignment` provides positioning singletons and the 2D combinations used by `Box`.

**Horizontal**: `Alignment.Start`, `Alignment.CenterHorizontally`, `Alignment.End`.
**Vertical**: `Alignment.Top`, `Alignment.CenterVertically`, `Alignment.Bottom`.

**2D (for `Box.contentAlignment`)**: `TopStart`, `TopCenter`, `TopEnd`, `CenterStart`, `Center`,
`CenterEnd`, `BottomStart`, `BottomCenter`, `BottomEnd`.

```kotlin
data class Alignment2D(val horizontal: Horizontal, val vertical: Vertical)
```

## Arrangement

`object Arrangement` distributes children along the main axis.

**Vertical (Column)**: `Arrangement.Top`, `Arrangement.Bottom`, plus the shared values below.
**Horizontal (Row)**: `Arrangement.Start`, `Arrangement.End`, plus the shared values below.
**Shared (both axes)**: `Arrangement.Center`, `Arrangement.SpaceBetween`, `Arrangement.SpaceAround`,
`Arrangement.SpaceEvenly`.

**Fixed spacing**:

```kotlin
fun spacedBy(spacing: Int): Arrangement.HorizontalOrVertical
```

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(2)) { /* 2 cells between each child */ }
Column(verticalArrangement = Arrangement.SpaceBetween) { /* push first/last to edges */ }
```

## Per-child alignment

Inside a scope, override a single child's cross-axis alignment with `align`:

```kotlin
fun BoxScope.align(alignment: Alignment.Alignment2D): Modifier
fun ColumnScope.align(alignment: Alignment.Horizontal): Modifier
fun RowScope.align(alignment: Alignment.Vertical): Modifier
```

```kotlin
Column {
    Text("left")
    Text("right", modifier = Modifier.then(align(Alignment.End)))
}
```

## Custom layouts

To build a layout that the built-in containers don't cover, use `Layout` with a `MeasurePolicy`.
These APIs live in `dispatch-runtime` (packages `io.github.darkryh.dispatch.layout`,
`io.github.darkryh.dispatch.constraints`).

```kotlin
@Composable
fun Layout(
    modifier: Modifier = Modifier,
    measurePolicy: MeasurePolicy,
    content: @Composable () -> Unit,
)

interface MeasurePolicy {
    fun measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult
}
```

You measure each child against `Constraints`, get a `Placeable`, and place it:

```kotlin
interface Measurable {
    val modifier: Modifier
    fun measure(constraints: Constraints): Placeable
}

interface Placeable {
    val width: Int
    val height: Int
    val lines: List<String>
    var x: Int
    var y: Int
}

data class MeasureResult(
    val width: Int,
    val height: Int,
    val activeStartLine: Int? = null,
    val scrollingStartLine: Int? = null,
    val placementBlock: PlacementScope.() -> Unit = {},
)

interface PlacementScope {
    fun Placeable.placeAt(x: Int, y: Int)
}
```

```kotlin
val policy = object : MeasurePolicy {
    override fun measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = placeables.sumOf { it.height }
        return MeasureResult(width, height) {
            var y = 0
            placeables.forEach { it.placeAt(0, y); y += it.height }
        }
    }
}
Layout(measurePolicy = policy) { Text("a"); Text("b") }
```

To honor size modifiers (`width`, `height`, `weight`, …) in a custom layout, fold them over your
constraints with [`Modifier.applyToConstraints`](modifiers.md#size).

### Constraints

```kotlin
data class Constraints(
    val minWidth: Int = 0,
    val maxWidth: Int = Int.MAX_VALUE,
    val minHeight: Int = 0,
    val maxHeight: Int = Int.MAX_VALUE,
)
```

Bounds passed down during measurement. Helpers: `hasBoundedWidth`, `hasBoundedHeight`,
`hasFixedWidth`, `hasFixedHeight`, `isFixed`, `isZero`; `constrainWidth(w)`, `constrainHeight(h)`,
`constrain(w, h)`, `offset(horizontal, vertical)`. Companion: `Constraints.Unbounded`,
`Constraints.Zero`, `Constraints.fixed(w, h)`, `fixedWidth(w)`, `fixedHeight(h)`, `maxSize(w, h)`,
`minSize(w, h)`.

A `ConstraintValidator` (`object`, package `io.github.darkryh.dispatch.constraints`) checks measured sizes
against constraints; its `mode` is `STRICT`, `LENIENT`, or `SILENT`.

## See also

- [Modifiers](modifiers.md) — size, padding, border, and alignment modifiers.
- [Widgets](widgets.md) — content to place inside layouts.
- [Lay out a screen](../how-to/lay-out-a-screen.md) — a task-focused walkthrough.
