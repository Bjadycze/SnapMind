package com.app.snapmind.domain.model

/**
 * spec.md 7. These are product requirements, not tunables -- raising them re-creates
 * exactly the overwhelm the app exists to avoid.
 */
object ReminderPolicy {
    /** At most one notification per day, never one per item. */
    const val MAX_DIGESTS_PER_DAY = 1

    /** At most three items in a digest, oldest unresolved first. */
    const val MAX_ITEMS_PER_DIGEST = 3

    /** An item may appear in at most three digests, then it is archived silently. */
    const val MAX_APPEARANCES_PER_ITEM = 3

    /** Default delivery hour, user-configurable. */
    const val DEFAULT_REMINDER_HOUR = 18

    /** Catch-up scan cap after a Doze freeze, so a long freeze cannot produce a burst. */
    const val MAX_CATCH_UP_ITEMS = 3
}
