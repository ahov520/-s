package com.readflow.app.di

import com.readflow.app.data.repository.BookRepositoryImpl
import com.readflow.app.data.repository.BookmarkRepositoryImpl
import com.readflow.app.data.repository.ChapterIndexRepositoryImpl
import com.readflow.app.data.repository.ReaderFileRepositoryImpl
import com.readflow.app.data.repository.ReaderSettingsRepositoryImpl
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.BookmarkRepository
import com.readflow.app.domain.repository.ChapterIndexRepository
import com.readflow.app.domain.repository.ReaderFileRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindChapterIndexRepository(impl: ChapterIndexRepositoryImpl): ChapterIndexRepository

    @Binds
    @Singleton
    abstract fun bindReaderSettingsRepository(impl: ReaderSettingsRepositoryImpl): ReaderSettingsRepository

    @Binds
    @Singleton
    abstract fun bindReaderFileRepository(impl: ReaderFileRepositoryImpl): ReaderFileRepository
}
