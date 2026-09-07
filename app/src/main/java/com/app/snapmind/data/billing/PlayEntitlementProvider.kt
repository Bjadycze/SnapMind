package com.app.snapmind.data.billing

import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.domain.billing.EntitlementProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entitlement as the rest of the app sees it (spec.md 11.2): one boolean, no billing types
 * anywhere outside `data/billing`.
 *
 * Fails open by construction. `BillingManager.entitled` is null until Play answers, and in that
 * window the cached result of the last successful check is what counts -- so a flat battery,
 * a train tunnel or a Play outage never revokes access to something already paid for.
 *
 * A lapsed subscription stops new generation only. Everything already produced stays visible
 * and searchable (spec.md 11.12); nothing in this file deletes anything.
 */
@Singleton
class PlayEntitlementProvider @Inject constructor(
    private val billing: BillingManager,
    private val settings: SettingsDataStore
) : EntitlementProvider {

    override fun isPro(): Flow<Boolean> =
        combine(billing.entitled, settings.proEntitled) { live, cached -> live ?: cached }
}
