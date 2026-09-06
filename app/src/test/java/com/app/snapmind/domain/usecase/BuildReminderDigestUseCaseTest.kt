package com.app.snapmind.domain.usecase

import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.model.ReminderPolicy
import com.app.snapmind.domain.repository.CapturedItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * spec.md Task 5 definition of done: max three items, max three appearances per item,
 * silence when the pool is empty, and exclusion of resolved items.
 *
 * These are product rules. A failure here is not a bug in a helper -- it means the app would
 * start nagging (spec.md 7.3).
 */
class BuildReminderDigestUseCaseTest {

    private class FakeRepository(
        private var items: MutableList<CapturedItem> = mutableListOf()
    ) : CapturedItemRepository {

        val incremented = mutableListOf<Long>()

        fun seed(vararg new: CapturedItem) {
            items = new.toMutableList()
        }

        override suspend fun getDigestCandidates(limit: Int): List<CapturedItem> =
            items
                .filter { !it.isResolved }
                .filter { it.requiresReminder }
                .filter { it.remindersSent < ReminderPolicy.MAX_APPEARANCES_PER_ITEM }
                .sortedBy { it.timestamp }
                .take(limit)

        override suspend fun incrementRemindersSent(ids: List<Long>) {
            incremented.addAll(ids)
            items = items.map {
                if (it.id in ids) it.copy(remindersSent = it.remindersSent + 1) else it
            }.toMutableList()
        }

        override fun observeAll(): Flow<List<CapturedItem>> = flowOf(items)
        override suspend fun save(item: CapturedItem): Long? = null
        override suspend fun getById(id: Long): CapturedItem? = items.find { it.id == id }
        override suspend fun getByMediaStoreId(mediaStoreId: Long): CapturedItem? = null
        override suspend fun getHighestMediaStoreId(): Long? = null
        override suspend fun getUnprocessed(): List<CapturedItem> = emptyList()
        override suspend fun updateNote(id: Long, note: String) = Unit
        override suspend fun updateOcrResult(id: Long, text: String) = Unit
        override suspend fun markDone(id: Long) = Unit
        override suspend fun markUnresolved(id: Long) = Unit
        override suspend fun markDiscarded(id: Long) = Unit
        override fun search(query: String, limit: Int): Flow<List<CapturedItem>> = flowOf(emptyList())
        override suspend fun delete(item: CapturedItem) = Unit
        override suspend fun updateImageUri(id: Long, uri: String) {}
        override suspend fun updateClassification(id: Long, category: String?, dateMillis: Long?) {}
    }

    private fun item(
        id: Long,
        timestamp: Long,
        remindersSent: Int = 0,
        resolvedAt: Long? = null,
        requiresReminder: Boolean = true
    ) = CapturedItem(
        id = id,
        imageUri = null,
        mediaStoreId = null,
        extractedText = "",
        userNote = "",
        source = CaptureSource.SCREENSHOT,
        timestamp = timestamp,
        isProcessed = true,
        requiresReminder = requiresReminder,
        remindersSent = remindersSent,
        resolvedAt = resolvedAt
    )

    @Test
    fun `empty pool produces no digest`() = runTest {
        val repo = FakeRepository()
        assertTrue(BuildReminderDigestUseCase(repo)().isEmpty())
    }

    @Test
    fun `digest never exceeds three items`() = runTest {
        val repo = FakeRepository()
        repo.seed(
            item(1, 100), item(2, 200), item(3, 300), item(4, 400), item(5, 500)
        )
        assertEquals(3, BuildReminderDigestUseCase(repo)().size)
    }

    @Test
    fun `digest is oldest first`() = runTest {
        val repo = FakeRepository()
        repo.seed(item(1, 300), item(2, 100), item(3, 200))
        val result = BuildReminderDigestUseCase(repo)()
        assertEquals(listOf(100L, 200L, 300L), result.map { it.timestamp })
    }

    @Test
    fun `resolved items are excluded`() = runTest {
        val repo = FakeRepository()
        repo.seed(item(1, 100, resolvedAt = 999L), item(2, 200))
        val result = BuildReminderDigestUseCase(repo)()
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `items not flagged for reminder are excluded`() = runTest {
        val repo = FakeRepository()
        repo.seed(item(1, 100, requiresReminder = false), item(2, 200))
        assertEquals(listOf(2L), BuildReminderDigestUseCase(repo)().map { it.id })
    }

    @Test
    fun `an item drops out after three appearances`() = runTest {
        val repo = FakeRepository()
        repo.seed(item(1, 100))
        val useCase = BuildReminderDigestUseCase(repo)

        repeat(ReminderPolicy.MAX_APPEARANCES_PER_ITEM) {
            val digest = useCase()
            assertEquals(1, digest.size)
            useCase.markDelivered(digest)
        }

        assertTrue("item should be archived after three digests", useCase().isEmpty())
    }

    @Test
    fun `markDelivered on an empty list touches nothing`() = runTest {
        val repo = FakeRepository()
        BuildReminderDigestUseCase(repo).markDelivered(emptyList())
        assertTrue(repo.incremented.isEmpty())
    }
}
