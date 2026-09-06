package com.app.snapmind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.snapmind.data.local.entity.CapturedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedItemDao {

    /**
     * IGNORE, not REPLACE: a duplicate mediaStoreId means the same screenshot arrived twice
     * and the first row is the one with the user's note on it. Returns -1 when ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CapturedItemEntity): Long

    @Update
    suspend fun update(item: CapturedItemEntity)

    @Delete
    suspend fun delete(item: CapturedItemEntity)

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<CapturedItemEntity>>

    @Query("SELECT * FROM captured_items WHERE id = :id")
    suspend fun getById(id: Long): CapturedItemEntity?

    @Query("SELECT * FROM captured_items WHERE mediaStoreId = :mediaStoreId")
    suspend fun getByMediaStoreId(mediaStoreId: Long): CapturedItemEntity?

    /** Highest MediaStore id already stored, used by the catch-up scan (spec.md 6.2). */
    @Query("SELECT MAX(mediaStoreId) FROM captured_items")
    suspend fun getHighestMediaStoreId(): Long?

    /**
     * The digest pool: unresolved, still under the appearance cap, oldest first.
     * spec.md 7.1 caps the caller at three; the limit is passed in rather than hardcoded
     * so the rule lives in one place (ReminderPolicy).
     */
    @Query(
        """
        SELECT * FROM captured_items
        WHERE resolvedAt IS NULL
          AND requiresReminder = 1
          AND remindersSent < :maxAppearances
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun getDigestCandidates(limit: Int, maxAppearances: Int): List<CapturedItemEntity>

    /** Items that never finished OCR, e.g. because the process was killed mid-run. */
    @Query("SELECT * FROM captured_items WHERE isProcessed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessed(): List<CapturedItemEntity>

    @Query("UPDATE captured_items SET remindersSent = remindersSent + 1 WHERE id IN (:ids)")
    suspend fun incrementRemindersSent(ids: List<Long>)

    /** Fajfka: the user handled it. The only resolution path that earns a reward (spec.md 11.8). */
    @Query("UPDATE captured_items SET resolvedAt = :now, resolution = 'DONE' WHERE id = :id")
    suspend fun markDone(id: Long, now: Long)

    /** Koš: the user does not want it back. Same list effect as markDone, no reward. */
    @Query("UPDATE captured_items SET resolvedAt = :now, resolution = 'DISCARDED' WHERE id = :id")
    suspend fun markDiscarded(id: Long, now: Long)

    /** Undo for a mistaken discard: the item returns to the digest pool, resolution forgotten. */
    @Query("UPDATE captured_items SET resolvedAt = NULL, resolution = NULL WHERE id = :id")
    suspend fun markUnresolved(id: Long)

    @Query("UPDATE captured_items SET userNote = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("UPDATE captured_items SET extractedText = :text, isProcessed = 1 WHERE id = :id")
    suspend fun updateOcrResult(id: Long, text: String)

    @Query("UPDATE captured_items SET imageUri = :uri WHERE id = :id")
    suspend fun updateImageUri(id: Long, uri: String)
    @Query("UPDATE captured_items SET detectedCategory = :category, detectedDateMillis = :dateMillis WHERE id = :id")
    suspend fun updateClassification(id: Long, category: String?, dateMillis: Long?)
    /**
     * Matches `userNote` or `extractedText` -- the latter holds both OCR output and shared
     * URLs, so one query covers a screenshot's caption and a shared link's title alike. No
     * `isArchived` / `remindersSent` filter: the quiet archive (spec.md 7.2) must stay
     * reachable by search, that is the entire point of it not being a dead end.
     */
    @Query(
        """
        SELECT * FROM captured_items
        WHERE userNote LIKE '%' || :query || '%'
           OR extractedText LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )

    fun search(query: String, limit: Int): Flow<List<CapturedItemEntity>>
}
