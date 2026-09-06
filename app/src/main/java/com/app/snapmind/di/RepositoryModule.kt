package com.app.snapmind.di

import com.app.snapmind.data.repository.CapturedItemRepositoryImpl
import com.app.snapmind.domain.billing.AlwaysProEntitlementProvider
import com.app.snapmind.domain.billing.EntitlementProvider
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

    /** Hardcoded true in v1. See spec.md 11.2 before changing this. */
    @Binds
    @Singleton
    abstract fun bindEntitlementProvider(
        impl: AlwaysProEntitlementProvider
    ): EntitlementProvider
}
