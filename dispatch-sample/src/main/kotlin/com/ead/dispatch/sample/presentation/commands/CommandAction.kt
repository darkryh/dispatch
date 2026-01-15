package com.ead.dispatch.sample.presentation.commands

sealed class CommandAction {
    object ShowHelp : CommandAction()
    object ClearContext : CommandAction()
    object OpenCharacter : CommandAction()
    object OpenStoryInfo : CommandAction()
}
