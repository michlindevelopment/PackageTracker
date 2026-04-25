package com.michlind.packagetracker.domain.usecase

import android.util.Log
import com.michlind.packagetracker.domain.model.AliOrderImport
import com.michlind.packagetracker.domain.model.ImportResult
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.RemoteImageDownloader
import javax.inject.Inject

private const val TAG = "AliImport"

class ImportAliOrderUseCase @Inject constructor(
    private val repository: PackageRepository,
    private val imageDownloader: RemoteImageDownloader
) {
    suspend operator fun invoke(order: AliOrderImport): ImportResult {
        val externalId = "ali:${order.orderId}"
        val existing = repository.getByExternalOrderId(externalId)
        val tn = order.trackingNumber?.trim().orEmpty()

        Log.d(
            TAG,
            "import order=${order.orderId} name='${order.name.take(40)}' " +
                "tn='${tn.ifBlank { "<none>" }}' existing=${existing != null} " +
                "existingTn='${existing?.trackingNumber.orEmpty()}'"
        )

        return runCatching {
            when {
                existing == null -> {
                    val localPhoto = order.imageUrl?.let {
                        imageDownloader.download(it, fileBaseName = order.orderId)
                    }
                    val status = if (tn.isBlank()) PackageStatus.NOT_YET_SENT
                    else PackageStatus.ORDER_PLACED
                    val pkg = TrackedPackage(
                        trackingNumber = tn,
                        name = order.name,
                        photoUri = localPhoto,
                        status = status,
                        statusDescription = "",
                        lastEvent = null,
                        events = emptyList(),
                        lastUpdated = 0L,
                        isReceived = false,
                        createdAt = order.orderCreatedAt,
                        estimatedDeliveryTime = null,
                        daysInTransit = null,
                        originCountry = null,
                        destCountry = null,
                        externalOrderId = externalId
                    )
                    val newId = repository.addPackage(pkg)
                    if (tn.isNotBlank()) repository.refreshPackage(newId)
                    Log.d(TAG, "  -> ADDED id=$newId status=$status")
                    ImportResult.ADDED
                }

                existing.trackingNumber.isBlank() && tn.isNotBlank() -> {
                    repository.updatePackage(
                        existing.copy(
                            trackingNumber = tn,
                            status = PackageStatus.ORDER_PLACED
                        )
                    )
                    repository.refreshPackage(existing.id)
                    Log.d(TAG, "  -> UPGRADED id=${existing.id}")
                    ImportResult.UPGRADED
                }

                else -> {
                    Log.d(TAG, "  -> SKIPPED (already imported, no tn change)")
                    ImportResult.SKIPPED
                }
            }
        }.getOrElse {
            Log.e(TAG, "  -> FAILED order=${order.orderId}", it)
            ImportResult.FAILED
        }
    }
}
