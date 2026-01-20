package com.ead.dispatch.sample.presentation.chat

data class ChatUiState(
    val text: String = "",
    val status: String = "idle",
    val totalTokens: Int? = null,
)
