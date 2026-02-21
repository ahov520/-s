package com.readflow.app.domain.model

data class BookSubscription(
    val bookId: String,
    val sourceUrl: String,
    val etag: String,
    val lastModified: String,
    val hasUpdate: Boolean,
    val lastCheckedAt: Long,
)
