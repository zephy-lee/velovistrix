package com.regnius.photoprism.core.di

import com.regnius.photoprism.core.auth.SessionSource
import com.regnius.photoprism.core.auth.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindSessionSource(impl: SessionStore): SessionSource
}
