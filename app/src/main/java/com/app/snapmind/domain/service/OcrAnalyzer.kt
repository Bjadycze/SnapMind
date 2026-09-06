package com.app.snapmind.domain.service

interface OcrAnalyzer {
    /** Extracts text from the given image URI. Returns an empty string on failure. */
    suspend fun analyzeText(uri: String): String
}
