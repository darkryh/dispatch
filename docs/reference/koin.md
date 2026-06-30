# Koin DI reference

Package: `io.github.darkryh.dispatch.koin`. Module: `dispatch-koin`.

The Koin module integrates [Koin](https://insert-koin.io/) so you can declare dependencies once and
resolve view models — with their constructor arguments — automatically. You install Koin from the
`config { }` block, declare a module with the Dispatch DSL, and connect it to navigation with a
`KoinViewModelFactory`.

## Installing Koin

```kotlin
fun DispatchConfig.koin(
    vararg validateViewModels: KClass<out ViewModel>,
    stopOnExit: Boolean = true,
    appDeclaration: KoinAppDeclaration,
): KoinApplication
```

Call `koin { }` inside `config { }`. It starts Koin, registers a stop-on-exit hook when
`stopOnExit = true`, and validates that the listed (or DSL-registered) view models resolve.

```kotlin
DispatchApplication(args) {
    config {
        name = "my-app"
        koin { modules(appModule) }
    }
    content { App() }
}
```

## Declaring a module

```kotlin
fun dispatchModule(builder: DispatchKoinModuleScope.() -> Unit): Module
```

`dispatchModule` builds a Koin `Module` with a Dispatch-aware DSL. Inside it:

```kotlin
class DispatchKoinModuleScope {
    inline fun <reified T : ViewModel> viewModel(
        closeAfterValidation: Boolean = true,
        noinline savedStateHandleProvider: (() -> SavedStateHandle)? = null,
        noinline definition: Definition<T>,
    )
    inline fun <reified T> factory(noinline definition: Definition<T>)
    inline fun <reified T> single(noinline definition: Definition<T>)   // auto-closes AutoCloseable on shutdown
    fun includes(vararg modules: Module)
}
```

- `viewModel { … }` — declares a view model as a Koin factory and registers it for startup
  validation.
- `factory { … }` / `single { … }` — ordinary Koin definitions. `single` auto-registers `onClose`
  for `AutoCloseable` instances.
- `includes(...)` — pull in other Koin modules.

```kotlin
val appModule = dispatchModule {
    single { ChatRepository() }
    single<ResponseSimulator> { LocalResponseSimulator() }

    viewModel { HomeViewModel() }
    viewModel { ChatViewModel(repository = get(), simulator = get()) }
}
```

## Connecting Koin to navigation

```kotlin
class KoinViewModelFactory(koin: Koin = DispatchKoin.koin()) : ViewModelFactory, SavedStateViewModelFactory

@Composable
fun KoinViewModelProviderScope(koin: Koin = DispatchKoin.koin(), content: @Composable () -> Unit)
```

Pass a `KoinViewModelFactory` to `NavDisplay` so each entry's view model is resolved from Koin:

```kotlin
NavDisplay(
    backStack = backStack,
    viewModelFactory = KoinViewModelFactory(),
    entryProvider = entryProvider { /* … */ },
)
```

Then obtain the view model in a screen with the usual `viewModel()` — it resolves through Koin:

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) { /* … */ }
```

`KoinViewModelProviderScope` does the same for a subtree that is not hosted by `NavDisplay`.

## Injecting elsewhere

```kotlin
inline fun <reified T : Any> inject(
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
    noinline parameters: ParametersDefinition? = null,
): Lazy<T>
```

A lazy property delegate resolving `T` from the Dispatch Koin container:

```kotlin
val repository: ChatRepository by inject()
```

## DispatchKoin helpers

```kotlin
object DispatchKoin {
    fun start(appDeclaration: KoinAppDeclaration): KoinApplication
    fun stop()
    fun koin(): Koin
    fun installShutdownHook()
    fun validateViewModels(vararg modelClasses: KClass<out ViewModel>, koin: Koin = koin(), closeAfterValidation: Boolean = false)
}
```

`config.koin { }` calls these for you; use them directly only if you manage Koin's lifecycle
yourself.

## See also

- [Wire dependency injection with Koin](../how-to/use-koin-di.md) — a task-focused guide.
- [View models & MVI](viewmodel.md) — the view models Koin resolves.
- [Navigation](navigation.md#navdisplay) — where the factory is wired in.
