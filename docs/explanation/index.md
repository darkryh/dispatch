# Explanation

These pages explain how Dispatch works and why it is built the way it is. They are for understanding,
not for step-by-step tasks — read them away from the keyboard.

- [The render model](render-model.md) — How Dispatch paints a terminal without flicker: the
  print-once / append-only scrollback, the in-place active area, and full-repaint navigation.
- [Architecture and modules](architecture.md) — How the library is split into modules, the
  dependency boundaries between them, and what each one is responsible for.
- [Why the Compose runtime](why-compose-runtime.md) — Why Dispatch reuses Jetpack Compose's runtime
  instead of inventing its own, and what that buys you.

For exact APIs, see the [reference](../reference/index.md). To build something, see the
[tutorials](../tutorials/index.md) and [how-to guides](../how-to/index.md).
