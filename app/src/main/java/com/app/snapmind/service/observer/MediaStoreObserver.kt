package com.app.snapmind.service.observer

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Task 0: one screenshot produces exactly three fires on MagicOS 9 (insert, then metadata
 * updates). Debouncing here means the service only ever sees one event per screenshot.
 */
class MediaStoreObserver(
    private val onSettled: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { onSettled() }
        pending = task
        handler.postDelayed(task, DEBOUNCE_MS)
    }

    fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    companion object {
        /** Long enough for the file write to settle, short enough to stay under the 3 s target. */
        private const val DEBOUNCE_MS = 300L
    }
}
