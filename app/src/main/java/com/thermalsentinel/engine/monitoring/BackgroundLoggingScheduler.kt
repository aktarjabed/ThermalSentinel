package com.thermalsentinel.engine.monitoring

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.thermalsentinel.engine.service.BackgroundLoggingWorker
import java.util.concurrent.TimeUnit

/** Opt-in sparse history logging; this never starts the foreground monitoring service. */
object BackgroundLoggingScheduler {
    private const val TAG = "BackgroundLogScheduler"
    const val UNIQUE_WORK_NAME = "thermal-sentinel-background-log"

    fun sync(context: Context, enabled: Boolean) {
        try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<BackgroundLoggingWorker>(
                BackgroundLoggingPolicy.PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            ).setConstraints(constraints).build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (failure: IllegalStateException) {
            Log.w(TAG, "Could not reconcile scheduled background logging", failure)
        } catch (failure: SecurityException) {
            Log.w(TAG, "Platform rejected background logging scheduling", failure)
        }
    }
}
