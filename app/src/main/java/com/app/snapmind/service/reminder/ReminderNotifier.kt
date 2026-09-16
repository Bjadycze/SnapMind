package com.app.snapmind.service.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app.snapmind.R
import com.app.snapmind.data.prefs.AppLanguagePrefs
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.QuietHours
import com.app.snapmind.domain.usecase.BuildReminderDigestUseCase
import com.app.snapmind.presentation.locale.withAppLocale
import com.app.snapmind.presentation.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts the daily digest (spec.md 7).
 *
 * One notification per day, at most three items, each item at most three times ever. No badge,
 * no count, no streak, no "all caught up" -- read spec.md 7.3 before adding anything that
 * resembles progress tracking.
 *
 * Lives apart from whatever triggers it so the scheduling mechanism can change without
 * touching the product rules. It has changed twice already.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buildDigest: BuildReminderDigestUseCase,
    private val settings: SettingsDataStore
) {

    /** Runs outside any Activity, so the language comes from the synchronous prefs mirror. */
    private val localizedContext: Context
        get() = context.withAppLocale(AppLanguagePrefs.get(context))

    suspend fun deliverIfDue() {
        val quiet = QuietHours(
            startHour = settings.quietStartHour.first(),
            endHour = settings.quietEndHour.first()
        )
        // An alarm can still land inside quiet hours if the phone was asleep past the window.
        if (quiet.contains(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) return

        val items = buildDigest()
        if (items.isEmpty()) return

        ensureChannel()

        // A bare count ("3 items are waiting") makes the digest feel like a backlog. Naming
        // the items is the difference between a reminder and a number to feel bad about.
        val lines = items.map(::describe)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(localizedContext.getString(R.string.digest_title))
            .setContentText(lines.joinToString(" · "))
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

        // Only count an appearance if the notification actually went out, otherwise a denied
        // permission would silently burn through an item's three chances.
        if (posted) buildDigest.markDelivered(items)
    }

    /** Note first, then recognised text, then a fallback. The digest is a nudge, not reading. */
    private fun describe(item: CapturedItem): String {
        val note = item.userNote.trim()
        if (note.isNotEmpty()) return note.take(MAX_LINE_CHARS)

        val text = item.extractedText.trim().replace(Regex("\\s+"), " ")
        if (text.isNotEmpty()) return text.take(MAX_LINE_CHARS)

        return localizedContext.getString(R.string.digest_item_untitled)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localizedContext.getString(R.string.channel_reminder_desc)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "snapmind_reminder"
        private const val NOTIF_ID = 4300
        private const val MAX_LINE_CHARS = 40
    }
}
