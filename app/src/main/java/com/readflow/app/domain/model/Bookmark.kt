package com.readflow.app.domain.model

data class Bookmark(
    val id: String,
    val bookId: String,
    val position: Int,
    val label: String,
    val createdAt: Long,
)
