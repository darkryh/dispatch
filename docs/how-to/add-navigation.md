# Add navigation

This guide shows how to move between screens with Dispatch's typed back stack: define routes, host
them, and navigate forward and back. It assumes a working single-screen app.

Add the dependency:

```kotlin
implementation("io.github.darkryh.dispatch:dispatch-navigation:1.0.0-beta01")
```

## Define routes

A route is a `@Serializable` class implementing `NavKey`. Use a `data object` for a screen with no
arguments, and a `data class` to carry arguments. Add `@SerialName` so the back stack can be saved
and restored.

```kotlin
import io.github.darkryh.dispatch.navigation.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("home")
data object HomeRoute : NavKey

@Serializable @SerialName("detail")
data class DetailRoute(val itemId: String) : NavKey
```

Routes need the Kotlin serialization plugin in your build:

```kotlin
plugins {
    kotlin("plugin.serialization") version "2.4.0"
}
```

## Host the back stack

Create a back stack seeded with your start route and render it in a `NavDisplay`. The `entryProvider`
maps each route to its screen.

```kotlin
import io.github.darkryh.dispatch.navigation.NavDisplay
import io.github.darkryh.dispatch.navigation.entryProvider
import io.github.darkryh.dispatch.navigation.rememberNavBackStack

@Composable
fun App() {
    val backStack = rememberNavBackStack(HomeRoute)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<HomeRoute> { HomeScreen() }
            entry<DetailRoute> { route -> DetailScreen(itemId = route.itemId) }
        },
    )
}
```

The `entry<DetailRoute>` lambda receives the route instance, so you can read its arguments directly.

## Navigate between screens

Get the `Navigator` from `LocalNavigator` and push or pop routes.

```kotlin
import io.github.darkryh.dispatch.navigation.LocalNavigator

@Composable
fun HomeScreen() {
    val navigator = LocalNavigator.current
    KeyBindings {
        on(Key.Enter, "open") { navigator.navigate(DetailRoute(itemId = "42")) }
    }
    Text("Press Enter to open item 42")
}
```

Go back by popping the stack. `popBackStack()` returns `true` if it popped (it keeps at least one
entry).

```kotlin
@Composable
fun DetailScreen(itemId: String) {
    val navigator = LocalNavigator.current
    KeyBindings {
        on(Key.Escape, "back") { navigator.popBackStack() }
    }
    Text("Item $itemId")
}
```

## Read arguments inside a view model

When a screen passes its route straight to `content`, read arguments from the lambda parameter (as
above). Inside a view model, the route arrives as the entry's `SavedStateHandle` — read it with
`toRoute`:

```kotlin
import io.github.darkryh.dispatch.navigation.toRoute

class DetailViewModel(handle: SavedStateHandle) : StateViewModel<DetailState>(DetailState()) {
    private val route = handle.toRoute<DetailRoute>()   // throws if missing
    init { load(route.itemId) }
}
```

## Resolve screens through Koin

To let each screen pull its view model (with dependencies) from Koin, pass a `KoinViewModelFactory`:

```kotlin
NavDisplay(
    backStack = backStack,
    viewModelFactory = KoinViewModelFactory(),
    entryProvider = entryProvider { /* … */ },
)
```

See [Wire dependency injection with Koin](use-koin-di.md).

## Related

- [Navigation reference](../reference/navigation.md) — the full API, decorators, and serialization.
- [Use view models and MVI](use-viewmodels.md) — per-screen state and logic.
- [Build a navigated, stateful app](../tutorials/build-a-navigated-app.md) — a full walkthrough.
