package com.ead.dispatch.sample.presentation.commands

sealed class CommandAction {
    object ShowHelp : CommandAction()
    object ClearContext : CommandAction()
    object OpenStoryInfo : CommandAction()
    object OpenStorySummary : CommandAction()
    data class OpenEntityList(val type: String) : CommandAction()
}
