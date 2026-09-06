package com.app.snapmind.domain.usecase

import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.repository.CapturedItemRepository
import javax.inject.Inject

/**
 * Saves a capture and returns its row id, or null if it was a duplicate.
 *
 * OCR deliberately does NOT run here. The item is stored with isProcessed = false and
 * recognition happens later in OcrWorker, so the user never waits on ML Kit to write a note
 * (spec.md 6.5).
 */
class ProcessCapturedImageUseCase @Inject constructor(
    private val repository: CapturedItemRepository
) {
    suspend operator fun invoke(
        imageUri: String?,
        mediaStoreId: Long?,
        source: CaptureSource,
        timestamp: Long = System.currentTimeMillis()
    ): Long? {
        // Cheap pre-check. The unique index on mediaStoreId is the real guarantee; the
        // ContentObserver fires three times per screenshot and will occasionally race.
        if (mediaStoreId != null && repository.getByMediaStoreId(mediaStoreId) != null) return null

        return repository.save(
            CapturedItem(
                imageUri = imageUri,
                mediaStoreId = mediaStoreId,
                extractedText = "",
                userNote = "",
                source = source,
                timestamp = timestamp,
                isProcessed = false,
                requiresReminder = true
            )
        )
    }
}
