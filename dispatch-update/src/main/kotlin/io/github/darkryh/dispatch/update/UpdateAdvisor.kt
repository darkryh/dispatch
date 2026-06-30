package io.github.darkryh.dispatch.update

import io.github.darkryh.dispatch.runtime.DispatchConfig

class UpdateAdvisor(
    private val dispatchConfig: DispatchConfig,
    private val updateConfig: UpdateConfig = UpdateConfig(),
    private val sourceResolver: UpdateSourceResolver = DefaultUpdateSourceResolver(),
    private val commandProvider: UpdateCommandProvider = DefaultUpdateCommandProvider(),
    private val environment: UpdateEnvironment = SystemUpdateEnvironment(),
) {
    suspend fun check(): UpdateAdvice? {
        if (!updateConfig.enabled) return null

        val current = dispatchConfig.version?.trim().orEmpty()
        if (current.isEmpty()) return null

        val appName = dispatchConfig.name ?: "dispatch"
        val resolvedSource =
            updateConfig.sourceOverride
                ?: sourceResolver.resolve(appName, updateConfig, environment)

        val selected = selectProvider(resolvedSource)
        if (selected == null) return null

        val providerSource = selected.first
        val provider = selected.second

        if (!updateConfig.allowExternalCommands && provider is CommandBasedUpdateProvider) {
            val fallback = updateConfig.providers[UpdateSource.GITHUB] ?: return null
            return checkWithProvider(UpdateSource.GITHUB, fallback, current, appName)
        }

        return checkWithProvider(providerSource, provider, current, appName)
    }

    private fun selectProvider(source: UpdateSource): Pair<UpdateSource, UpdateProvider>? {
        val direct = updateConfig.providers[source]
        if (direct != null) return source to direct

        val fallback = updateConfig.providers[UpdateSource.GITHUB]
        return if (fallback != null) UpdateSource.GITHUB to fallback else null
    }

    private suspend fun checkWithProvider(
        source: UpdateSource,
        provider: UpdateProvider,
        current: String,
        appName: String,
    ): UpdateAdvice? {
        val latest = provider.latestVersion()?.trim().orEmpty()
        if (latest.isEmpty()) return null
        if (!VersionComparator.isNewer(latest, current)) return null

        val packageName = updateConfig.packageIds[source] ?: appName
        val command =
            if (updateConfig.commandOverride != null) {
                updateConfig.commandOverride
            } else if (!updateConfig.allowExternalCommands) {
                null
            } else {
                commandProvider.commandFor(source, packageName)
            }

        val message = buildMessage(current, latest, command)

        return UpdateAdvice(
            currentVersion = current,
            latestVersion = latest,
            source = source,
            command = command,
            message = message,
        )
    }

    private fun buildMessage(
        current: String,
        latest: String,
        command: String?,
    ): String =
        if (command.isNullOrBlank()) {
            "Update available: $current -> $latest"
        } else {
            "Update available: $current -> $latest. Run: $command"
        }
}
