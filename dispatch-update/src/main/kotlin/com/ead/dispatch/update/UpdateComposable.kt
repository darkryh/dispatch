package com.ead.dispatch.update

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.runtime.LaunchedEffect
import com.ead.dispatch.runtime.LocalDispatchConfig
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue

@Dispatchable
fun rememberUpdateAdvice(
    updateConfig: UpdateConfig = UpdateConfig(),
    sourceResolver: UpdateSourceResolver = DefaultUpdateSourceResolver(),
    commandProvider: UpdateCommandProvider = DefaultUpdateCommandProvider(),
    environment: UpdateEnvironment = SystemUpdateEnvironment(),
): UpdateAdvice? {
    val dispatchConfig = LocalDispatchConfig.current
    var advice by remember { mutableStateOf<UpdateAdvice?>(null) }
    var lastCheckAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(dispatchConfig.version, updateConfig, updateConfig.providers) {
        if (!updateConfig.enabled || !updateConfig.checkOnStartup) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val lastCheck = lastCheckAt
        if (lastCheck != null) {
            val elapsed = now - lastCheck
            if (elapsed < updateConfig.checkInterval.inWholeMilliseconds) {
                return@LaunchedEffect
            }
        }

        lastCheckAt = now
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
