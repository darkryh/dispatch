package com.ead.dispatch.sample.presentation.commands

import com.ead.dispatch.sample.domain.entity.EntityOptionType

sealed class CommandAction {
    object ClearContext : CommandAction()
    data class OpenEntityList(val type: EntityOptionType) : CommandAction()
    object OpenStoryChat : CommandAction()
}
