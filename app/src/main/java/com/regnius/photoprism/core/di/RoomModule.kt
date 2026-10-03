package com.regnius.photoprism.core.di

import android.content.Context
import androidx.room.Room
import com.regnius.photoprism.core.data.local.AppDatabase
import com.regnius.photoprism.core.data.local.dao.AlbumDao
import com.regnius.photoprism.core.data.local.dao.FeedRemoteKeyDao
import com.regnius.photoprism.core.data.local.dao.PhotoDao
import com.regnius.photoprism.core.data.local.dao.PhotoFeedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // pre-1.0 앱, 순수 캐시(서버가 진실 소스)라 손실 리스크가 없다 —
            // 다음 REFRESH 가 다시 채운다. AppDatabase 의 exportSchema=false 와 짝.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePhotoDao(db: AppDatabase): PhotoDao = db.photoDao()

    @Provides
    fun providePhotoFeedDao(db: AppDatabase): PhotoFeedDao = db.photoFeedDao()

    @Provides
    fun provideFeedRemoteKeyDao(db: AppDatabase): FeedRemoteKeyDao = db.feedRemoteKeyDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()
}
