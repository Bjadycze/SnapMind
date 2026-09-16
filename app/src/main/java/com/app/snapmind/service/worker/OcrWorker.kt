package com.app.snapmind.service.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.snapmind.data.prefs.AppLanguagePrefs
import com.app.snapmind.domain.classify.ContentClassifier
import com.app.snapmind.domain.repository.CapturedItemRepository
import com.app.snapmind.domain.service.OcrAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs recognition off the capture path. Processes every unprocessed item, so an item whose
 * OCR was interrupted by a process kill is picked up on the next run.
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CapturedItemRepository,
    private val ocr: OcrAnalyzer,
    private val classifier: ContentClassifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // No Activity here, so the choice comes from the synchronous SharedPreferences mirror,
        // not DataStore (data/prefs/AppLanguagePrefs.kt).
        val language = AppLanguagePrefs.get(applicationContext)

        val pending = repository.getUnprocessed()
        for (item in pending) {
            val uri = item.imageUri
            // Text-only share has nothing to recognise, but must not stay pending forever.
            val text = if (uri == null) "" else runCatching { ocr.analyzeText(uri) }.getOrDefault("")
            repository.updateOcrResult(item.id, text)

            val result = classifier.classify("$text\n${item.userNote}", language = language)
            repository.updateClassification(item.id, result.category.name, result.dateMillis)
        }
        return Result.success()
    }

    companion object {
        const val NAME = "snapmind_ocr"
    }
}
