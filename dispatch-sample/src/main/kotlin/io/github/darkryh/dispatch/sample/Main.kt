package io.github.darkryh.dispatch.sample

import io.github.darkryh.dispatch.koin.koin
import io.github.darkryh.dispatch.runtime.DispatchApplication
import io.github.darkryh.dispatch.runtime.ExitKeyBinding
import io.github.darkryh.dispatch.sample.di.sampleModule
import io.github.darkryh.dispatch.sample.presentation.DispatchSampleApp
import io.github.darkryh.dispatch.theme.DispatchTheme
import kotlin.time.Duration.Companion.milliseconds

/** Sample-only idle timeout: hibernate after this long with no input so the demo shows it quickly. */
private const val DEFAULT_SAMPLE_IDLE_TIMEOUT_MS = 5_000L

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
                // The sample ships a short idle timeout so hibernation is visible within a few seconds
                // of a normal run — production apps keep the framework's 5-minute default. Override via
                //   DISPATCH_SAMPLE_IDLE_TIMEOUT_MS=1500 ./dispatch-sample
                val idleTimeoutMs =
                    System.getenv("DISPATCH_SAMPLE_IDLE_TIMEOUT_MS")?.toLongOrNull() ?: DEFAULT_SAMPLE_IDLE_TIMEOUT_MS
                idleTimeout = idleTimeoutMs.milliseconds
            }

            koin { modules(sampleModule) }
        }

        content { DispatchSampleApp() }
    }
