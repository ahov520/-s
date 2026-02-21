package com.readflow.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.readflow.app.data.local.db.ReadFlowDatabase
import com.readflow.app.data.local.db.dao.BookDao
import com.readflow.app.data.local.db.dao.BookmarkDao
import com.readflow.app.data.local.db.dao.ChapterIndexDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN cover_image_url TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadFlowDatabase =
        Room.databaseBuilder(context, ReadFlowDatabase::class.java, "readflow.db")
            .addMigrations(migration1To2)
            .build()

    @Provides
    fun provideBookDao(database: ReadFlowDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookmarkDao(database: ReadFlowDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideChapterIndexDao(database: ReadFlowDatabase): ChapterIndexDao = database.chapterIndexDao()
}
