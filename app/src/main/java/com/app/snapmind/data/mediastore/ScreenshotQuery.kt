package com.app.snapmind.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All MediaStore access lives here. Every constant in this file came out of Task 0 measurement,
 * not from guesswork -- see spec.md 6.2 before changing any of them.
 */
@Singleton
class ScreenshotQuery @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** The most recent screenshot, or null. Freshness is the caller's decision. */
    fun latest(): ScreenshotHit? = query(newerThanId = null).firstOrNull()

    /**
     * Catch-up scan: everything newer than the highest id already stored, oldest first,
     * capped so a long Doze freeze cannot produce a burst of prompts (spec.md 7).
     */
    fun newerThan(mediaStoreId: Long, limit: Int): List<ScreenshotHit> =
        query(newerThanId = mediaStoreId).take(limit).sortedBy { it.mediaStoreId }

    private fun query(newerThanId: Long?): List<ScreenshotHit> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            pathColumn
        )

        // Samsung stores screenshots under DCIM/Screenshots, Honor/Huawei under
        // Pictures/Screenshots. LIKE covers both; Task 0 confirmed Pictures/ on MagicOS 9.
        val selection = StringBuilder("$pathColumn LIKE ?")
        val args = mutableListOf("%Screenshots%")
        if (newerThanId != null) {
            selection.append(" AND ${MediaStore.Images.Media._ID} > ?")
            args.add(newerThanId.toString())
        }

        // No LIMIT in sortOrder: that syntax is silently ignored on API 30+.
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return try {
            context.contentResolver.query(
                collection, projection, selection.toString(), args.toTypedArray(), sort
            ).use { cursor ->
                if (cursor == null) {
                    Log.w(TAG, "cursor was null -- media permission missing?")
                    return emptyList()
                }
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(pathColumn)

                buildList {
                    while (cursor.moveToNext() && size < MAX_ROWS_SCANNED) {
                        val id = cursor.getLong(idCol)
                        add(
                            ScreenshotHit(
                                mediaStoreId = id,
                                uri = ContentUris.withAppendedId(collection, id).toString(),
                                displayName = cursor.getString(nameCol) ?: "",
                                relativePath = cursor.getString(pathCol) ?: "",
                                dateAddedMillis = cursor.getLong(dateCol) * 1000L
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "media access denied", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ScreenshotQuery"

        /**
         * 120 s, not 10 s. Task 0 measured MediaStore write lag reaching 99 s while the process
         * was frozen by Doze, which discarded a real screenshot as stale.
         */
        const val STALE_THRESHOLD_MS = 120_000L

        /** Guard against scanning a huge gallery when the catch-up window is wide. */
        private const val MAX_ROWS_SCANNED = 50
    }
}
