# How-to guides

How-to guides are recipes for specific tasks. Each assumes you already know the basics (if not,
start with the [Getting started tutorial](../getting-started.md)) and gets you to one concrete goal.

## Building UI

- [Handle keyboard input](handle-keyboard-input.md) — React to keys, register interceptors, and
  declare key bindings.
- [Lay out a screen](lay-out-a-screen.md) — Combine `Column`, `Row`, `Box`, weighting, and spacing.
- [Theme your app](theme-your-app.md) — Choose a built-in theme or define your own colors and styles.

## Architecture

- [Add navigation](add-navigation.md) — Define typed routes and move between screens.
- [Use view models and MVI](use-viewmodels.md) — Drive a screen from observable state, intents, and
  one-time side effects.
- [Wire dependency injection with Koin](use-koin-di.md) — Declare modules and resolve view models
  through Koin.

## Optional modules

- [Check for updates](check-for-updates.md) — Notify users when a newer version is available via
  Homebrew, Scoop, APT, or GitHub releases.
- [Watch the workspace](watch-the-workspace.md) — React to filesystem changes for live-reload-style
  workflows.

For exhaustive options on any of these, see the [reference](../reference/index.md).
