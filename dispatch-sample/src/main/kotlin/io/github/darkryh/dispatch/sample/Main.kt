package io.github.darkryh.dispatch.sample

import io.github.darkryh.dispatch.koin.koin
import io.github.darkryh.dispatch.runtime.DispatchApplication
import io.github.darkryh.dispatch.runtime.ExitKeyBinding
import io.github.darkryh.dispatch.sample.di.sampleModule
import io.github.darkryh.dispatch.sample.presentation.DispatchSampleApp
import io.github.darkryh.dispatch.theme.DispatchTheme
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
