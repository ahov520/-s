package com.readflow.app.domain.model

data class ChapterIndex(
    val id: String,
    val bookId: String,
    val chapterOrder: Int,
    val title: String,
    val startChar: Int,
    val endChar: Int,
)
