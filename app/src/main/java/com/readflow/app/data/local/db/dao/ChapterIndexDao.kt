package com.readflow.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.readflow.app.data.local.db.entity.ChapterIndexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterIndexDao {
    @Query("SELECT * FROM chapter_indices WHERE book_id = :bookId ORDER BY chapter_order ASC")
    fun observeByBook(bookId: String): Flow<List<ChapterIndexEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterIndexEntity>)

    @Query("DELETE FROM chapter_indices WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
