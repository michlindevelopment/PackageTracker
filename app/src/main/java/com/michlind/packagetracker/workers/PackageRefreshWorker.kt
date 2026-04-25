package com.michlind.packagetracker.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.NotificationUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PackageRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PackageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val packages = repository.getNonReceivedPackages()
            val byTracking = packages
                .filter { it.trackingNumber.isNotBlank() }
                .groupBy { it.trackingNumber }

            byTracking.forEach { (trackingNumber, pkgs) ->
                repository.refreshTrackingNumber(trackingNumber).onSuccess { changes ->
                    pkgs.forEach { pkg ->
                        if (changes[pkg.id] == true) {
                            val updated = repository.getPackageById(pkg.id)
                            NotificationUtils.sendStatusUpdateNotification(
                                context = applicationContext,
                                packageId = pkg.id,
                                packageName = pkg.name.ifBlank { pkg.trackingNumber },
                                newStatus = updated?.status?.displayName ?: "Updated",
                                photoUri = updated?.photoUri ?: pkg.photoUri
                            )
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "package_refresh_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PackageRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            // UPDATE policy so changes to the interval propagate to installs
            // that already have the previous schedule registered (KEEP would
            // silently retain the old interval).
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
