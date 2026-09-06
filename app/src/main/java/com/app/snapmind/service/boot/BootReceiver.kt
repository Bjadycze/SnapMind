package com.app.snapmind.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.app.snapmind.service.foreground.ScreenshotObserverService
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.service.worker.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Task 0 verified on API 35: a specialUse foreground service starts successfully from
 * BOOT_COMPLETED, and detection worked three minutes after reboot without opening the app.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        try {
            ScreenshotObserverService.start(context)
        } catch (e: Exception) {
            Log.e("BootReceiver", "could not restart observer service", e)
        }

        // Alarms do not survive a reboot, so the daily reminder has to be booked again.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.schedule(settings.reminderHour.first(), replaceExisting = true)
            } catch (e: Exception) {
                Log.e("BootReceiver", "could not reschedule reminder", e)
            } finally {
                pending.finish()
            }
        }
    }
}
