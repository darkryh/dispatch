package io.github.darkryh.dispatch.initializr

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.darkryh.dispatch.initializr.ui.InitializrApp
import io.github.darkryh.dispatch.initializr.ui.LocalReducedMotion
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("loading")?.remove()
    val reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ComposeViewport(document.body!!) {
        CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
            InitializrApp()
        }
    }
}
