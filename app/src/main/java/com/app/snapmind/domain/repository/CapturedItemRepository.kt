package com.app.snapmind.domain.repository

import com.app.snapmind.domain.model.CapturedItem
import kotlinx.coroutines.flow.Flow

interface CapturedItemRepository {

    fun observeAll(): Flow<List<CapturedItem>>

    /** Returns the new row id, or null when the item was a duplicate and was ignored. */
    suspend fun save(item: CapturedItem): Long?

    suspend fun getById(id: Long): CapturedItem?

    suspend fun getByMediaStoreId(mediaStoreId: Long): CapturedItem?

    /** Highest MediaStore id seen so far; the catch-up scan starts from here. */
    suspend fun getHighestMediaStoreId(): Long?

    /** Unresolved items under the appearance cap, oldest first. */
    suspend fun getDigestCandidates(limit: Int): List<CapturedItem>

    suspend fun getUnprocessed(): List<CapturedItem>

    suspend fun updateNote(id: Long, note: String)

    suspend fun updateOcrResult(id: Long, text: String)

    suspend fun updateImageUri(id: Long, uri: String)

    /** Fajfka: the only resolution path that earns a reward (spec.md 11.8). */
    suspend fun markDone(id: Long)

    /** Koš: same list effect as markDone, deliberately no reward. */
    suspend fun markDiscarded(id: Long)

    /** Undo a discard. remindersSent is left alone: the item resumes where it was. */
    suspend fun markUnresolved(id: Long)

    suspend fun incrementRemindersSent(ids: List<Long>)

    suspend fun delete(item: CapturedItem)

    suspend fun updateClassification(id: Long, category: String?, dateMillis: Long?)

    /** The category the user set by hand; null clears it back to the classifier's guess. */
    suspend fun updateUserCategory(id: Long, category: String?)
    /**
     * Matches `userNote` or `extractedText`, archived items included -- spec.md 11.10/7.2.
     * An empty query must not be sent here as a wildcard match-everything; guard it at the
     * call site instead.
     */
    fun search(query: String, limit: Int): Flow<List<CapturedItem>>
}
