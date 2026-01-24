package com.ead.dispatch.sample.domain.model.story

import kotlinx.serialization.Serializable

@Serializable
data class OverflowList<T>(
    val items: List<T> = emptyList(),
    val overflowCount: Int = 0,
)

fun <T> emptyOverflowList(): OverflowList<T> = OverflowList()
