# Wire dependency injection with Koin

This guide shows how to declare dependencies with [Koin](https://insert-koin.io/) and have Dispatch
resolve your view models — with their constructor arguments — automatically. It assumes a working app
that uses view models and navigation.

Add the dependency:

```kotlin
implementation("io.github.darkryh.dispatch:dispatch-koin:1.0.0")
```

## Declare a module

Build a module with `dispatchModule`. Use `single` for shared objects, `factory` for per-request
objects, and `viewModel` for view models. Resolve constructor dependencies with `get()`.

```kotlin
import com.ead.dispatch.koin.dispatchModule

val appModule = dispatchModule {
    single { ChatRepository() }
    single<ResponseService> { LocalResponseService() }

    viewModel { HomeViewModel() }
    viewModel { ChatViewModel(repository = get(), service = get()) }
}
```

## Install Koin in config

Call `koin { }` inside `config { }`, passing your module(s). It starts Koin, validates that the view
models resolve, and stops Koin on exit.

```kotlin
import com.ead.dispatch.koin.koin

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "my-app"
            koin { modules(appModule) }
        }
        content { App() }
    }
```

## Connect Koin to navigation

Pass a `KoinViewModelFactory` to `NavDisplay` so each screen's view model is resolved from Koin.

```kotlin
import com.ead.dispatch.koin.KoinViewModelFactory

@Composable
fun App() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        viewModelFactory = KoinViewModelFactory(),
        entryProvider = entryProvider {
            entry<HomeRoute> { HomeScreen() }
            entry<ChatRoute> { ChatScreen() }
        },
    )
}
```

## Obtain a view model

In a screen, call `viewModel()` with no arguments — it now resolves through Koin, dependencies and
all.

```kotlin
import com.ead.dispatch.viewmodel.viewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    // …
}
```

## Inject elsewhere

To resolve a dependency outside a view model, use the `inject` delegate:

```kotlin
import com.ead.dispatch.koin.inject

val repository: ChatRepository by inject()
```

## Inject into a subtree without navigation

If a composable subtree is not hosted by `NavDisplay` but still needs Koin-resolved view models,
wrap it in `KoinViewModelProviderScope`:

```kotlin
import com.ead.dispatch.koin.KoinViewModelProviderScope

KoinViewModelProviderScope {
    SettingsScreen()   // viewModel() inside here resolves from Koin
}
```

## Related

- [Koin DI reference](../reference/koin.md) — the full module DSL and helpers.
- [Use view models and MVI](use-viewmodels.md) — the view models Koin resolves.
- [Add navigation](add-navigation.md) — where the factory is wired in.
