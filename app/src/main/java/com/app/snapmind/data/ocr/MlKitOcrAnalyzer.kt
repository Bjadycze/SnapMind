package com.app.snapmind.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.app.snapmind.domain.service.OcrAnalyzer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MlKitOcrAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrAnalyzer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun analyzeText(uri: String): String = withContext(Dispatchers.IO) {
        val bitmap = decodeDownscaled(uri) ?: return@withContext ""

        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener {
                    Log.w(TAG, "recognition failed for $uri", it)
                    cont.resume("")
                }
        }
    }

    /**
     * Full-resolution screenshots are slow to recognise and offer no accuracy benefit,
     * so anything wider than ~2000 px is halved until it fits (spec.md 6.5).
     */
    private fun decodeDownscaled(uri: String): android.graphics.Bitmap? = try {
        val parsed = Uri.parse(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sample = 1
        while (bounds.outWidth / sample > MAX_WIDTH_PX) sample *= 2

        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not decode $uri", e)
        null
    }

    companion object {
        private const val TAG = "MlKitOcrAnalyzer"
        private const val MAX_WIDTH_PX = 2000
    }
}
