package com.readflow.app.domain.model

data class Highlight(
    val id: String,
    val bookId: String,
    val startChar: Int,
    val endChar: Int,
    val quote: String,
    val colorKey: String,
    val note: String,
    val createdAt: Long,
)
