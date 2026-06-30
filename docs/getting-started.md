# Getting started

In this tutorial you build and run your first Dispatch app: a small terminal screen with a header, a
live counter you change from the keyboard, and a quit key. By the end you will know how a Dispatch
app is wired — the `config` block, the `content` composable, state, and key handling — and you will
have something running in your terminal.

It takes about ten minutes. You write real, complete code at each step.

> **In a hurry?** The [Dispatch Initializr](https://darkryh.github.io/dispatch/) generates a
> complete, ready-to-run project (a single screen with Koin DI and an MVI view model) that you can
> download and run immediately. This tutorial instead builds an app from an empty project, so you
> learn how each piece is wired. Once you finish here, the generated project's structure will look
> familiar.

## Before you begin

You need:

- **JDK 21 or newer.** Check with `java -version`.
- **A Gradle project.** Any Kotlin/JVM Gradle project works. If you are starting fresh, create an
  empty directory and run `gradle init --type kotlin-application` (or copy the files below).

You do not need prior Dispatch experience. Familiarity with Kotlin helps.

## Step 1: Configure the build

Dispatch composables are compiled by the Compose compiler plugin, so your `build.gradle.kts` needs
the Kotlin JVM plugin, the Compose compiler plugin, and two Dispatch dependencies.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.darkryh:dispatch-core:1.0.0-beta01")
    implementation("io.github.darkryh:dispatch-widgets:1.0.0-beta01")
}

application {
    mainClass.set("MainKt")
}

kotlin {
    jvmToolchain(21)
}
```

`dispatch-core` provides the app entry point; `dispatch-widgets` brings the widgets, layout,
modifiers, theme, and view-model APIs along with it.

## Step 2: Write a minimal app

Create `src/main/kotlin/Main.kt` with the smallest app that runs:

```kotlin
import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.runtime.DispatchApplication
import io.github.darkryh.dispatch.runtime.ExitKeyBinding
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Text

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "counter"
            version = "1.0.0"
            theme = DispatchTheme.Dark
            exitKeys(ExitKeyBinding.ctrl("C"))
        }
        content { App() }
    }

@Composable
fun App() {
    val theme = LocalTheme.current
    Text("Hello, Dispatch!", style = theme.primary)
}
```

Two blocks do the work. `config { }` sets the app's name, version, theme, and the key that quits.
`content { }` names the root composable. `App` reads the current theme from `LocalTheme` and renders
one styled line.

Run it. A Dispatch app needs a real terminal (TTY), and `./gradlew run` doesn't provide one — Gradle
captures stdin and stdout, so keypresses never reach the app and the display garbles. Build a native
launcher with `installDist` and run that instead:

```bash
./gradlew installDist
./build/install/<project>/bin/<project>
```

`<project>` is your Gradle project name — the directory name unless you set `rootProject.name`.

You should see:

```
Hello, Dispatch!
```

The app keeps running. Press **Ctrl+C** to quit. You now have a working Dispatch app.

## Step 3: Lay out a screen

One line is not much. Add a header and structure with `Column`, which stacks children top to bottom.
Replace `App` with:

```kotlin
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.height

@Composable
fun App() {
    val theme = LocalTheme.current
    Column {
        Text("Counter", style = theme.primary)
        Text("A first Dispatch app", style = theme.muted)
        Spacer(Modifier.height(1))
        Text("Count: 0")
    }
}
```

Add the `Modifier` import too:

```kotlin
import io.github.darkryh.dispatch.modifier.Modifier
```

Rebuild and relaunch the same way — `./gradlew installDist` then `./build/install/<project>/bin/<project>`. You should see:

```
Counter
A first Dispatch app

Count: 0
```

The `Spacer(Modifier.height(1))` inserts one blank row. Sizes in Dispatch are terminal cells —
`height(1)` is one row, `width(2)` is two columns.

## Step 4: Add state

`Count: 0` is hardcoded. Make it real state with `remember` and `mutableStateOf`, exactly as in
Compose. When the state changes, Dispatch recomposes and repaints.

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun App() {
    val theme = LocalTheme.current
    var count by remember { mutableStateOf(0) }

    Column {
        Text("Counter", style = theme.primary)
        Text("A first Dispatch app", style = theme.muted)
        Spacer(Modifier.height(1))
        Text("Count: $count", style = theme.accent)
    }
}
```

Nothing changes it yet, so the count stays at `0` — but the value now flows from state into the UI.

## Step 5: Change state from the keyboard

Wire keys to the counter with `KeyBindings`, the declarative way to register shortcuts. Press
**`+`** to increment, **`-`** to decrement, and **`r`** to reset.

```kotlin
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.runtime.KeyBindings

@Composable
fun App() {
    val theme = LocalTheme.current
    var count by remember { mutableStateOf(0) }

    KeyBindings {
        on(Key.char('+'), "increment") { count++ }
        on(Key.char('-'), "decrement") { count-- }
        on(Key.char('r'), "reset") { count = 0 }
    }

    Column {
        Text("Counter", style = theme.primary)
        Text("A first Dispatch app", style = theme.muted)
        Spacer(Modifier.height(1))
        Text("Count: $count", style = theme.accent)
        Spacer(Modifier.height(1))
        Text("+ add   - subtract   r reset   Ctrl+C quit", style = theme.muted)
    }
}
```

Run it. Press `+` a few times and watch the count rise; press `-` to lower it and `r` to reset. Each
key press updates state, and Dispatch repaints the changed line in place.

## What you built

You have a complete Dispatch app with:

- a `config { }` block that sets identity, theme, and the quit key;
- a `content { }` root composable;
- layout with `Column` and `Spacer`;
- state with `remember` / `mutableStateOf`;
- keyboard input with `KeyBindings`.

Here is the full `Main.kt`:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.runtime.DispatchApplication
import io.github.darkryh.dispatch.runtime.ExitKeyBinding
import io.github.darkryh.dispatch.runtime.KeyBindings
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Text

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "counter"
            version = "1.0.0"
            theme = DispatchTheme.Dark
            exitKeys(ExitKeyBinding.ctrl("C"))
        }
        content { App() }
    }

@Composable
fun App() {
    val theme = LocalTheme.current
    var count by remember { mutableStateOf(0) }

    KeyBindings {
        on(Key.char('+'), "increment") { count++ }
        on(Key.char('-'), "decrement") { count-- }
        on(Key.char('r'), "reset") { count = 0 }
    }

    Column {
        Text("Counter", style = theme.primary)
        Text("A first Dispatch app", style = theme.muted)
        Spacer(Modifier.height(1))
        Text("Count: $count", style = theme.accent)
        Spacer(Modifier.height(1))
        Text("+ add   - subtract   r reset   Ctrl+C quit", style = theme.muted)
    }
}
```

## Where to go next

- **Add screens.** [Build a navigated, stateful app](tutorials/build-a-navigated-app.md) grows this
  into a multi-screen app with navigation, an MVI view model, and Koin DI.
- **Solve a specific task.** Browse the [how-to guides](how-to/index.md) — [lay out a
  screen](how-to/lay-out-a-screen.md), [handle keyboard input](how-to/handle-keyboard-input.md),
  [theme your app](how-to/theme-your-app.md).
- **Look up widgets.** The [widget reference](reference/widgets.md) lists every widget and its
  options.
- **Understand the rendering.** [The render model](explanation/render-model.md) explains how Dispatch
  repaints a terminal without flicker.
