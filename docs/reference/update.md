# Self-update reference

Package: `io.github.darkryh.dispatch.update`. Modules: `dispatch-update` (core), plus one provider artifact per
channel: `dispatch-update-github`, `dispatch-update-brew`, `dispatch-update-scoop`,
`dispatch-update-apt`.

The update module checks whether a newer version of your app is available and tells the user how to
upgrade. The core module resolves the distribution channel and compares versions; provider artifacts
read the latest version from a specific source. The whole feature is optional — depend only on the
core module plus the providers you ship through.

## rememberUpdateAdvice

```kotlin
@Composable
fun rememberUpdateAdvice(
    updateConfig: UpdateConfig = UpdateConfig(),
    sourceResolver: UpdateSourceResolver = DefaultUpdateSourceResolver(),
    commandProvider: UpdateCommandProvider = DefaultUpdateCommandProvider(),
    environment: UpdateEnvironment = SystemUpdateEnvironment(),
): UpdateAdvice?
```

The composable entry point. On first composition it runs a throttled check off the render thread
(`Dispatchers.IO`) and returns the resulting `UpdateAdvice`, or `null` when no update is available
(or checks are disabled). It reads the app's version and name from `LocalDispatchConfig`, so it works
without extra wiring.

```kotlin
@Composable
fun App() {
    val advice = rememberUpdateAdvice(
        UpdateConfig(providers = mapOf(UpdateSource.GITHUB to GithubReleaseUpdateProvider("darkryh", "dispatch"))),
    )
    if (advice != null) {
        Text("${advice.message}", style = LocalTheme.current.warning)
    }
    // … rest of the UI
}
```

## UpdateAdvice

```kotlin
data class UpdateAdvice(
    val currentVersion: String,
    val latestVersion: String,
    val source: UpdateSource,
    val command: String?,
    val message: String,
)
```

The result of a successful check. `command` is the upgrade command for the resolved source (or
`null` for GitHub/manual). "Up to date" is represented by a `null` result, not a separate type.

## UpdateConfig

```kotlin
data class UpdateConfig(
    val enabled: Boolean = true,
    val checkOnStartup: Boolean = true,
    val checkInterval: Duration = 24.hours,
    val packageIds: Map<UpdateSource, String> = emptyMap(),
    val providers: Map<UpdateSource, UpdateProvider> = emptyMap(),
    val sourceOverride: UpdateSource? = null,
    val commandOverride: String? = null,
    val refreshBeforeCheck: Boolean = false,
    val allowExternalCommands: Boolean = true,
)
```

Controls whether and how checks run. Register a provider per channel in `providers`. Set
`allowExternalCommands = false` to forbid spawning `brew`/`scoop`/`apt`; the advisor then falls back
to the GitHub provider.

## UpdateSource

```kotlin
enum class UpdateSource { HOMEBREW, SCOOP, APT, GITHUB, MANUAL, UNKNOWN }
```

The distribution channel. `DefaultUpdateSourceResolver` picks one by OS and what is on `PATH`
(`brew` → `HOMEBREW`, `scoop` → `SCOOP`, `apt` → `APT`), falling back to `MANUAL`.

## Providers

A provider returns the latest available version string for one source.

```kotlin
interface UpdateProvider { suspend fun latestVersion(): String? }
interface CommandBasedUpdateProvider : UpdateProvider   // marker for command-backed providers
class StaticUpdateProvider(version: String?) : UpdateProvider
```

### GitHub releases — `dispatch-update-github`

```kotlin
class GithubReleaseUpdateProvider : UpdateProvider, AutoCloseable {
    constructor(owner: String, repo: String, tagPrefixToTrim: String? = "v")
    constructor(owner: String, repo: String, engine: HttpClientEngine, tagPrefixToTrim: String? = "v")
    constructor(owner: String, repo: String, client: HttpClient, tagPrefixToTrim: String? = "v")
}
```

Reads the latest release's `tag_name` from the GitHub REST API and trims `tagPrefixToTrim`. The
default constructor lazily owns an HTTP client (closed by `close()`); the other two let you inject an
engine or a shared client.

### Homebrew — `dispatch-update-brew`

```kotlin
class BrewUpdateProvider(formula: String, runner: CommandRunner) : CommandBasedUpdateProvider
```

Reads a formula's stable version from `brew info --json=v2`.

### Scoop — `dispatch-update-scoop`

```kotlin
class ScoopUpdateProvider(app: String, runner: CommandRunner, refreshBeforeCheck: Boolean = false) : CommandBasedUpdateProvider
```

Parses `scoop status` for the latest version; optionally runs `scoop update` first.

### APT — `dispatch-update-apt`

```kotlin
class AptUpdateProvider(packageName: String, runner: CommandRunner) : CommandBasedUpdateProvider
```

Reads the candidate version from `apt-cache policy`.

## Lower-level pieces

The advisor and supporting types, for when you check outside composition.

```kotlin
class UpdateAdvisor(
    dispatchConfig: DispatchConfig,
    updateConfig: UpdateConfig = UpdateConfig(),
    sourceResolver: UpdateSourceResolver = DefaultUpdateSourceResolver(),
    commandProvider: UpdateCommandProvider = DefaultUpdateCommandProvider(),
    environment: UpdateEnvironment = SystemUpdateEnvironment(),
) {
    suspend fun check(): UpdateAdvice?
}

interface UpdateSourceResolver { fun resolve(appName: String, config: UpdateConfig, env: UpdateEnvironment): UpdateSource }
class DefaultUpdateSourceResolver : UpdateSourceResolver

interface UpdateCommandProvider { fun commandFor(source: UpdateSource, packageName: String): String? }
class DefaultUpdateCommandProvider : UpdateCommandProvider

interface CommandRunner { fun run(vararg args: String): CommandResult }
data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) { val isSuccess: Boolean }

object VersionComparator {
    fun isNewer(latest: String, current: String): Boolean
    fun compare(a: String, b: String): Int
}

interface UpdateEnvironment {
    val os: OperatingSystem
    val pathEntries: List<String>
    val classPathEntries: List<String>
    fun env(name: String): String?
    fun fileExists(path: String): Boolean
}
class SystemUpdateEnvironment : UpdateEnvironment
enum class OperatingSystem { MAC, WINDOWS, LINUX, OTHER }

object UpdateCheckThrottle {
    fun shouldCheck(key: String, interval: Duration, now: Long = System.currentTimeMillis()): Boolean
}
```

Enable verbose update logging with the JVM flag `-Ddispatch.update.debug=true`.

## See also

- [Check for updates](../how-to/check-for-updates.md) — a task-focused guide.
