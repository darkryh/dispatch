package com.ead.dispatch.sample.presentation.library.model

sealed class ListEntry<out T> {
    data class Create(val title: String, val subtitle: String? = null) : ListEntry<Nothing>()
    data class Item<T>(val data: T) : ListEntry<T>()
}
