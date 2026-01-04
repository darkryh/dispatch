package com.ead.dispatch.runtime

import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.setValue

class ExitPromptState {
    var isArmed: Boolean by mutableStateOf(false)
}
