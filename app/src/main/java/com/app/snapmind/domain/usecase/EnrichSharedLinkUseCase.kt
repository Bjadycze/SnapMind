package com.app.snapmind.domain.usecase

import com.app.snapmind.data.link.LinkMetadataFetcher
import com.app.snapmind.domain.classify.ContentClassifier
import com.app.snapmind.domain.repository.CapturedItemRepository
import javax.inject.Inject

/**
 * Fills in a shared link's title and thumbnail after the fact.
 *
 * Runs after the item is already saved, so a slow or blocked request never delays the capture.
 * When it fails the item simply stays as it was — no error surfaces, because there is nothing
 * the user could do about Instagram refusing a logged-out request.
 */
class EnrichSharedLinkUseCase @Inject constructor(
    private val repository: CapturedItemRepository,
    private val fetcher: LinkMetadataFetcher,
    private val classifier: ContentClassifier
) {
    suspend operator fun invoke(itemId: Long, sharedText: String) {
        val url = extractUrl(sharedText) ?: return
        val meta = fetcher.fetch(url) ?: return

        val item = repository.getById(itemId) ?: return

        // The note holds the title only while the user has not written their own.
        meta.title?.let { title ->
            if (item.userNote == sharedText.trim() || item.userNote.isBlank()) {
                repository.updateNote(itemId, title)
            }
        }

        // The URL lives in extractedText: it is search material, same as OCR output, and this
        // avoids a schema migration for one field.
        repository.updateOcrResult(itemId, url)

        meta.imageUrl?.let { image ->
            if (item.imageUri == null) repository.updateImageUri(itemId, image)
        }

        // Classification has to happen here, not in OcrWorker: updateOcrResult above sets
        // isProcessed = 1, and the worker only ever looks at unprocessed rows, so a shared link
        // would otherwise never get a category at all (spec.md 11.16).
        //
        // Only detectedCategory is touched. A category the user set by hand lives in
        // userCategory and wins over this (CapturedItem.effectiveCategory), so re-sharing the
        // same link can never undo their choice.
        val text = listOfNotNull(meta.title, item.userNote.takeIf { it.isNotBlank() }, url)
            .joinToString("\n")
        val result = classifier.classify(text)
        repository.updateClassification(itemId, result.category.name, result.dateMillis)
    }

    private fun extractUrl(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')')
}