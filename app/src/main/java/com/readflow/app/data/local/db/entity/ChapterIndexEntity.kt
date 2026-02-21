package com.readflow.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter_indices",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("book_id")]
)
data class ChapterIndexEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_order") val chapterOrder: Int,
    val title: String,
    @ColumnInfo(name = "start_char") val startChar: Int,
    @ColumnInfo(name = "end_char") val endChar: Int,
)
