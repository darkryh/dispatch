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
