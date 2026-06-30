# Reference

The reference describes Dispatch's public API exactly: every widget, modifier, configuration option,
and class, with its signature, parameters, defaults, and behavior. It mirrors the library's module
structure. Look things up here; to learn or to accomplish a task, use the
[tutorials](../tutorials/index.md) and [how-to guides](../how-to/index.md).

All artifacts share the group `io.github.darkryh` and a single version. Packages are listed
per page.

## Pages

| Page | Module(s) | Covers |
|---|---|---|
| [Application & configuration](application.md) | `dispatch-core`, `dispatch-runtime` | `DispatchApplication`, the `config { }` DSL, CLI flags/arguments, exit keys, `DispatchScope`. |
| [Widgets](widgets.md) | `dispatch-widgets` | Every composable widget: text, buttons, inputs, lists, tables, panels, progress, and more. |
| [Layout](layout.md) | `dispatch-layout`, `dispatch-runtime` | `Column`, `Row`, `Box`, flow layouts, alignment, arrangement, and custom-layout authoring. |
| [Modifiers](modifiers.md) | `dispatch-runtime` | The `Modifier` chain: size, padding, border, offset, scroll, focus, semantics. |
| [Theme](theme.md) | `dispatch-runtime` | `DispatchTheme`, text styles, and theme composition locals. |
| [Keyboard input](input.md) | `dispatch-runtime` | `Key`, `KeyEvent`, `KeyStroke`, `KeyBindings`, and `KeyboardInterceptor`. |
| [Navigation](navigation.md) | `dispatch-navigation` | The typed back stack, `NavDisplay`, routes, and entry decorators. |
| [View models & MVI](viewmodel.md) | `dispatch-viewmodel` | `ViewModel` and MVI base classes, `StateFlow` helpers, and obtaining a view model. |
| [Lifecycle](lifecycle.md) | `dispatch-lifecycle` | `LifecycleRegistry`, `LifecycleOwner`, and `LifecycleState`. |
| [Koin DI](koin.md) | `dispatch-koin` | Installing Koin, the module DSL, and the Koin-backed view-model factory. |
| [Self-update](update.md) | `dispatch-update*` | The update advisor, providers, and the `rememberUpdateAdvice` hook. |
| [Workspace watching](workspace.md) | `dispatch-workspace` | The filesystem watcher, its events, and its composition helpers. |

## Conventions

- **Units are terminal cells.** Widths are columns (characters); heights are rows (lines). A
  `Modifier.height(1)` is one row tall.
- **`Modifier` is the identity modifier.** Chains start from the `Modifier` companion object and
  build with `.then(...)` or extension functions: `Modifier.fillMaxWidth().padding(1)`.
- **Mordant types** appear in some signatures. `TextStyle`, `TextColors`, `Terminal`,
  `KeyboardEvent`, and `MouseEvent` come from
  [Mordant](https://github.com/ajalt/mordant) (`com.github.ajalt.mordant.*`), the terminal library
  Dispatch renders through.
