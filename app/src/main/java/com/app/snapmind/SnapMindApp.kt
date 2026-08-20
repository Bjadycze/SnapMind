package com.app.snapmind

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Task 1 baseline.
 *
 * WorkManager is initialised on demand (see the manifest provider override) so that Hilt can
 * inject workers. Task 5 depends on this: per spec.md 7.4, nothing time-based may use
 * delay() inside a service -- Task 0 measured those deferred by up to three hours in Doze.
 */
@HiltAndroidApp
class SnapMindApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
