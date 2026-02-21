package com.readflow.app.domain.model

data class VocabularyWord(
    val id: String,
    val bookId: String,
    val word: String,
    val meaning: String,
    val sentence: String,
    val createdAt: Long,
)
