package com.ead.dispatch.sample.domain.embedding

data class RagContextChunk(
    val type: String,
    val label: String?,
    val content: String,
)