# Changelog

All notable changes to Dispatch are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Performance

Pre-release optimization pass (see `OPTIMIZATION_PLAN.md`). All changes are byte-stream and
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

- `LICENSE` (Apache 2.0), `README.md`, and this changelog.

[Unreleased]: https://github.com/darkryh/dispatch
