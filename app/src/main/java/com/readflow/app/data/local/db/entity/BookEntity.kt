package com.readflow.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["file_uri"], unique = true)]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "file_uri") val fileUri: String,
    @ColumnInfo(name = "cover_color") val coverColor: String,
    @ColumnInfo(name = "cover_image_url") val coverImageUrl: String?,
    val progress: Float,
    @ColumnInfo(name = "current_position") val currentPosition: Int,
    @ColumnInfo(name = "total_chars") val totalChars: Int,
    val encoding: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
