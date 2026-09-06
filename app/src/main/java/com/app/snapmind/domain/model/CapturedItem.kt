package com.app.snapmind.domain.model

/**
 * Domain representation of one capture. Framework-free by design: the URI stays a String
 * here and is converted at the data/presentation boundary.
 */
data class CapturedItem(
    val id: Long = 0,
    val imageUri: String?,
    val mediaStoreId: Long?,
    val extractedText: String,
    val userNote: String,
    val source: CaptureSource,
    val timestamp: Long,
    val isProcessed: Boolean,
    val requiresReminder: Boolean,
    val remindersSent: Int = 0,
    val resolvedAt: Long? = null,
    val resolution: Resolution? = null,
    val detectedCategory: String? = null,
    val detectedDateMillis: Long? = null,
    /**
     * The category the user picked by hand -- a DetectedCategory name, a custom name of their
     * own, or null when they never touched it (spec.md 11.16). Kept apart from
     * detectedCategory so "the classifier was wrong" stays distinguishable from "the
     * classifier had no idea".
     */
    val userCategory: String? = null
) {
    /** What the UI shows and filters by: the manual choice wins, the guess is the fallback. */
    val effectiveCategory: String? get() = userCategory ?: detectedCategory

    /** An item is out of the digest pool once the user has acted on it. */
    val isResolved: Boolean get() = resolvedAt != null

    /** spec.md 7.2: three appearances maximum, then it moves to the quiet archive. */
    val isArchived: Boolean get() = remindersSent >= MAX_REMINDERS

    companion object {
        const val MAX_REMINDERS = 3
    }
}
