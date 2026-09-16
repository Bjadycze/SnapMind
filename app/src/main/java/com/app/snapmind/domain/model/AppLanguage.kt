package com.app.snapmind.domain.model

/**
 * The app's display language, independent of the device's system language.
 *
 * Persisted by NAME, never by ordinal -- inserting a value later would silently reassign
 * every stored row. See SettingsDataStore.
 */
enum class AppLanguage {
    SYSTEM,
    CS,
    EN
}
