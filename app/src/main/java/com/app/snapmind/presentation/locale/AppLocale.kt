package com.app.snapmind.presentation.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.app.snapmind.domain.model.AppLanguage
import java.util.Locale

/** SYSTEM leaves [this] untouched -- CS/EN wrap it in a Configuration pinned to that locale. */
fun Context.withAppLocale(language: AppLanguage): Context {
    val locale = when (language) {
        AppLanguage.SYSTEM -> return this
        AppLanguage.CS -> Locale.forLanguageTag("cs")
        AppLanguage.EN -> Locale.forLanguageTag("en")
    }
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}

/**
 * Live, no-recreate() language switch for MainActivity's content tree.
 *
 * Two things a plain withAppLocale() call would get wrong here:
 *
 * - It reads resources.configuration off [this] Activity. If attachBaseContext already forced
 *   CS/EN at process start (from a choice made in a previous session), that configuration is
 *   already locale-locked, so picking SYSTEM again live would not actually revert anything.
 *   Deriving from applicationContext instead -- which attachBaseContext never touches -- makes
 *   SYSTEM a true revert within the same session.
 * - createConfigurationContext() returns a bare Context, not a wrapper around the Activity.
 *   Overriding LocalContext with that would break anything further down the tree that walks
 *   the ContextWrapper chain to find the Activity (billing's findActivity(), startActivity()
 *   without FLAG_ACTIVITY_NEW_TASK, permission launchers). Wrapping the Activity itself and
 *   only overriding getResources() keeps that chain intact.
 */
fun Activity.withLiveAppLocale(language: AppLanguage): Context {
    val localizedResources = applicationContext.withAppLocale(language).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}
