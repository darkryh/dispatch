package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ExitPromptState {
    var isArmed: Boolean by mutableStateOf(false)
}
