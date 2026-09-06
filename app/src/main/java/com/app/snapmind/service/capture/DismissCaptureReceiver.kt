package com.app.snapmind.service.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.app.snapmind.domain.repository.CapturedItemRepository
import com.app.snapmind.domain.service.QuickCapturePresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Discard": the user was only forwarding the screenshot and does not want it back.
 *
 * Without this the item stays in the digest pool and returns three more times, which is
 * exactly the nagging spec.md 7 exists to prevent. The row is kept, only resolved -- the
 * screenshot stays findable, it just stops asking for attention.
 */
@AndroidEntryPoint
class DismissCaptureReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: CapturedItemRepository
    @Inject lateinit var presenter: QuickCapturePresenter

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(NotificationQuickCapture.EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return

        // goAsync keeps the process alive for the write; onReceive would otherwise return first.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.markDiscarded(itemId)
                presenter.dismiss(itemId)
            } catch (e: Exception) {
                Log.e(TAG, "failed to discard item $itemId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DismissCapture"
    }
}
