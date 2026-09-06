package com.app.snapmind.service.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.app.snapmind.domain.repository.CapturedItemRepository
import com.app.snapmind.domain.service.QuickCapturePresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the RemoteInput payload. goAsync() keeps the process alive for the database write --
 * without it onReceive returns before the coroutine runs and the note is lost.
 */
@AndroidEntryPoint
class QuickCaptureReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: CapturedItemRepository
    @Inject lateinit var presenter: QuickCapturePresenter

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(NotificationQuickCapture.EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return

        val note = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationQuickCapture.KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (note.isNotEmpty()) {
                    repository.updateNote(itemId, note)
                }
                // Replying with a note is acting on the item: it leaves the digest pool as done.
                presenter.dismiss(itemId)
            } catch (e: Exception) {
                Log.e(TAG, "failed to store note for item $itemId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuickCaptureReply"
    }
}
