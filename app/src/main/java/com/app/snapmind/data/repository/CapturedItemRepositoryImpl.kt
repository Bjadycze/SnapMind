package com.app.snapmind.data.repository

import com.app.snapmind.data.local.dao.CapturedItemDao
import com.app.snapmind.data.local.entity.toDomain
import com.app.snapmind.data.local.entity.toEntity
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.ReminderPolicy
import com.app.snapmind.domain.repository.CapturedItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapturedItemRepositoryImpl @Inject constructor(
    private val dao: CapturedItemDao
) : CapturedItemRepository {

    override fun observeAll(): Flow<List<CapturedItem>> =
        dao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun save(item: CapturedItem): Long? =
        dao.insert(item.toEntity()).takeIf { it != -1L }

    override suspend fun markUnresolved(id: Long) = dao.markUnresolved(id)

    override suspend fun getById(id: Long): CapturedItem? = dao.getById(id)?.toDomain()

    override suspend fun getByMediaStoreId(mediaStoreId: Long): CapturedItem? =
        dao.getByMediaStoreId(mediaStoreId)?.toDomain()

    override suspend fun getHighestMediaStoreId(): Long? = dao.getHighestMediaStoreId()

    override suspend fun getDigestCandidates(limit: Int): List<CapturedItem> =
        dao.getDigestCandidates(
            limit = limit,
            maxAppearances = ReminderPolicy.MAX_APPEARANCES_PER_ITEM
        ).map { it.toDomain() }

    override suspend fun getUnprocessed(): List<CapturedItem> =
        dao.getUnprocessed().map { it.toDomain() }

    override suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    override suspend fun updateOcrResult(id: Long, text: String) = dao.updateOcrResult(id, text)

    override suspend fun updateImageUri(id: Long, uri: String) = dao.updateImageUri(id, uri)

    override suspend fun markDone(id: Long) =
        dao.markDone(id, System.currentTimeMillis())

    override suspend fun markDiscarded(id: Long) =
        dao.markDiscarded(id, System.currentTimeMillis())

    override suspend fun incrementRemindersSent(ids: List<Long>) =
        dao.incrementRemindersSent(ids)

    override suspend fun delete(item: CapturedItem) = dao.delete(item.toEntity())

    override suspend fun updateClassification(id: Long, category: String?, dateMillis: Long?)= dao.updateClassification(id, category, dateMillis)

    override suspend fun updateUserCategory(id: Long, category: String?) =
        dao.updateUserCategory(id, category)

    override fun search(query: String, limit: Int): Flow<List<CapturedItem>> =
        dao.search(query, limit).map { list -> list.map { it.toDomain() } }
}
