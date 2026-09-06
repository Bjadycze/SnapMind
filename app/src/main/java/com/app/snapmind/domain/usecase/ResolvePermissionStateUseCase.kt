package com.app.snapmind.domain.usecase

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.app.snapmind.domain.model.PermissionItem
import com.app.snapmind.domain.model.PermissionKey
import com.app.snapmind.domain.model.PermissionState
import com.app.snapmind.domain.model.PermissionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ResolvePermissionStateUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): PermissionState = PermissionState(
        listOf(
            PermissionItem(PermissionKey.NOTIFICATIONS, notificationStatus()),
            PermissionItem(PermissionKey.MEDIA_IMAGES, mediaStatus()),
            PermissionItem(PermissionKey.BATTERY_EXEMPTION, batteryStatus()),
            PermissionItem(PermissionKey.OEM_AUTOSTART, autostartStatus()),
            PermissionItem(PermissionKey.DND_EXCEPTION, dndStatus()),
            PermissionItem(PermissionKey.HIBERNATION_DISABLED, hibernationStatus())
        )
    )

    private fun notificationStatus(): PermissionStatus =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PermissionStatus.NOT_APPLICABLE
        } else {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        }

    /**
     * Partial photo access (API 34+) is treated as DENIED, not granted. Task 0 left this case
     * unresolved (spec.md 12.3), but a partial grant means the observer fires and resolves
     * nothing -- worse than an honest refusal, because it looks like it works.
     */
    private fun mediaStatus(): PermissionStatus = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        granted(Manifest.permission.READ_MEDIA_IMAGES) == PermissionStatus.GRANTED ->
            PermissionStatus.GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PermissionStatus.GRANTED ->
            PermissionStatus.DENIED
        else -> PermissionStatus.DENIED
    }

    private fun batteryStatus(): PermissionStatus {
        val pm = context.getSystemService(PowerManager::class.java)
            ?: return PermissionStatus.UNKNOWN
        return if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    /**
     * Not queryable on any OEM. Reported as UNKNOWN on manufacturers known to block
     * BOOT_COMPLETED, so the UI can show an instruction card the user confirms manually.
     */
    private fun autostartStatus(): PermissionStatus =
        if (isRestrictiveOem()) PermissionStatus.UNKNOWN else PermissionStatus.NOT_APPLICABLE

    private fun dndStatus(): PermissionStatus {
        val nm = context.getSystemService(NotificationManager::class.java)
            ?: return PermissionStatus.UNKNOWN
        return if (nm.isNotificationPolicyAccessGranted) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    /**
     * Whether the app is exempt from auto-revoke / hibernation.
     *
     * The API is a ListenableFuture, so this reads the cached last-known value rather than
     * blocking the UI thread; refresh() re-reads it on every onResume anyway.
     */
    private fun hibernationStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return PermissionStatus.NOT_APPLICABLE
        return try {
            val future = androidx.core.content.PackageManagerCompat
                .getUnusedAppRestrictionsStatus(context)
            if (!future.isDone) return PermissionStatus.UNKNOWN
            when (future.get()) {
                androidx.core.content.UnusedAppRestrictionsConstants.DISABLED,
                androidx.core.content.UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE ->
                    PermissionStatus.GRANTED
                androidx.core.content.UnusedAppRestrictionsConstants.ERROR ->
                    PermissionStatus.UNKNOWN
                else -> PermissionStatus.DENIED
            }
        } catch (e: Exception) {
            PermissionStatus.UNKNOWN
        }
    }

    private fun granted(permission: String): PermissionStatus =
        if (ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED

    companion object {
        private val RESTRICTIVE_OEMS = setOf(
            "honor", "huawei", "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "oneplus",
            "samsung", "meizu", "asus"
        )

        fun isRestrictiveOem(): Boolean =
            Build.MANUFACTURER.lowercase() in RESTRICTIVE_OEMS

        /** Used by the UI to name the right settings screen for this phone. */
        fun oemName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    }
}
