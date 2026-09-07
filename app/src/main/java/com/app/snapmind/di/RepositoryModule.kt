package com.app.snapmind.di

import com.app.snapmind.data.repository.CapturedItemRepositoryImpl
import com.app.snapmind.domain.repository.CapturedItemRepository
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
    abstract fun bindCapturedItemRepository(
        impl: CapturedItemRepositoryImpl
    ): CapturedItemRepository
}
