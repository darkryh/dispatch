package com.ead.dispatch.sample

import com.ead.dispatch.runtime.DispatchApplication
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.koin.koin
import com.ead.dispatch.sample.di.module
import com.ead.dispatch.sample.presentation.DispatchSampleApp
import com.ead.dispatch.runtime.ExitKeyBinding
import kotlin.time.Duration.Companion.milliseconds

fun main(args: Array<String>) = DispatchApplication(args) {
    config {
        name = "dispatch-sample"
        windowTitle = "Dispatch"
        enforceWindowTitle = true
        version = "0.0.1"
        description = "Dispatch Sample - Chat Interface"
        theme = DispatchTheme.Dark
        targetFps = 60

        argument(name = "start", shortName = 's', description = "Start screen route")

        flag(name = "resume", shortName = 'r', description = "Start on session selector screen")

        exitKeys(ExitKeyBinding.ctrl("C"),)
        requireExitDoublePress = true
        exitTimeoutOnDoublePress = 1500.milliseconds

        koin {
            modules(module)
        }
    }

    // renderer section where to insert composables / dispatch widgets
    renderer {
        DispatchSampleApp()
    }
}
