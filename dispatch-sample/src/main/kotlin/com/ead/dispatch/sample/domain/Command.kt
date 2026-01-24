package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.domain.model.story.WriterMode

data class Command(
    val label: String,
    val description: String,
    val modes: Set<WriterMode> = emptySet(),
)
