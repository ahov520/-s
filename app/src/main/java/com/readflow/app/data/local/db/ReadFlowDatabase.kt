package com.readflow.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.readflow.app.data.local.db.dao.BookDao
import com.readflow.app.data.local.db.dao.BookmarkDao
import com.readflow.app.data.local.db.dao.ChapterIndexDao
import com.readflow.app.data.local.db.entity.BookEntity
import com.readflow.app.data.local.db.entity.BookmarkEntity
import com.readflow.app.data.local.db.entity.ChapterIndexEntity

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, ChapterIndexEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReadFlowDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun chapterIndexDao(): ChapterIndexDao
}
