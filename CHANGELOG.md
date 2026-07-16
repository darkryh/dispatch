# Changelog

All notable changes to Dispatch are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0-beta03] - 2026-07-16

### Fixed

- `DispatchTheme`'s string convenience methods (`primary(text)`, `secondary(text)`, `muted(text)`,
  `accent(text)`, `success(text)`, `warning(text)`, `error(text)`, `info(text)`) were accidentally
  self-recursive and crashed with a `StackOverflowError` on every call. They now apply the
  same-named `TextStyle` and return the styled string, as documented.
- Koin startup validation no longer crashes apps whose `@Serializable` routes carry required
  (no-default) fields. Validation decodes a synthetic `"{}"` payload, which throws
  `MissingFieldException` — a subclass of `SerializationException` that the previous exact-class
  filter missed. The filter now walks the exception class hierarchy, so the
  [navigated-app tutorial](docs/tutorials/build-a-navigated-app.md) runs as written.
- `VersionComparator` now understands pre-release qualifiers instead of discarding them:
  `1.0.0-beta02` is newer than `1.0.0-beta01`, `1.0.0` is newer than `1.0.0-rc1`, and numbering is
  natural (`beta10` > `beta2`). Previously, self-update checks silently no-opped between
  prereleases sharing the same core version.

### Changed

- `SystemCommandRunner` is now public, so the Brew/Scoop/APT update providers can be constructed
  without hand-writing a `CommandRunner`.

### Deprecated

- `DispatchConfig.captureSystemOutput` — never read by the engine; setting it has no effect.
- `UpdateConfig.refreshBeforeCheck` — never read; pass `refreshBeforeCheck` to
  `ScoopUpdateProvider`'s constructor instead.
- The `LocalFocused`, `LocalEnabled`, `LocalContentAlpha`, and `LocalPosition` composition locals —
  never provided by the framework, so they always held their defaults. For focus styling, use
  `LocalFocusRegistry.current.isFocused(token)`.

### Documentation

- Dependency snippets across the README and docs now pin the current release (they were stuck on
  `1.0.0-beta01`).
- The README and getting-started guide now explain double-press exit (`requireExitDoublePress`
  defaults to `true` — the first Ctrl+C only arms a 1.5 s window).
- `modifiers.md` now states plainly that `padding`, `border`, and the scroll markers are chain
  metadata that no built-in component reads yet, and points to `Panel`/`Surface`/`ScrollableList`/
  `LazyColumn` for the rendered equivalents.
- `layout.md` clarifies `ConstraintValidator` is an opt-in helper for custom measure policies, not
  an active runtime check; `widgets.md` corrects `Tree`'s `nodeKey` default; `navigation.md`
  documents all four `entry` overloads and `NavEntryDecorator`'s internal constructor properties;
  `RELEASING.md` describes the actual (explicitly dispatched) Pages redeploy.

## [1.0.0-beta02] - 2026-07-02

### Fixed

- `Column`/`Row` with `Arrangement.spacedBy` under-measured by the total inter-child gap: the gap
  total is now reserved out of the space budget before weighted children are sized, and included in
  the container's reported size.
- The release workflow now dispatches the Pages redeploy explicitly (a `GITHUB_TOKEN`-created
  release never fires the `published` event for other workflows) and tolerates a missing signing
  key ID.

## [1.0.0-beta01] - 2026-07-01

Initial public release: the Compose-runtime terminal UI framework — `DispatchApplication` entry
point, layout (`Column`/`Row`/`Box`/flow/`TerminalScreen`), the full widget set, typed navigation,
view models (MVI), lifecycle, saved state, Koin integration, workspace watching, and advisory
self-update providers (Homebrew, Scoop, APT, GitHub Releases).

### Performance

Pre-release optimization pass. All changes are byte-stream and
frame-timing neutral; the print-once / append-only render model and the screen-transition
full-repaint behavior are unchanged.

- Render diagnostics (`RenderPipeline`, `TerminalRenderer`, `RenderDecisionTelemetry`) no longer
  build their per-frame field maps — including a full-scrollback `hashCode()` and a frame
  `toString().toByteArray()` — when `DISPATCH_DIAGNOSTICS_FILE` is unset (the production default).
- `ScrollingContentTracker` appends only the newly grown tail instead of re-copying the entire
  scrollback every frame (O(appended) instead of O(total)).
- `viewportLineCount` computes its result arithmetically instead of allocating a `takeLast` sublist.
- `FocusRegistry.sync` reuses a scratch set and walks the modifier chain via `foldIn`, eliminating
  per-node and per-frame list allocations.
- Layout fast-paths: lazy ANSI-pending buffer in `paintLine`, bulk `CharArray` fill, single-pass
  propagated start-line merge, and removal of redundant placement `Pair` tuples (placeables already
  carry their `x`/`y`).
- `Box`, `Grid`, weighted `Row`/`Column`, and `Text` reduce per-measure boxing and allocations
  (primitive weight arrays, `subList` grid rows, sentinel-strip skipped for markdown, etc.).
- `TerminalRenderer` reuses a single scratch frame buffer instead of allocating one per write.
- Navigation: bounded LRU for the stable-content-key cache (fixes an unbounded retention),
  cached route-payload encoding, and short-circuited per-frame back-stack diffs.
- `Text` memoizes parsed+rendered markdown keyed on terminal/text/width/style, avoiding an AST
  re-parse on every measure of unchanged content.
- Compose locals that never change are now `staticCompositionLocalOf`, with the root
  `DispatchContext` remembered for referential stability.
- Workspace file hashing uses `HexFormat` instead of per-byte `String.format`.
- GitHub update provider constructs its HTTP client lazily, so a throttled-away check never spins
  one up.

### Changed

- The blocking update check now runs on `Dispatchers.IO` instead of stalling the render thread.
- MVI intent delivery uses an unlimited `Channel` (ordered, non-dropping, no per-intent coroutine).
- Session exit is awaited via a completion signal instead of a 50 ms idle poll.
- `bell()` now emits under the render lock, so the BEL byte can no longer interleave into a frame.

### Fixed

- Maven coordinates corrected from `com.github.ajalt.mordant.dispatch` to `io.github.darkryh.dispatch`
  (the previous group is the Mordant author's namespace and could not be published).

### Added

- **Idle hibernation** — after a configurable span with no keyboard or mouse input, the app drops to
  a low-power state: the paint cadence falls to `idleFps` (default 1) and rebuildable caches (markdown
  render cache, lazy-list heights, frame diff) are released, then the next input wakes it instantly.
  Enabled by default and non-destructive (scrollback and app state are kept); it only affects
  rendering and never pauses coroutines, view models, or background work. Configure it with the
  `hibernation { }` block in `config { }`; observe it through `LocalHibernation` or the
  `hibernate_enter` / `hibernate_exit` diagnostics events. See the
  [reference](docs/reference/application.md#idle-hibernation) and
  [how-to](docs/how-to/tune-idle-hibernation.md).
- `LICENSE` (Apache 2.0), `README.md`, and this changelog.

[Unreleased]: https://github.com/darkryh/dispatch/compare/v1.0.0-beta03...HEAD
[1.0.0-beta03]: https://github.com/darkryh/dispatch/compare/v1.0.0-beta02...v1.0.0-beta03
[1.0.0-beta02]: https://github.com/darkryh/dispatch/compare/v1.0.0-beta01...v1.0.0-beta02
[1.0.0-beta01]: https://github.com/darkryh/dispatch/releases/tag/v1.0.0-beta01
