package com.readflow.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.readflow.app.data.local.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY updated_at DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY updated_at DESC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BookEntity>)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query(
        "UPDATE books SET current_position = :position, progress = :progress, total_chars = :totalChars, " +
            "last_read_at = :lastReadAt, updated_at = :updatedAt WHERE id = :bookId"
    )
    suspend fun updateProgress(
        bookId: String,
        position: Int,
        progress: Float,
        totalChars: Int,
        lastReadAt: Long,
        updatedAt: Long,
    )
}
