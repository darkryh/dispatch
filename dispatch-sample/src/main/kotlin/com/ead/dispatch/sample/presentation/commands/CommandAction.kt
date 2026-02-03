package com.ead.dispatch.sample.presentation.commands

sealed class CommandAction {
    object ClearContext : CommandAction()
    data class OpenEntityList(val type: String) : CommandAction()
    object OpenStoryChat : CommandAction()
}
