package com.app.snapmind.domain.model

/**
 * Everything the capture pipeline needs in order to work, and whether it is currently granted.
 *
 * Task 3 field finding: reinstalling the app silently reset every runtime permission to denied
 * and nothing in the UI said so. That is why this is re-checked on every onResume rather than
 * only during onboarding (spec.md 5.3).
 */
enum class PermissionKey {
    /** POST_NOTIFICATIONS. Without it the capture prompt cannot appear at all. */
    NOTIFICATIONS,

    /** READ_MEDIA_IMAGES. Without it the observer fires but resolves nothing. */
    MEDIA_IMAGES,

    /** Battery optimisation exemption. Measured as the difference between usable and not. */
    BATTERY_EXEMPTION,

    /**
     * OEM autostart (Honor/Huawei "App launch"). Cannot be queried or requested.
     * Task 3 measured that without it BOOT_COMPLETED never reaches the app.
     */
    OEM_AUTOSTART,

    /** Do Not Disturb policy access. Optional: without it captures land silently under DND. */
    DND_EXCEPTION,

    /**
     * App hibernation disabled. Critical for this app specifically (spec.md R8): SnapMind is
     * designed not to be opened, so the system will eventually classify it as abandoned and
     * revoke everything. Every other permission depends on this one staying off.
     */
    HIBERNATION_DISABLED
}

enum class PermissionStatus {
    GRANTED,
    DENIED,

    /** Denied twice, or denied with "don't ask again": only Settings can fix it now. */
    PERMANENTLY_DENIED,

    /** Not applicable on this API level or manufacturer. */
    NOT_APPLICABLE,

    /** Cannot be determined programmatically -- the user has to confirm it themselves. */
    UNKNOWN
}

data class PermissionItem(
    val key: PermissionKey,
    val status: PermissionStatus
) {
    val isSatisfied: Boolean
        get() = status == PermissionStatus.GRANTED || status == PermissionStatus.NOT_APPLICABLE
}

data class PermissionState(
    val items: List<PermissionItem>
) {
    fun statusOf(key: PermissionKey): PermissionStatus =
        items.firstOrNull { it.key == key }?.status ?: PermissionStatus.UNKNOWN

    /**
     * The two that capture cannot work without. Everything else degrades gracefully:
     * the app stays usable via the Share Sheet even if the user declines them.
     */
    val canCapture: Boolean
        get() = statusOf(PermissionKey.NOTIFICATIONS).let {
            it == PermissionStatus.GRANTED || it == PermissionStatus.NOT_APPLICABLE
        } && statusOf(PermissionKey.MEDIA_IMAGES) == PermissionStatus.GRANTED

    /** Shown as a banner on the main screen, never as a blocking dialog. */
    val missingCritical: List<PermissionKey>
        get() = items
            .filter { !it.isSatisfied }
            .map { it.key }
            .filter { it == PermissionKey.NOTIFICATIONS || it == PermissionKey.MEDIA_IMAGES }
}
