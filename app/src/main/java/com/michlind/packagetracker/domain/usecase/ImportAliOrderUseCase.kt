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

// AliExpress card status mapping:
//   "Ready to ship"     -> not yet sent
//   "Awaiting delivery" -> in transit (falls through both checks)
//   "Completed"         -> received
private fun isNotYetShipped(statusText: String?): Boolean {
    val s = (statusText ?: "").lowercase()
    return Regex("ready\\s*to\\s*ship").containsMatchIn(s)
}

private fun isReceivedStatus(statusText: String?): Boolean =
    (statusText ?: "").lowercase().contains("complet")

class ImportAliOrderUseCase @Inject constructor(
    private val repository: PackageRepository,
    private val imageDownloader: RemoteImageDownloader
) {
    suspend operator fun invoke(order: AliOrderImport): ImportResult {
        val externalId = "ali:${order.orderId}"
        val existing = repository.getByExternalOrderId(externalId)
        val tn = order.trackingNumber?.trim().orEmpty()
        val notYetShipped = isNotYetShipped(order.statusText)
        val received = isReceivedStatus(order.statusText)

        Log.d(
            TAG,
            "import order=${order.orderId} name='${order.name.take(40)}' " +
                "tn='${tn.ifBlank { "<none>" }}' existing=${existing != null} " +
                "existingTn='${existing?.trackingNumber.orEmpty()}' " +
                "cardStatus='${order.statusText.orEmpty()}' " +
                "notYetShipped=$notYetShipped received=$received"
        )

        return runCatching {
            when {
                existing == null -> {
                    val localPhoto = order.imageUrl?.let {
                        imageDownloader.download(it, fileBaseName = order.orderId)
                    }
                    // The card status is authoritative — a missing tracking
                    // number on an "Awaiting delivery" card just means we
                    // couldn't scrape it, NOT that the order hasn't shipped.
                    val status = when {
                        received -> PackageStatus.DELIVERED
                        notYetShipped -> PackageStatus.NOT_YET_SENT
                        else -> PackageStatus.ORDER_PLACED
                    }
                    val pkg = TrackedPackage(
                        trackingNumber = tn,
                        name = order.name,
                        photoUri = localPhoto,
                        status = status,
                        statusDescription = order.statusText.orEmpty(),
                        lastEvent = null,
                        events = emptyList(),
                        lastUpdated = 0L,
                        isReceived = received,
                        createdAt = order.orderCreatedAt,
                        estimatedDeliveryTime = null,
                        daysInTransit = null,
                        originCountry = null,
                        destCountry = null,
                        externalOrderId = externalId
                    )
                    val newId = repository.addPackage(pkg)
                    // Only refresh tracking for in-transit imports — pre-shipment
                    // and delivered packages either have no tracking or don't need it.
                    if (tn.isNotBlank() && !notYetShipped && !received) {
                        repository.refreshPackage(newId)
                    }
                    Log.d(TAG, "  -> ADDED id=$newId status=$status received=$received")
                    ImportResult.ADDED
                }

                // Existing record — promote to Received if AliExpress now says
                // the order is complete (the user might have confirmed receipt
                // on the website since our last import).
                received && !existing.isReceived -> {
                    repository.updatePackage(
                        existing.copy(
                            isReceived = true,
                            status = PackageStatus.DELIVERED,
                            statusDescription = order.statusText.orEmpty()
                        )
                    )
                    Log.d(TAG, "  -> UPGRADED id=${existing.id} (now received)")
                    ImportResult.UPGRADED
                }

                existing.trackingNumber.isBlank() && tn.isNotBlank() -> {
                    repository.updatePackage(
                        existing.copy(
                            trackingNumber = tn,
                            status = PackageStatus.ORDER_PLACED
                        )
                    )
                    repository.refreshPackage(existing.id)
                    Log.d(TAG, "  -> UPGRADED id=${existing.id} (got tn)")
                    ImportResult.UPGRADED
                }

                else -> {
                    Log.d(TAG, "  -> SKIPPED (already imported, no change)")
                    ImportResult.SKIPPED
                }
            }
        }.getOrElse {
            Log.e(TAG, "  -> FAILED order=${order.orderId}", it)
            ImportResult.FAILED
        }
    }
}
