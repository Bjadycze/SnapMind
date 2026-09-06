package com.app.snapmind.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Deep links into the system settings screens that cannot be requested inline.
 * Every one falls back to the app's own settings page, because OEM ROMs occasionally
 * ship without the activity these intents target.
 */
object PermissionIntents {

    /**
     * Opens the battery optimisation list. Deliberately NOT
     * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: that one is restricted by Play policy
     * and can get an app rejected (spec.md 5.3).
     */
    fun batteryOptimisation(context: Context) = launch(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )

    fun dndPolicyAccess(context: Context) = launch(
        context,
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    )

    /**
     * Opens the screen where hibernation can be switched off. On API 31+ the system provides a
     * dedicated intent; older versions land on app settings.
     */
    fun hibernation(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val intent = androidx.core.content.IntentCompat
                .createManageUnusedAppRestrictionsIntent(context, context.packageName)
            launch(context, intent)
        } else {
            appSettings(context)
        }
    }

    /** No intent exists for OEM autostart, so this opens app settings as the closest hop. */
    fun appSettings(context: Context) = launch(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    )

    private fun launch(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
