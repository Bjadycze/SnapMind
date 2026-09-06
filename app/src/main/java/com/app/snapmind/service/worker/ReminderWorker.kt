package com.app.snapmind.service.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.snapmind.R
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.domain.model.QuietHours
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.usecase.BuildReminderDigestUseCase
import com.app.snapmind.presentation.main.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * The anti-overwhelm engine (spec.md 7).
 *
 * One notification per day, at most three items, each item at most three times ever. There is
 * no badge, no count, no streak, and no "all caught up" message -- see spec.md 7.3 before
 * adding anything that looks like progress tracking.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val buildDigest: BuildReminderDigestUseCase,
    private val settings: SettingsDataStore,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Chain the next run first, so a crash below cannot break the daily cycle.
        scheduler.schedule(settings.reminderHour.first(), replaceExisting = true)

        val quiet = QuietHours(
            startHour = settings.quietStartHour.first(),
            endHour = settings.quietEndHour.first()
        )
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // WorkManager batches deferred work, so a run can land inside quiet hours even though
        // it was scheduled outside them. Skipping is correct: the next daily run picks it up.
        if (quiet.contains(hour)) return Result.success()

        val items = buildDigest()
        if (items.isEmpty()) return Result.success()

        ensureChannel()

        // A bare count ("3 items are waiting") tells the user nothing and makes the digest
        // feel like a backlog. Naming the items is the difference between a reminder and a
        // number to feel bad about.
        val lines = items.map(::describe)
        val body = lines.joinToString(" · ")

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(context.getString(R.string.digest_title))
            .setContentText(body)
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    lines.forEach { style.addLine(it) }
                }
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        val posted = runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        }.isSuccess

        // Only count an appearance if the notification actually went out. Otherwise a denied
        // permission would silently burn through an item's three chances.
        if (posted) buildDigest.markDelivered(items)

        return Result.success()
    }

    /**
     * Note first, then recognised text, then a plain fallback. Kept short: the digest is a
     * nudge, not a reading task.
     */
    private fun describe(item: CapturedItem): String {
        val note = item.userNote.trim()
        if (note.isNotEmpty()) return note.take(MAX_LINE_CHARS)

        val text = item.extractedText.trim().replace(Regex("\\s+"), " ")
        if (text.isNotEmpty()) return text.take(MAX_LINE_CHARS)

        return context.getString(R.string.digest_item_untitled)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_reminder_desc)
                // spec.md 7.3: no badges anywhere in this app.
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val NAME = "snapmind_daily_reminder"
        private const val CHANNEL_ID = "snapmind_reminder"
        private const val NOTIF_ID = 4300
        private const val MAX_LINE_CHARS = 40
    }
}
