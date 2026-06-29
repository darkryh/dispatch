# Navigation reference

Package: `com.ead.dispatch.navigation`. Module: `dispatch-navigation`.

Dispatch navigation is a typed, serializable back stack. You define routes as `NavKey`
implementations, host them in a `NavDisplay`, and move between them through a `Navigator`. Each entry
has its own lifecycle, saved state, and view-model scope.

## Defining routes

A route is any class implementing `NavKey`. Make it `@Serializable` so the back stack can be saved
and restored; use a `data object` for argument-less routes and a `data class` to carry arguments.

```kotlin
interface NavKey
```

```kotlin
import com.ead.dispatch.navigation.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("home")
data object HomeRoute : NavKey

@Serializable @SerialName("chat")
data class ChatRoute(val conversationId: String = "sample") : NavKey
```

## NavDisplay

```kotlin
@Composable
fun <T : NavKey> NavDisplay(
    backStack: NavBackStack<T>,
    entryProvider: (key: T) -> NavEntry<T>,
    modifier: Modifier = Modifier,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(),
    viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
    json: Json = DefaultRouteJson,
)
```

The navigation host. It renders the top entry of `backStack`, installs the `Navigator`, and manages
each entry's lifecycle and disposal. The back stack must not be empty.

```kotlin
@Composable
fun App() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<HomeRoute> { HomeScreen() }
            entry<ChatRoute> { ChatScreen() }
        },
    )
}
```

To resolve view models through Koin, pass `viewModelFactory = KoinViewModelFactory()` — see the
[Koin reference](koin.md).

## The back stack

```kotlin
class NavBackStack<T : NavKey> : MutableList<T> {
    constructor()
    constructor(vararg elements: T)
}

@Composable
fun rememberNavBackStack(vararg elements: NavKey, json: Json = DefaultRouteJson): NavBackStack<NavKey>
```

`rememberNavBackStack` creates a save/restore-able stack seeded with your start route(s). Prefer the
extension functions below over the raw `MutableList` mutators — they keep at least one entry and run
deterministic disposal:

```kotlin
fun <T : NavKey> NavBackStack<T>.navigate(key: T)         // push
fun <T : NavKey> NavBackStack<T>.popBackStack(): Boolean   // pop top if size > 1
fun NavBackStack<*>.canGoBack(): Boolean                    // size > 1
```

## Navigator

```kotlin
interface Navigator {
    fun <T : NavKey> navigate(key: T)
    fun popBackStack(): Boolean
}
```

The controller available inside `NavDisplay`. Read it from `LocalNavigator.current`:

```kotlin
val navigator = LocalNavigator.current
navigator.navigate(ChatRoute(conversationId = "42"))   // push a screen
navigator.popBackStack()                                 // go back
```

## Entries and the entry provider

```kotlin
fun <T : NavKey> entryProvider(
    fallback: (unknownScreen: T) -> NavEntry<T> = { throw IllegalStateException("Unknown screen $it") },
    builder: EntryProviderScope<T>.() -> Unit,
): (T) -> NavEntry<T>
```

Builds the route-to-content map. Inside the builder, declare an `entry` per route. The reified,
type-keyed overload is the common one:

```kotlin
entryProvider<NavKey> {
    entry<HomeRoute> { HomeScreen() }
    entry<ChatRoute> { route -> ChatScreen(conversationId = route.conversationId) }
}
```

The `EntryProviderScope` offers instance-keyed and class-keyed overloads of `entry` /
`addEntryProvider`, each taking an optional `contentKey` and `metadata`:

```kotlin
inline fun <reified K : T> entry(
    noinline clazzContentKey: (key: K) -> Any = { /* stable key */ },
    metadata: Map<String, Any> = emptyMap(),
    noinline content: @Composable (K) -> Unit,
)

fun <K : T> entry(
    key: K,
    contentKey: Any = /* stable key */,
    metadata: Map<String, Any> = emptyMap(),
    content: @Composable (K) -> Unit,
)
```

Each resolved destination is a `NavEntry`:

```kotlin
data class NavEntry<T : NavKey>(
    val key: T,
    val contentKey: Any = /* stable key */,
    val metadata: Map<String, Any> = emptyMap(),
    val content: @Composable (T) -> Unit,
)
```

At runtime, the host wraps each in a `NavBackStackEntry`, which is a `LifecycleOwner` and holds the
entry's `savedStateHandle`, `viewModelProvider`, and `lifecycleRegistry`.

## Reading route arguments

A route's typed payload is stored in the entry's `SavedStateHandle`. Read it back with `toRoute`:

```kotlin
inline fun <reified T : Any> NavBackStackEntry<*>.toRoute(json: Json = DefaultRouteJson): T?
inline fun <reified T : Any> SavedStateHandle.toRoute(json: Json = DefaultRouteJson): T      // throws if missing
inline fun <reified T : Any> SavedStateHandle.requireRoute(json: Json = DefaultRouteJson): T
```

`SavedStateHandle` also has typed single-argument accessors:

```kotlin
fun SavedStateHandle.getStringArgument(key: String): String?
fun SavedStateHandle.requireStringArgument(key: String): String
fun SavedStateHandle.getIntArgument(key: String): Int?
fun SavedStateHandle.requireIntArgument(key: String): Int
fun SavedStateHandle.getLongArgument(key: String): Long?
fun SavedStateHandle.requireLongArgument(key: String): Long
fun SavedStateHandle.getBooleanArgument(key: String): Boolean?
fun SavedStateHandle.requireBooleanArgument(key: String): Boolean
```

Because you usually pass the route object straight to the entry's `content` lambda, reading from
`SavedStateHandle` matters most inside a view model, where the route arrived as the handle.

## Entry decorators

```kotlin
open class NavEntryDecorator<T : NavKey>(
    onPop: (key: Any) -> Unit = {},
    decorate: @Composable (entry: NavBackStackEntry<T>) -> Unit,
)
```

Wrap every entry with shared behavior or providers; `onPop` runs when an entry is popped, enabling
custom disposal. Pass decorators to `NavDisplay(entryDecorators = …)`.

## Serialization

```kotlin
const val ROUTE_PAYLOAD_KEY = "__route"
val DefaultRouteJson: Json   // encodeDefaults = true, ignoreUnknownKeys = true, explicitNulls = false

inline fun <reified T : Any> routeKey(): String
fun <T : Any> decodeRoutePayload(serializer: KSerializer<T>, payload: String, json: Json = DefaultRouteJson): T
```

`DefaultRouteJson` is the `Json` used to encode and decode route payloads; pass your own to
`NavDisplay`, `rememberNavBackStack`, and `toRoute` if you need different settings.

## Composition locals

```kotlin
val LocalNavigator: ProvidableCompositionLocal<Navigator>                 // errors outside NavDisplay
val LocalNavBackStackEntry: ProvidableCompositionLocal<NavBackStackEntry<*>?>  // default null
val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner?>      // default null
```

## See also

- [Add navigation](../how-to/add-navigation.md) — a task-focused guide.
- [View models & MVI](viewmodel.md) — per-entry view-model scoping.
- [Lifecycle](lifecycle.md) — the lifecycle each entry owns.
