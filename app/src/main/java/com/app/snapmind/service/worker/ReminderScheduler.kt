package com.app.snapmind.service.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.app.snapmind.service.reminder.ReminderAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder with AlarmManager.
 *
 * Two earlier attempts failed on real hardware:
 *
 *  1. PeriodicWorkRequest measured its interval from the last enqueue rather than a wall-clock
 *     time, so opening the app at 15:00 moved an 18:00 reminder to 15:00.
 *  2. One-shot WorkManager fixed that, but WorkManager only promises to run work *eventually*.
 *     On MagicOS an 11:00 reminder arrived at 12:45. Task 0 had measured the same effect on
 *     the service heartbeat (gaps of 70-181 minutes) -- the conclusion in spec.md 7.4 that
 *     "WorkManager was unaffected" was drawn from ContentObserver delivery, not from timed
 *     work, and was wrong.
 *
 * setAndAllowWhileIdle survives Doze and needs no extra permission. It is inexact by design,
 * so expect accuracy in minutes, not seconds. setExactAndAllowWhileIdle would be precise but
 * requires SCHEDULE_EXACT_ALARM, which Play restricts to alarm clocks and calendars -- not a
 * trade worth making for a reminder that says "whenever you have a moment".
 *
 * Alarms do not survive a reboot; BootReceiver re-registers them.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @param replaceExisting true when the user changed the time, or when the alarm has just
     *        fired and tomorrow's needs booking. False for the idempotent call on app start,
     *        which must leave an already-correct alarm alone.
     */
    fun schedule(reminderHour: Int, replaceExisting: Boolean = false) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return

        if (!replaceExisting && existingAlarm() != null) return

        val triggerAt = nextOccurrence(reminderHour)
        // FLAG_UPDATE_CURRENT always returns an intent; the nullable type comes from the
        // FLAG_NO_CREATE lookup sharing this helper.
        val intent = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                intent
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "alarm refused", e)
        }
    }

    fun cancel() {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        existingAlarm()?.let { manager.cancel(it) }
    }

    private fun existingAlarm(): PendingIntent? =
        alarmIntent(PendingIntent.FLAG_NO_CREATE)

    private fun alarmIntent(extraFlags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        extraFlags or PendingIntent.FLAG_IMMUTABLE
    )

    /** Always the next occurrence of that hour; tomorrow if today's has passed. */
    private fun nextOccurrence(hour: Int): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        private const val REQUEST_CODE = 4301
    }
}