package com.app.snapmind.domain.usecase

import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.ReminderPolicy
import com.app.snapmind.domain.repository.CapturedItemRepository
import javax.inject.Inject

/**
 * Decides what, if anything, today's single reminder contains.
 *
 * The caps in ReminderPolicy are product requirements, not tunables (spec.md 7). Raising them
 * re-creates exactly the overwhelm this engine exists to prevent, so they are enforced here
 * rather than left to the caller.
 */
class BuildReminderDigestUseCase @Inject constructor(
    private val repository: CapturedItemRepository
) {

    /**
     * Returns the items to mention, oldest unresolved first. An empty list means send nothing:
     * there is deliberately no "you're all caught up" notification.
     */
    suspend operator fun invoke(): List<CapturedItem> =
        repository.getDigestCandidates(ReminderPolicy.MAX_ITEMS_PER_DIGEST)

    /**
     * Called after the notification is posted. Items that reach the appearance cap drop out of
     * the pool permanently and move to the quiet archive -- they are never deleted, and the
     * user is never told a count.
     */
    suspend fun markDelivered(items: List<CapturedItem>) {
        if (items.isEmpty()) return
        repository.incrementRemindersSent(items.map { it.id })
    }
}
