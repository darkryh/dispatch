package io.github.darkryh.dispatch.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.darkryh.dispatch.runtime.LocalDispatchConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        val advisor =
            UpdateAdvisor(
                dispatchConfig = dispatchConfig,
                updateConfig = updateConfig,
                sourceResolver = sourceResolver,
                commandProvider = commandProvider,
                environment = environment,
            )
        // advisor.check() can run a BLOCKING external command (ProcessBuilder.waitFor) or HTTP call.
        // The composition runs on the single render thread, so run the check on IO to avoid stalling
        // frames. Writing the snapshot-backed `advice` state off-thread is safe and schedules a frame.
        advice = withContext(Dispatchers.IO) { advisor.check() }
    }

    return advice
}
