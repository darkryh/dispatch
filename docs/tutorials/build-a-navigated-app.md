# Build a navigated, stateful app

In this tutorial you grow a single-screen app into a small multi-screen one: a home screen that lists
items and a detail screen you open and return from. Along the way you add a typed navigation back
stack, an MVI view model, and Koin dependency injection — the pieces a real Dispatch app is built
from.

It builds on [Getting started](../getting-started.md); you should be comfortable with `config`,
`content`, `Column`, and `KeyBindings`. Plan on about twenty minutes.

## What you'll build

A "notes" app with two screens:

- **Home** — a selectable list of note titles. Up/Down move the selection; Enter opens the note.
- **Detail** — the selected note's body. Escape returns to home.

State lives in view models; the note data comes from a repository wired through Koin.

## Step 1: Add the dependencies

On top of `dispatch-core` and `dispatch-widgets`, add navigation, Koin, and the serialization
plugin.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
}

dependencies {
    implementation("io.github.darkryh.dispatch:dispatch-core:1.0.0-beta03")
    implementation("io.github.darkryh.dispatch:dispatch-widgets:1.0.0-beta03")
    implementation("io.github.darkryh.dispatch:dispatch-navigation:1.0.0-beta03")
    implementation("io.github.darkryh.dispatch:dispatch-koin:1.0.0-beta03")
}
```

## Step 2: Model the data and a repository

Create the domain types and a repository that holds the notes. The repository is plain Kotlin — no
Dispatch types.

```kotlin
// Notes.kt
data class Note(val id: String, val title: String, val body: String)

class NoteRepository {
    private val notes = listOf(
        Note("1", "Welcome", "This is your first note. Press Esc to go back."),
        Note("2", "Shopping", "Milk, bread, coffee."),
        Note("3", "Ideas", "Build a terminal UI framework."),
    )
    fun all(): List<Note> = notes
    fun byId(id: String): Note? = notes.find { it.id == id }
}
```

## Step 3: Define the routes

Each screen gets a `@Serializable` route implementing `NavKey`. Home takes no arguments; detail
carries the note id.

```kotlin
// Routes.kt
import io.github.darkryh.dispatch.navigation.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("home")
data object HomeRoute : NavKey

@Serializable @SerialName("detail")
data class DetailRoute(val noteId: String) : NavKey
```

## Step 4: Write the home view model

The home screen needs a list of notes and a moving selection. Use `MviViewModel` with a state and
two intents.

```kotlin
// HomeViewModel.kt
import io.github.darkryh.dispatch.viewmodel.MviViewModel

data class HomeState(val notes: List<Note> = emptyList(), val selected: Int = 0)

sealed interface HomeIntent {
    data object MoveUp : HomeIntent
    data object MoveDown : HomeIntent
}

class HomeViewModel(repository: NoteRepository) :
    MviViewModel<HomeState, HomeIntent>(HomeState(notes = repository.all())) {

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.MoveUp -> updateState { it.copy(selected = (it.selected - 1).coerceAtLeast(0)) }
            HomeIntent.MoveDown -> updateState {
                it.copy(selected = (it.selected + 1).coerceAtMost(it.notes.lastIndex))
            }
        }
    }
}
```

## Step 5: Write the home screen

Obtain the view model with `viewModel()`, collect its state, render a `SelectableList`, and wire
keys. Open a note by navigating to its `DetailRoute`.

```kotlin
// HomeScreen.kt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.navigation.LocalNavigator
import io.github.darkryh.dispatch.runtime.KeyBindings
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.SelectableList
import io.github.darkryh.dispatch.widget.SelectableListStyles
import io.github.darkryh.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsState()

    KeyBindings {
        on(Key.ArrowUp, "up") { viewModel.sendIntent(HomeIntent.MoveUp) }
        on(Key.ArrowDown, "down") { viewModel.sendIntent(HomeIntent.MoveDown) }
        on(Key.Enter, "open") {
            val note = state.notes.getOrNull(state.selected) ?: return@on
            navigator.navigate(DetailRoute(noteId = note.id))
        }
    }

    Column {
        Text("Notes", style = theme.primary)
        SelectableList(
            items = state.notes,
            selectedIndex = state.selected,
            styles = SelectableListStyles(prefix = TextStyle(), selectedPrefix = theme.accent),
        ) { note, selected ->
            Text(note.title, style = if (selected) theme.accent else null)
        }
        Text("↑↓ move   Enter open   Ctrl+C quit", style = theme.muted)
    }
}
```

## Step 6: Write the detail screen

The detail view model reads its route from the `SavedStateHandle` with `toRoute`, then loads the
note.

```kotlin
// DetailViewModel.kt
import io.github.darkryh.dispatch.navigation.toRoute
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import io.github.darkryh.dispatch.viewmodel.StateViewModel

