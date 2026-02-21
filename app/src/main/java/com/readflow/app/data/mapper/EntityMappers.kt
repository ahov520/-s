package com.readflow.app.data.mapper

import com.readflow.app.data.local.db.entity.BookEntity
import com.readflow.app.data.local.db.entity.BookmarkEntity
import com.readflow.app.data.local.db.entity.ChapterIndexEntity
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    fileUri = fileUri,
    coverColor = coverColor,
    progress = progress,
    currentPosition = currentPosition,
    totalChars = totalChars,
    encoding = encoding,
    fileSizeBytes = fileSizeBytes,
    lastReadAt = lastReadAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Book.toEntity() = BookEntity(
    id = id,
    title = title,
    fileUri = fileUri,
    coverColor = coverColor,
    progress = progress,
    currentPosition = currentPosition,
    totalChars = totalChars,
    encoding = encoding,
    fileSizeBytes = fileSizeBytes,
    lastReadAt = lastReadAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BookmarkEntity.toDomain() = Bookmark(
    id = id,
    bookId = bookId,
    position = position,
    label = label,
    createdAt = createdAt,
)

fun Bookmark.toEntity() = BookmarkEntity(
    id = id,
    bookId = bookId,
    position = position,
    label = label,
    createdAt = createdAt,
)

fun ChapterIndexEntity.toDomain() = ChapterIndex(
    id = id,
    bookId = bookId,
    chapterOrder = chapterOrder,
    title = title,
    startChar = startChar,
    endChar = endChar,
)

fun ChapterIndex.toEntity() = ChapterIndexEntity(
    id = id,
    bookId = bookId,
    chapterOrder = chapterOrder,
    title = title,
    startChar = startChar,
    endChar = endChar,
)
