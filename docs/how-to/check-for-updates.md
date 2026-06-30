# Check for updates

This guide shows how to notify users when a newer version of your app is available, through GitHub
releases or a package manager (Homebrew, Scoop, APT). It assumes a working app.

The feature is opt-in. Add the core module plus a provider for each channel you ship through:

```kotlin
implementation("io.github.darkryh:dispatch-update:1.0.0-beta01")
implementation("io.github.darkryh:dispatch-update-github:1.0.0-beta01")
// or -brew / -scoop / -apt
```

The check reads your app's current version from `config.version`, so make sure that is set.

## Show an update banner

`rememberUpdateAdvice` runs a throttled check off the render thread and returns an `UpdateAdvice`, or
`null` when you are up to date. Configure which provider serves which channel.

```kotlin
import io.github.darkryh.dispatch.update.UpdateConfig
import io.github.darkryh.dispatch.update.UpdateSource
import io.github.darkryh.dispatch.update.rememberUpdateAdvice
import io.github.darkryh.dispatch.update.GithubReleaseUpdateProvider

@Composable
fun App() {
    val theme = LocalTheme.current
    val advice = rememberUpdateAdvice(
        UpdateConfig(
            providers = mapOf(
                UpdateSource.GITHUB to GithubReleaseUpdateProvider(owner = "darkryh", repo = "dispatch"),
            ),
        ),
    )

    Column {
        if (advice != null) {
            Text(advice.message, style = theme.warning)
            advice.command?.let { Text("Run: $it", style = theme.muted) }
        }
        // … the rest of your UI
    }
}
```

`advice.message` reads like `Update available: 1.0.0 -> 1.1.0`, and `advice.command` is the upgrade
command for the resolved channel (or `null` for GitHub/manual).

## Configure the check

`UpdateConfig` controls timing and behavior:

```kotlin
UpdateConfig(
    enabled = true,
    checkOnStartup = true,
    checkInterval = 24.hours,        // throttle: don't recheck more often than this
    allowExternalCommands = true,    // permit running brew/scoop/apt to read versions
    providers = mapOf(/* … */),
)
```

Set `allowExternalCommands = false` to forbid spawning package managers; the advisor then falls back
to the GitHub provider.

## Use a package-manager provider

Command-based providers read the latest version by shelling out. Supply a `CommandRunner` and map the
provider to its source:

```kotlin
import io.github.darkryh.dispatch.update.BrewUpdateProvider

UpdateConfig(
    providers = mapOf(
        UpdateSource.HOMEBREW to BrewUpdateProvider(formula = "my-app", runner = systemRunner),
    ),
)
```

The advisor picks the channel for the current machine automatically (by OS and what is on `PATH`), so
you can register several providers and let it choose.

## Check outside composition

To check without a composable — for example in a `--check-updates` flag — use `UpdateAdvisor`
directly:

```kotlin
import io.github.darkryh.dispatch.update.UpdateAdvisor

val advisor = UpdateAdvisor(dispatchConfig = config, updateConfig = updateConfig)
val advice = advisor.check()   // suspend; null when up to date
```

## Related

- [Self-update reference](../reference/update.md) — every provider, config option, and result type.
