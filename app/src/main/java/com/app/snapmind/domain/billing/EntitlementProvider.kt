package com.app.snapmind.domain.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * spec.md 11.2. One app gated by entitlement, never a separate "Pro" APK: two listings would
 * mean two rating pools and a data migration on upgrade.
 *
 * Hardcoded to true for all of v1. Capture and reminders stay free permanently -- a paywall at
 * the moment of capture contradicts the whole premise of the app.
 */
interface EntitlementProvider {
    fun isPro(): Flow<Boolean>
}

@Singleton
class AlwaysProEntitlementProvider @Inject constructor() : EntitlementProvider {
    override fun isPro(): Flow<Boolean> = flowOf(true)
}
