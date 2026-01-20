package com.ead.dispatch.sample.presentation.chat.event

import com.ead.dispatch.navigation.NavController

sealed  class ChatEvent {
    data object OnClearTextField : ChatEvent()
    data class OnTextChanged(val text: String) : ChatEvent()
    data class OnSubmitMessage(val navController: NavController,val text: String) : ChatEvent()
    data object OnChatModeChanged : ChatEvent()
    data object OnCancelProcessing : ChatEvent()
}
