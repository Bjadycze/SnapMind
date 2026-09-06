package com.app.snapmind.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.service.worker.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired by AlarmManager at the user's reminder hour.
 *
 * goAsync keeps the process alive while the digest is read and posted; onReceive would
 * otherwise return before the coroutine ran.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var settings: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Chain tomorrow's alarm first, so a failure below cannot break the cycle.
                scheduler.schedule(settings.reminderHour.first(), replaceExisting = true)
                notifier.deliverIfDue()
            } catch (e: Exception) {
                Log.e(TAG, "reminder delivery failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarm"
    }
}
