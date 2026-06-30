# Tune idle hibernation

This guide shows how to adjust idle hibernation: change when it kicks in, keep a screen awake when
you want it always live, turn it off, and watch it work. It assumes you can build and run an app.

Hibernation is on by default. After a stretch with no keyboard or mouse input, the app lowers its
paint cadence to `idleFps` and releases rebuildable caches; the next input wakes it instantly. You
configure it in the `hibernation { }` block inside `config { }`. For the full property list, see the
[Idle hibernation reference](../reference/application.md#idle-hibernation).

## Change when it kicks in

Set the inactivity timeout and the idle frame rate:

```kotlin
import io.github.darkryh.dispatch.runtime.DispatchApplication
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            hibernation {
                idleTimeout = 2.minutes   // hibernate after 2 min with no input (default: 5)
                idleFps = 1               // paint at most once per second while idle (default: 1)
            }
        }
        content { App() }
    }
```

`idleTimeout` is measured from the last keypress or mouse event — not from the last screen change. A
screen that keeps updating from background work still hibernates if you do not touch it; only the
*visible* refresh slows to `idleFps` until you press a key.

## Keep a live screen awake

For a screen you watch without typing — a dashboard, a tailing log — 1 FPS while idle may be slower
than you want. You have three options, from least to most surgical.

**Raise the idle frame rate** so "hibernating" still refreshes smoothly (you keep the cache release,
give up most of the CPU saving):

```kotlin
hibernation {
    idleFps = 15
}
```

**Wake on fresh data** from a specific screen, so it never stays throttled while it has updates to
show. Read the handle from composition and call `wakeNow()` when new data arrives:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import io.github.darkryh.dispatch.runtime.LocalHibernation

@Composable
fun LiveLogScreen(viewModel: LogViewModel) {
    val hibernation = LocalHibernation.current
    val entries by viewModel.entries.collectAsState()

    LaunchedEffect(entries.size) {
        // A new log line arrived — make sure the screen is painting at full rate to show it.
        hibernation?.wakeNow()
    }

    LogList(entries)
}
```

**Turn hibernation off for the whole app** if it is a dedicated monitor that should always run hot —
see the next section.

## Turn it off

```kotlin
hibernation {
    enabled = false
}
```

You can also keep hibernation but stop it from touching memory — useful if you only want the FPS
throttle and never the cache rebuild on wake:

```kotlin
hibernation {
    releaseCaches = false   // throttle FPS only; keep caches
    requestGc = false       // and skip the GC hint
}
```

## Watch it work

Hibernation writes diagnostics when `DISPATCH_DIAGNOSTICS_FILE` points at a file. Run your app with
it set, stay idle past the timeout, then press a key:

```bash
./gradlew installDist
DISPATCH_DIAGNOSTICS_FILE=/tmp/dispatch-diag.jsonl ./build/install/<project>/bin/<project>
```

(A Dispatch TUI needs a real terminal; `./gradlew run` would capture stdin/stdout and never wake on a
keypress, so launch the installed binary. `<project>` is your Gradle project name.)

```bash
grep hibernate /tmp/dispatch-diag.jsonl
```

```json
{"event":"hibernate_enter","idleFps":1,"heapBeforeBytes":27894144,"heapAfterBytes":7245824,"heapFreedBytes":20648320}
{"event":"hibernate_exit","hibernatedDurationMs":8123,"wakeLatencyNanos":463900,"activeFps":60}
```

`heapFreedBytes` shows how much the cache release reclaimed; `wakeLatencyNanos` is the time from your
keypress to the wake.

To show state on screen instead, read `LocalHibernation` in a small overlay composable:

```kotlin
import io.github.darkryh.dispatch.runtime.LocalHibernation
import io.github.darkryh.dispatch.widget.Text

@Composable
fun HibernationStatus() {
    val hibernation = LocalHibernation.current ?: return
    val label = if (hibernation.isHibernating) "HIBERNATING" else "AWAKE"
    Text("$label · idle in ${hibernation.idleCountdownMillis() / 1000}s")
}
```

`isHibernating` recomposes the overlay when it flips. `idleCountdownMillis()` is time-based, so drive
a periodic recomposition (a `LaunchedEffect` that bumps a state every ~500 ms) if you want the
countdown to tick live.

## See also

- [Idle hibernation reference](../reference/application.md#idle-hibernation) — every option, the
  `HibernationHandle` API, and the diagnostics fields.
- [The render model](../explanation/render-model.md#idle-hibernation) — why hibernation only touches
  rendering and leaves your background work alone.
