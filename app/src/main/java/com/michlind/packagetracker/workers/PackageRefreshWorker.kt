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
import com.michlind.packagetracker.data.preferences.NotificationPreferenceRepository
import com.michlind.packagetracker.di.CainiaoRateLimitException
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.NotificationUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@HiltWorker
class PackageRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PackageRepository,
    private val notificationPrefs: NotificationPreferenceRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val packages = repository.getPackagesEligibleForRefresh()
            val byTracking = packages.groupBy { it.trackingNumber }

            val notify = notificationPrefs.enabled.value
            var first = true
            for ((trackingNumber, pkgs) in byTracking) {
                // Same gap the home screen keeps between lookups. Firing them
                // back to back is what trips Cainiao's bot wall.
                if (!first) delay(INTER_REQUEST_MS)
                first = false
                val result = repository.refreshTrackingNumber(trackingNumber)
                result.onSuccess { changes ->
                    pkgs.forEach { pkg ->
                        if (changes[pkg.id] == true && notify) {
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
                // Once blocked, every further request hits the same wall and
                // only prolongs the ban. Stop; the next hourly run tries again.
                if (result.exceptionOrNull() is CainiaoRateLimitException) break
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "package_refresh_periodic"
        private const val INTER_REQUEST_MS = 750L

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
