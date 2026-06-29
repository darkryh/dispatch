# The render model

This page explains how Dispatch paints a terminal without flicker, and why the rules it follows are
the way they are. It is background reading — to build something, see the
[tutorials](../tutorials/index.md) and [how-to guides](../how-to/index.md).

## The problem: a terminal is not a canvas

A GUI toolkit owns a rectangle of pixels and can repaint any of them, any time. A terminal is
different. It is a stream of characters and escape codes layered over two regions you do not fully
control:

- **Scrollback** — the lines that have scrolled up out of the active view. Users select and copy
  from it; their own shell history lives there too. Rewriting scrollback is both expensive and rude.
- **The active view** — the rows currently on screen, which you can move the cursor around and
  overwrite.

If you repaint the whole screen on every frame — the naive approach — you get flicker, you fight the
user's scrollback, and you waste bytes. Dispatch is built to avoid all three.

## The approach: print-once, append-only, repaint-in-place

Dispatch splits every frame into two parts and treats them differently.

**Committed scrollback is append-only.** Once a line has scrolled into history, Dispatch never
rewrites it. New content is *appended* below, the way normal terminal output works. This is what lets
your app's output coexist with the user's terminal — they can scroll up and see a stable, copyable
record.

**The bottom "active area" updates in place.** A bounded region at the bottom of the screen — the
part that changes frame to frame, like an input line, a progress bar, or a live status — is rewritten
in place by moving the cursor, without disturbing the scrollback above it. Its height is capped by
`config.activeAreaHeight` (default 12 rows).

**Only what changed is repainted.** A render-decision engine compares each new frame to the last and
chooses the smallest update: append the newly grown tail, rewrite just the active area, or — when a
screen genuinely replaces another — do a deliberate full repaint.

**Every frame is flushed atomically.** A frame is composed into a single buffer and written to the
terminal in one flush, so a frame can never be torn halfway through. Frames are coalesced to a target
rate (`config.targetFps`, default 60; set `0` to render immediately).

## The invariants

These rules hold by design. Code that touches rendering must preserve them.

- **Scrollback is append-only** — committed lines are never repainted.
- **The active area updates in place** — without disturbing scrollback.
- **A terminal resize forces a full rewrite** — cursor positioning is invalidated, so the frame is
  repainted from scratch.
- **Screen-to-screen navigation clears and repaints fresh** — moving between screens is a deliberate
  full repaint, not an incremental diff.
- **Every frame is buffered and flushed once** — to prevent flicker and tearing.

## What this means for you

Most of the time, nothing — you write composables and Dispatch handles the rest. But the model
explains behaviors you will notice:

- **Output you print scrolls away and stays put.** Content that leaves the active area becomes part
  of the immutable scrollback. That is intentional: it is your app's permanent log.
- **The active area is bounded.** Live, in-place UI lives in the bottom region. If you need a large
  interactive screen, that is what navigation and full-screen layouts ([`TerminalScreen`](../reference/layout.md#terminalscreen))
  are for — they take over the screen with a full repaint.
- **Navigation feels like a page turn, not a diff.** Because screen changes repaint fresh, moving
  between screens is clean and predictable.
- **`targetFps` trades latency for work.** A higher target coalesces fewer frames; `0` paints every
  change immediately. The default of 60 is a good balance.

## Why not just clear and redraw everything?

It would be simpler to write, and it is what many terminal apps do. Dispatch does not, for three
reasons: full redraws flicker (the screen blanks between clear and paint); they destroy the user's
scrollback and selection; and they send far more bytes than a targeted update. The print-once /
append-only model is more work internally, but it is what makes a Dispatch app feel like a native
part of the terminal rather than a hijacked screen.

## See also

- [Architecture and modules](architecture.md) — where the renderer sits among the modules.
- [Application & configuration](../reference/application.md#dispatchconfig) — `activeAreaHeight`,
  `targetFps`, and related options.
