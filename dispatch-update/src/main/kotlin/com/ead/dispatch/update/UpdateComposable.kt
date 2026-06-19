package com.ead.dispatch.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ead.dispatch.runtime.LocalDispatchConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberUpdateAdvice(
    updateConfig: UpdateConfig = UpdateConfig(),
    sourceResolver: UpdateSourceResolver = DefaultUpdateSourceResolver(),
    commandProvider: UpdateCommandProvider = DefaultUpdateCommandProvider(),
    environment: UpdateEnvironment = SystemUpdateEnvironment(),
): UpdateAdvice? {
    val dispatchConfig = LocalDispatchConfig.current
    var advice by remember { mutableStateOf<UpdateAdvice?>(null) }

    // updateConfig is a data class that already contains providers; keying on both is redundant and
    // would re-run the effect needlessly. The throttle timestamp lives outside composition so a
    // remount does not reset it and re-trigger external commands / HTTP.
    LaunchedEffect(dispatchConfig.version, updateConfig) {
        if (!updateConfig.enabled || !updateConfig.checkOnStartup) return@LaunchedEffect
        if (!UpdateCheckThrottle.shouldCheck(dispatchConfig.version.orEmpty(), updateConfig.checkInterval)) {
            return@LaunchedEffect
        }

        val advisor = UpdateAdvisor(
            dispatchConfig = dispatchConfig,
            updateConfig = updateConfig,
            sourceResolver = sourceResolver,
            commandProvider = commandProvider,
            environment = environment,
        )
        advice = advisor.check()
    }

    return advice
}
