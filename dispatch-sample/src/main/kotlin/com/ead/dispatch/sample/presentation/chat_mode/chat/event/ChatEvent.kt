package com.ead.dispatch.sample.presentation.chat_mode.chat.event

import com.ead.dispatch.navigation.Navigator

sealed  class ChatEvent {
    data object OnClearTextField : ChatEvent()
    data class OnTextChanged(val text: String) : ChatEvent()
    data class OnSubmitMessage(val navigator: Navigator, val text: String) : ChatEvent()
    data object OnChatModeChanged : ChatEvent()
    data object OnCancelProcessing : ChatEvent()
    data object OnToggleStoryPreviewFocus : ChatEvent()
    data object OnStoryPreviewMoveLeft : ChatEvent()
    data object OnStoryPreviewMoveRight : ChatEvent()
    data object OnStoryPreviewMoveUp : ChatEvent()
    data object OnStoryPreviewMoveDown : ChatEvent()
    data class OnStoryPreviewPageCountUpdated(val pageCount: Int) : ChatEvent()
    data object OnStoryPreviewExecuteSelection : ChatEvent()
    data object OnStoryPreviewApproveShortcut : ChatEvent()
    data object OnStoryPreviewRejectShortcut : ChatEvent()
}
