package com.ead.dispatch.sample

import com.ead.dispatch.koin.koin
import com.ead.dispatch.runtime.DispatchApplication
import com.ead.dispatch.runtime.ExitKeyBinding
import com.ead.dispatch.sample.di.sampleModule
import com.ead.dispatch.sample.presentation.DispatchSampleApp
import com.ead.dispatch.theme.DispatchTheme
import kotlin.time.Duration.Companion.milliseconds

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "dispatch-sample"
            windowTitle = "Dispatch UI Sample"
            enforceWindowTitle = true
            version = "1.0.0"
            description = "Offline terminal UI and navigation showcase"
            theme = DispatchTheme.Dark
            targetFps = 60
            exitKeys(ExitKeyBinding.ctrl("C"))
            requireExitDoublePress = true
            exitTimeoutOnDoublePress = 1_500.milliseconds

            hibernation {
                // Framework defaults (enabled, idleFps = 1, releaseCaches, requestGc) are kept.
                // The idle timeout is overridable so demos and PTY tests can hibernate in seconds
                // instead of the production 5-minute default:
                //   DISPATCH_SAMPLE_IDLE_TIMEOUT_MS=1500 ./dispatch-sample
                System.getenv("DISPATCH_SAMPLE_IDLE_TIMEOUT_MS")?.toLongOrNull()?.let { valueMs ->
                    idleTimeout = valueMs.milliseconds
                }
            }

            koin { modules(sampleModule) }
        }

        content { DispatchSampleApp() }
    }