data class DetailState(val note: Note? = null)

class DetailViewModel(
    handle: SavedStateHandle,
    repository: NoteRepository,
) : StateViewModel<DetailState>(DetailState()) {
    init {
        val route = handle.toRoute<DetailRoute>()
        setState(DetailState(note = repository.byId(route.noteId)))
    }
}
```

```kotlin
// DetailScreen.kt
@Composable
fun DetailScreen(viewModel: DetailViewModel = viewModel()) {
    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsState()

    KeyBindings {
        on(Key.Escape, "back") { navigator.popBackStack() }
    }

    Column {
        Text(state.note?.title ?: "Not found", style = theme.primary)
        Text(state.note?.body ?: "", style = theme.muted)
        Text("Esc back   Ctrl+C quit", style = theme.muted)
    }
}
```

## Step 7: Declare the Koin module

Wire the repository and both view models. The detail view model takes a `SavedStateHandle`, which
Koin passes through as a parameter — declare it with `get()`.

```kotlin
// AppModule.kt
import io.github.darkryh.dispatch.koin.dispatchModule
import io.github.darkryh.dispatch.runtime.SavedStateHandle

val appModule = dispatchModule {
    single { NoteRepository() }

    viewModel { HomeViewModel(repository = get()) }
    viewModel { DetailViewModel(handle = get(), repository = get()) }
}
```

## Step 8: Assemble the app

Install Koin in `config`, host the back stack in `NavDisplay`, and use a `KoinViewModelFactory` so
each screen's view model resolves from Koin.

```kotlin
// Main.kt
import io.github.darkryh.dispatch.koin.KoinViewModelFactory
import io.github.darkryh.dispatch.koin.koin
import io.github.darkryh.dispatch.navigation.NavDisplay
import io.github.darkryh.dispatch.navigation.entryProvider
import io.github.darkryh.dispatch.navigation.rememberNavBackStack
import io.github.darkryh.dispatch.runtime.DispatchApplication
import io.github.darkryh.dispatch.runtime.ExitKeyBinding
import io.github.darkryh.dispatch.theme.DispatchTheme

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "notes"
            version = "1.0.0"
            theme = DispatchTheme.Dark
            exitKeys(ExitKeyBinding.ctrl("C"))
            koin { modules(appModule) }
        }
        content { App() }
    }

@Composable
fun App() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        viewModelFactory = KoinViewModelFactory(),
        entryProvider = entryProvider {
            entry<HomeRoute> { HomeScreen() }
            entry<DetailRoute> { DetailScreen() }
        },
    )
}
```

## Step 9: Run it

A Dispatch TUI needs a real terminal, which `./gradlew run` can't give it (Gradle captures stdin and
stdout, so arrow keys and Enter never reach the app). Build a native launcher with `installDist` and
run that:

```bash
./gradlew installDist
./build/install/<project>/bin/<project>
```

`<project>` is your Gradle project name (the directory name unless you set `rootProject.name`).

You should see the notes list:

```
Notes
> Welcome
  Shopping
  Ideas
↑↓ move   Enter open   Ctrl+C quit
```

Move the selection with the arrow keys and press **Enter** to open a note. The detail screen
replaces it:

```
Shopping
Milk, bread, coffee.
Esc back   Ctrl+C quit
```

Press **Escape** to return to the list, right where you left off. Press **Ctrl+C** to quit.

## What you built

A two-screen app with the full Dispatch architecture stack:

- **Typed navigation** — `@Serializable` routes, a `NavDisplay` host, and forward/back navigation
  through `LocalNavigator`.
- **MVI view models** — `MviViewModel` and `StateViewModel`, each scoped to its navigation entry.
- **Route arguments** — read in the detail view model with `toRoute`.
- **Dependency injection** — a Koin module supplying the repository and view models, connected to
  navigation with `KoinViewModelFactory`.

## Where to go next

- **Go deeper on each piece.** [Add navigation](../how-to/add-navigation.md),
  [Use view models and MVI](../how-to/use-viewmodels.md), and
  [Wire dependency injection with Koin](../how-to/use-koin-di.md).
- **Add more widgets.** The [widget reference](../reference/widgets.md) covers tables, trees, the
  command palette, inputs, and more.
- **Understand the internals.** [Architecture and modules](../explanation/architecture.md) shows how
  the layers fit together.
