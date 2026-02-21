package com.readflow.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.readflow.app.data.local.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    suspend fun getAll(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}
