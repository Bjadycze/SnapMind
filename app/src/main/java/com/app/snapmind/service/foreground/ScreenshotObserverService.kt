package com.app.snapmind.service.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.snapmind.R
import com.app.snapmind.data.mediastore.ScreenshotQuery
import com.app.snapmind.data.prefs.AppLanguagePrefs
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.model.ReminderPolicy
import com.app.snapmind.domain.repository.CapturedItemRepository
import com.app.snapmind.domain.service.QuickCapturePresenter
import com.app.snapmind.domain.usecase.ProcessCapturedImageUseCase
import com.app.snapmind.presentation.locale.withAppLocale
import com.app.snapmind.service.observer.MediaStoreObserver
import com.app.snapmind.service.worker.OcrWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The capture engine. Task 0 established the shape of this class:
 *
 *  - It survives overnight and is never killed, provided the battery exemption is granted.
 *  - Doze freezes it for hours, so it must not rely on timers. There is no delay() or
 *    Handler-based polling here; the only wake sources are ContentObserver delivery and
 *    ACTION_SCREEN_ON, both of which were measured to arrive promptly (spec.md 7.4).
 */
@AndroidEntryPoint
class ScreenshotObserverService : LifecycleService() {

    @Inject lateinit var query: ScreenshotQuery
    @Inject lateinit var repository: CapturedItemRepository
    @Inject lateinit var processCapture: ProcessCapturedImageUseCase
    @Inject lateinit var presenter: QuickCapturePresenter

    private var observer: MediaStoreObserver? = null

    /** In-memory guard. The unique index on mediaStoreId is the real backstop. */
    private var lastHandledId: Long = -1L

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) runCatchUp()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        registerObserver()
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        runCatchUp()
    }

    private fun startAsForeground() {
        // No Activity to read from here, so the choice comes from the synchronous prefs mirror.
        val localizedContext = withAppLocale(AppLanguagePrefs.get(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    localizedContext.getString(R.string.channel_service_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
            )
        }

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(localizedContext.getString(R.string.service_running))
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+ rejects startForeground without a declared type. Task 0 verified
                // that specialUse is also permitted from BOOT_COMPLETED on API 35.
                startForeground(
                    SERVICE_NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(SERVICE_NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground refused", e)
            stopSelf()
        }
    }

    private fun registerObserver() {
        val obs = MediaStoreObserver(onSettled = ::handleLatest)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            obs
        )
        observer = obs
    }

    /** Normal path: the observer settled, so look at the newest screenshot only. */
    private fun handleLatest() {
        val hit = query.latest() ?: return
        if (hit.mediaStoreId == lastHandledId) return
        if (hit.ageMillis > ScreenshotQuery.STALE_THRESHOLD_MS) return
        lastHandledId = hit.mediaStoreId
        capture(hit.uri, hit.mediaStoreId)
    }

    /**
     * Doze can freeze this process for hours, during which screenshots accumulate unseen.
     * Capped at three so a long freeze cannot produce a burst of prompts (spec.md 6.2).
     */
    private fun runCatchUp() {
        lifecycleScope.launch {
            val highest = repository.getHighestMediaStoreId() ?: return@launch
            val missed = query.newerThan(highest, ReminderPolicy.MAX_CATCH_UP_ITEMS)
            for (hit in missed) {
                lastHandledId = hit.mediaStoreId
                captureSuspend(hit.uri, hit.mediaStoreId)
            }
        }
    }

    private fun capture(uri: String, mediaStoreId: Long) {
        lifecycleScope.launch { captureSuspend(uri, mediaStoreId) }
    }

    private suspend fun captureSuspend(uri: String, mediaStoreId: Long) {
        val id = processCapture(
            imageUri = uri,
            mediaStoreId = mediaStoreId,
            source = CaptureSource.SCREENSHOT
        ) ?: return

        presenter.present(id, uri, CaptureSource.SCREENSHOT)

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            OcrWorker.NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<OcrWorker>().build()
        )
    }

    override fun onDestroy() {
        observer?.let {
            it.cancelPending()
            contentResolver.unregisterContentObserver(it)
        }
        runCatching { unregisterReceiver(screenOnReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenshotObserver"
        private const val CHANNEL_ID = "snapmind_service"
        private const val SERVICE_NOTIF_ID = 4200

        fun start(context: Context) {
            val intent = Intent(context, ScreenshotObserverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotObserverService::class.java))
        }
    }
}
