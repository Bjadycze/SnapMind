package com.app.snapmind.service.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.service.QuickCapturePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only capture path in v1 (spec.md 11.1).
 *
 * Two constraints measured in Task 0 shape this class:
 *  - The reply action is hidden until the notification is expanded, which costs the
 *    sub-3-second target. setStyle(BigPicture) at least makes expansion one gesture.
 *  - Do Not Disturb suppresses the heads-up entirely. The post still succeeds and the reply
 *    still works, so we detect DND and report it rather than failing silently.
 */
@Singleton
class NotificationQuickCapture @Inject constructor(
    @ApplicationContext private val context: Context
) : QuickCapturePresenter {

    override suspend fun present(itemId: Long, imageUri: String?, source: CaptureSource) {
        ensureChannel()

        val replyIntent = Intent(context, QuickCaptureReplyReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val replyPending = PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            context.getString(com.app.snapmind.R.string.capture_action_note),
            replyPending
        )
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).build())
            .setAllowGeneratedReplies(false)
            .build()

        val discardIntent = Intent(context, DismissCaptureReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val discardPending = PendingIntent.getBroadcast(
            context,
            // Distinct request code: sharing one with the reply action would overwrite it.
            -itemId.toInt(),
            discardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val discardAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(com.app.snapmind.R.string.capture_action_discard),
            discardPending
        ).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(context.getString(com.app.snapmind.R.string.capture_title))
            .setContentText(context.getString(com.app.snapmind.R.string.capture_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(replyAction)
            .addAction(discardAction)

        loadPreview(imageUri)?.let { bmp ->
            builder.setLargeIcon(bmp)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bmp)
                    .bigLargeIcon(null as Bitmap?)
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(itemId.toInt(), builder.build())
        }
    }

    override fun dismiss(itemId: Long) {
        NotificationManagerCompat.from(context).cancel(itemId.toInt())
    }

    /**
     * True when Do Not Disturb will suppress the heads-up. Onboarding uses this to offer the
     * policy-access exception rather than letting captures land silently (spec.md R7).
     */
    fun isSuppressedByDnd(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(com.app.snapmind.R.string.channel_capture_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.app.snapmind.R.string.channel_capture_desc)
            // No badges anywhere in this app -- spec.md 7.3.
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Downscaled hard: a full-res bigPicture will hit the 1 MB binder limit. */
    private suspend fun loadPreview(uri: String?): Bitmap? {
        if (uri == null) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    BitmapFactory.decodeStream(
                        input, null, BitmapFactory.Options().apply { inSampleSize = 4 }
                    )
                }
            }.getOrNull()
        }
    }

    companion object {
        const val CHANNEL_ID = "quick_capture"
        const val KEY_REPLY = "snapmind_note_reply"
        const val EXTRA_ITEM_ID = "snapmind_item_id"
    }
}
