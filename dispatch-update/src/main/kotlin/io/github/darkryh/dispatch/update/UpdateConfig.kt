package io.github.darkryh.dispatch.update

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class UpdateConfig(
    val enabled: Boolean = true,
    val checkOnStartup: Boolean = true,
    val checkInterval: Duration = 24.hours,
    val packageIds: Map<UpdateSource, String> = emptyMap(),
    val providers: Map<UpdateSource, UpdateProvider> = emptyMap(),
    val sourceOverride: UpdateSource? = null,
    val commandOverride: String? = null,
    @Deprecated(
        "Has no effect — nothing reads this flag. Pass refreshBeforeCheck to " +
            "ScoopUpdateProvider's constructor instead, which is the only provider that " +
            "supports a pre-check refresh.",
    )
    val refreshBeforeCheck: Boolean = false,
    val allowExternalCommands: Boolean = true,
)
