package com.readflow.app.domain.model

data class SyncConflict(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val localPosition: Int,
    val localProgress: Float,
    val remotePosition: Int,
    val remoteProgress: Float,
    val createdAt: Long,
)
