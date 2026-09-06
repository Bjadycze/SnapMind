package com.app.snapmind.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.snapmind.data.local.dao.CapturedItemDao
import com.app.snapmind.data.local.entity.CapturedItemEntity
import com.app.snapmind.domain.model.CaptureSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * spec.md Task 2 definition of done: insert, dedup collision on mediaStoreId, and the
 * digest query ordering.
 */
@RunWith(AndroidJUnit4::class)
class CapturedItemDaoTest {

    private lateinit var db: SnapMindDatabase
    private lateinit var dao: CapturedItemDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SnapMindDatabase::class.java
        ).build()
        dao = db.capturedItemDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        mediaStoreId: Long? = null,
        timestamp: Long = 1_000L,
        remindersSent: Int = 0,
        resolvedAt: Long? = null,
        requiresReminder: Boolean = true,
        isProcessed: Boolean = true,
        note: String = ""
    ) = CapturedItemEntity(
        imageUri = "content://media/external/images/media/$mediaStoreId",
        mediaStoreId = mediaStoreId,
        extractedText = "",
        userNote = note,
        source = CaptureSource.SCREENSHOT,
        timestamp = timestamp,
        isProcessed = isProcessed,
        requiresReminder = requiresReminder,
        remindersSent = remindersSent,
        resolvedAt = resolvedAt
    )

    @Test
    fun insert_thenReadBack() = runTest {
        val id = dao.insert(item(mediaStoreId = 1L))
        assertEquals(1L, id)
        assertNotNull(dao.getById(id))
        assertEquals(1, dao.getAllFlow().first().size)
    }

    @Test
    fun duplicateMediaStoreId_isIgnored_andFirstRowSurvives() = runTest {
        dao.insert(item(mediaStoreId = 42L, note = "original"))
        val second = dao.insert(item(mediaStoreId = 42L, note = "duplicate"))

        assertEquals(-1L, second)
        assertEquals(1, dao.getAllFlow().first().size)
        assertEquals("original", dao.getByMediaStoreId(42L)?.userNote)
    }

    @Test
    fun nullMediaStoreId_doesNotCollide() = runTest {
        // Share Sheet items have no MediaStore id; SQLite treats NULLs as distinct.
        dao.insert(item(mediaStoreId = null, timestamp = 1L))
        dao.insert(item(mediaStoreId = null, timestamp = 2L))
        assertEquals(2, dao.getAllFlow().first().size)
    }

    @Test
    fun digestCandidates_areOldestFirst_andRespectTheLimit() = runTest {
        dao.insert(item(mediaStoreId = 1L, timestamp = 300L))
        dao.insert(item(mediaStoreId = 2L, timestamp = 100L))
        dao.insert(item(mediaStoreId = 3L, timestamp = 200L))
        dao.insert(item(mediaStoreId = 4L, timestamp = 400L))

        val result = dao.getDigestCandidates(limit = 3, maxAppearances = 3)

        assertEquals(3, result.size)
        assertEquals(listOf(100L, 200L, 300L), result.map { it.timestamp })
    }

    @Test
    fun digestCandidates_excludeResolvedAndCappedAndNonReminder() = runTest {
        dao.insert(item(mediaStoreId = 1L, timestamp = 100L, resolvedAt = 999L))
        dao.insert(item(mediaStoreId = 2L, timestamp = 200L, remindersSent = 3))
        dao.insert(item(mediaStoreId = 3L, timestamp = 300L, requiresReminder = false))
        dao.insert(item(mediaStoreId = 4L, timestamp = 400L))

        val result = dao.getDigestCandidates(limit = 3, maxAppearances = 3)

        assertEquals(1, result.size)
        assertEquals(400L, result.first().timestamp)
    }

    @Test
    fun incrementRemindersSent_movesItemOutOfPoolAfterThree() = runTest {
        val id = dao.insert(item(mediaStoreId = 1L))
        repeat(3) { dao.incrementRemindersSent(listOf(id)) }

        assertEquals(3, dao.getById(id)?.remindersSent)
        assertEquals(0, dao.getDigestCandidates(limit = 3, maxAppearances = 3).size)
    }

    @Test
    fun highestMediaStoreId_drivesCatchUpScan() = runTest {
        assertNull(dao.getHighestMediaStoreId())
        dao.insert(item(mediaStoreId = 10L))
        dao.insert(item(mediaStoreId = 7L))
        assertEquals(10L, dao.getHighestMediaStoreId())
    }

    @Test
    fun updateOcrResult_marksProcessed() = runTest {
        val id = dao.insert(item(mediaStoreId = 1L, isProcessed = false))
        assertEquals(1, dao.getUnprocessed().size)

        dao.updateOcrResult(id, "recognised text")

        assertEquals(0, dao.getUnprocessed().size)
        assertEquals("recognised text", dao.getById(id)?.extractedText)
    }

    @Test
    fun captureSource_survivesRoundTripByName() = runTest {
        val id = dao.insert(
            item(mediaStoreId = 1L).copy(source = CaptureSource.SHARE_SHEET)
        )
        assertEquals(CaptureSource.SHARE_SHEET, dao.getById(id)?.source)
    }
}
