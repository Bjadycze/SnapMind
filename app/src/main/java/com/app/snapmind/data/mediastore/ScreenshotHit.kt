package com.app.snapmind.data.mediastore

data class ScreenshotHit(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val dateAddedMillis: Long
) {
    val ageMillis: Long get() = System.currentTimeMillis() - dateAddedMillis
}
