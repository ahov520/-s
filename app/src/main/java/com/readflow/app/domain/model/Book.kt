package com.readflow.app.domain.model

data class Book(
    val id: String,
    val title: String,
    val fileUri: String,
    val coverColor: String,
    val progress: Float,
    val currentPosition: Int,
    val totalChars: Int,
    val encoding: String,
    val fileSizeBytes: Long,
    val lastReadAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
