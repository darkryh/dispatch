# Modifiers reference

Package: `io.github.darkryh.dispatch.modifier`. Module: `dispatch-runtime`.

A `Modifier` configures a layout node: its size, padding, border, position, scrolling, and focus. As
in Jetpack Compose, modifiers form an immutable, ordered chain. Start from the `Modifier` identity
and append with extension functions or `then`:

```kotlin
Modifier.fillMaxWidth().padding(1).border(BorderStyle.Rounded)
```

All dimensions are terminal cells: widths in columns, heights in rows.

> Dispatch has **no** `Modifier.background` and **no** `Modifier.onKeyEvent`. For backgrounds, wrap
> content in a [`Surface`](widgets.md#surface) or [`Panel`](widgets.md#panel). For key handling, use
> [`KeyBindings`](input.md#keybindings) or the [`KeyboardInterceptor`](input.md#keyboardinterceptor).

## The Modifier interface

```kotlin
interface Modifier {
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R
    fun <R> foldOut(initial: R, operation: (Element, R) -> R): R
    fun any(predicate: (Element) -> Boolean): Boolean
    fun <T : Element> allOf(type: Class<T>): List<T>
    fun <T : Element> firstOrNull(type: Class<T>): T?
    infix fun then(other: Modifier): Modifier

    interface Element : Modifier
    companion object : Modifier   // the identity / empty modifier
}
```

The `Modifier` companion object is the empty modifier; every chain begins there. Reified helpers:

```kotlin
inline fun <reified T : Modifier.Element> Modifier.allOf(): List<T>
inline fun <reified T : Modifier.Element> Modifier.firstOrNull(): T?
```

## Size

```kotlin
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier
fun Modifier.width(chars: Int): Modifier
fun Modifier.height(lines: Int): Modifier
fun Modifier.size(width: Int, height: Int): Modifier
fun Modifier.widthIn(min: Int = 0, max: Int = Int.MAX_VALUE): Modifier
fun Modifier.heightIn(min: Int = 0, max: Int = Int.MAX_VALUE): Modifier
fun Modifier.sizeIn(minWidth: Int = 0, maxWidth: Int = Int.MAX_VALUE, minHeight: Int = 0, maxHeight: Int = Int.MAX_VALUE): Modifier
fun Modifier.weight(weight: Float): Modifier
fun Modifier.defaultSize(width: Int? = null, height: Int? = null): Modifier
fun Modifier.applyToConstraints(constraints: Constraints): Constraints
```

- `fillMaxWidth` / `fillMaxHeight` / `fillMaxSize` — take a fraction in `0f..1f` of the available
  space (default the whole axis).
- `width(chars)` / `height(lines)` — fixed size in cells; values must be `>= 0`.
- `weight(weight)` — inside a `Row` or `Column`, claim a proportional share of the leftover space.
  Must be `> 0`.
- `defaultSize` — apply a size only when the incoming constraints are unbounded.
- `applyToConstraints` — fold every size modifier in the chain over the given constraints; call this
  from a [custom layout](layout.md#custom-layouts).

```kotlin
Row {
    Sidebar(Modifier.width(20))
    Content(Modifier.weight(1f))   // takes the rest
}
```

## Padding

```kotlin
fun Modifier.padding(all: Int): Modifier
fun Modifier.padding(horizontal: Int = 0, vertical: Int = 0): Modifier
fun Modifier.padding(start: Int = 0, end: Int = 0, top: Int = 0, bottom: Int = 0): Modifier
fun Modifier.horizontalPadding(chars: Int): Modifier
fun Modifier.verticalPadding(lines: Int): Modifier
fun Modifier.totalPadding(): PaddingValues
fun Modifier.getPadding(): PaddingValues?
```

Insets a node's content. Values must be `>= 0`.

```kotlin
data class PaddingValues(val start: Int, val end: Int, val top: Int, val bottom: Int) {
    val horizontal: Int      // start + end
    val vertical: Int        // top + bottom
    val isEmpty: Boolean
    companion object { val Zero = PaddingValues(0, 0, 0, 0) }
}
```

## Border

```kotlin
fun Modifier.border(style: BorderStyle = BorderStyle.Rounded, textStyle: TextStyle? = null): Modifier
fun Modifier.border(style: BorderStyle = BorderStyle.Rounded, title: String, textStyle: TextStyle? = null): Modifier
fun Modifier.getBorder(): BorderModifierElement?
```

Draws a box-drawing border around a node, optionally titled and styled.

**`BorderStyle`** (enum): `None`, `Ascii`, `Rounded`, `Square`, `Heavy`, `Double`, `Dashed`.

```kotlin
Text("Boxed", modifier = Modifier.border(BorderStyle.Square, title = "Note"))
```

The characters for each style are exposed via `object BorderCharacters` (`BorderChars` for each
style plus `forStyle(style)`), in case you draw borders manually.

## Offset

```kotlin
data class OffsetModifier(val x: Int, val y: Int) : Modifier.Element
fun Modifier.offset(x: Int = 0, y: Int = 0): Modifier
fun Modifier.getOffset(): OffsetModifier?
```

Shifts a node by a fixed number of cells **after** placement. It does not affect measurement or
sibling positions.

## Scroll

```kotlin
fun Modifier.verticalScroll(enabled: Boolean = true): Modifier
fun Modifier.horizontalScroll(enabled: Boolean = true): Modifier
fun Modifier.scrollable(horizontal: Boolean = false, vertical: Boolean = true): Modifier
fun Modifier.isScrollable(): Boolean
fun Modifier.getScrollConfig(): ScrollConfig
```

Marks a node as scrollable.

```kotlin
data class ScrollConfig(val horizontal: Boolean, val vertical: Boolean) {
    val isScrollable: Boolean
    companion object { val None; val Vertical; val Horizontal; val Both }
}
```

## Focus

```kotlin
data class FocusTargetModifier(val token: Any?) : Modifier.Element
fun Modifier.focusable(key: Any? = null): Modifier
```

Marks a node as a focus target so it can receive keyboard input. The optional `key` is the stable
focus identity; if null, the owning layout node is used. The [`clickable`](widgets.md#modifierclickable)
widget modifier builds on this. Focus is tracked by the `FocusRegistry`, available via
`LocalFocusRegistry`.

## Semantics

```kotlin
data class SemanticsTagModifier(val tag: String) : Modifier.Element
fun Modifier.semantics(tag: String): Modifier
```

Attaches a semantic tag to a node, surfaced through `LayoutNode.semanticsTags` — useful for testing
and tooling.

## See also

- [Layout](layout.md) — the containers these modifiers configure.
- [Widgets](widgets.md) — `Surface` and `Panel` for backgrounds and bordered regions.
