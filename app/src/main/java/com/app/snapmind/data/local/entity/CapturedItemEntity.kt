package com.app.snapmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.model.Resolution

/**
 * The unique index on mediaStoreId makes screenshot de-duplication a database guarantee
 * rather than application logic. Task 0 measured three ContentObserver fires per screenshot,
 * so the in-memory guard will occasionally lose a race; this is the backstop.
 */
@Entity(
    tableName = "captured_items",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["resolvedAt", "remindersSent", "timestamp"])
    ]
)
data class CapturedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val userCategory: String? = null
)

fun CapturedItemEntity.toDomain() = CapturedItem(
    id = id,
    imageUri = imageUri,
    mediaStoreId = mediaStoreId,
    extractedText = extractedText,
    userNote = userNote,
    source = source,
    timestamp = timestamp,
    isProcessed = isProcessed,
    requiresReminder = requiresReminder,
    remindersSent = remindersSent,
    resolvedAt = resolvedAt,
    resolution = resolution,
    detectedCategory = detectedCategory,
    detectedDateMillis = detectedDateMillis,
    userCategory = userCategory
)

fun CapturedItem.toEntity() = CapturedItemEntity(
    id = id,
    imageUri = imageUri,
    mediaStoreId = mediaStoreId,
    extractedText = extractedText,
    userNote = userNote,
    source = source,
    timestamp = timestamp,
    isProcessed = isProcessed,
    requiresReminder = requiresReminder,
    remindersSent = remindersSent,
    resolvedAt = resolvedAt,
    resolution = resolution,
    detectedCategory = detectedCategory,
    detectedDateMillis = detectedDateMillis,
    userCategory = userCategory
)
