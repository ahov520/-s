package com.readflow.app.di

import android.content.Context
import androidx.room.Room
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadFlowDatabase =
        Room.databaseBuilder(context, ReadFlowDatabase::class.java, "readflow.db").build()

    @Provides
    fun provideBookDao(database: ReadFlowDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookmarkDao(database: ReadFlowDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideChapterIndexDao(database: ReadFlowDatabase): ChapterIndexDao = database.chapterIndexDao()
}
