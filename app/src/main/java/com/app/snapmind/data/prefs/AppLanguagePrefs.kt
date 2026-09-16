package com.app.snapmind.data.prefs

import android.content.Context
import com.app.snapmind.domain.model.AppLanguage

private const val PREFS_NAME = "snapmind_language"
private const val KEY_LANGUAGE = "app_language"

/**
 * Mirror of the language choice, readable synchronously outside a coroutine.
 *
 * DataStore is the source of truth everywhere else, but MainActivity.attachBaseContext() runs
 * before anything may suspend, and notification builders run from a Service/BroadcastReceiver
 * with no Activity to read from -- both need the current choice right now.
 */
object AppLanguagePrefs {
    fun get(context: Context): AppLanguage {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return runCatching { enumValueOf<AppLanguage>(raw ?: "") }.getOrDefault(AppLanguage.SYSTEM)
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}
