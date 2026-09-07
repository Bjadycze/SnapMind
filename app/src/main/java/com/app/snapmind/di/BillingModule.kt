package com.app.snapmind.di

import com.app.snapmind.data.billing.PlayEntitlementProvider
import com.app.snapmind.domain.billing.EntitlementProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Replaces the constant `true` binding of v1 (spec.md 11.2). `AlwaysProEntitlementProvider`
 * stays in the source tree unbound -- swap it in here to develop a paid feature without a
 * Play Console round trip.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindEntitlementProvider(impl: PlayEntitlementProvider): EntitlementProvider
}
