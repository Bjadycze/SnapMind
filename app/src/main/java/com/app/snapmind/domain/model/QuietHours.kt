package com.app.snapmind.domain.model

/**
 * A window during which the digest is never delivered. Wraps past midnight, so
 * 22:00 to 08:00 is a single quiet period rather than two.
 */
data class QuietHours(
    val startHour: Int,
    val endHour: Int
) {
    fun contains(hour: Int): Boolean = when {
        startHour == endHour -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }
}
