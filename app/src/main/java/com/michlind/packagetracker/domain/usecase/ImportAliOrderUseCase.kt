package com.michlind.packagetracker.domain.usecase

import android.util.Log
import com.michlind.packagetracker.domain.model.AliImportMode
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
    /**
     * Downloads every item thumbnail of a multi-item order. Item 0 reuses
     * [firstLocal] (the already-downloaded main photo) when given, so the
     * first image is never fetched twice for a brand-new order. Items whose
     * download fails are skipped, so the result can be shorter than [urls].
     */
    private suspend fun downloadItemPhotos(
        orderId: String,
        urls: List<String>,
        firstLocal: String?
    ): List<String> = urls.mapIndexedNotNull { index, url ->
        if (index == 0 && firstLocal != null) firstLocal
        else imageDownloader.download(url, fileBaseName = "${orderId}_$index")
    }

    suspend operator fun invoke(
        order: AliOrderImport,
        mode: AliImportMode = AliImportMode.Quick
    ): ImportResult {
        val externalId = "ali:${order.orderId}"
        val existing = repository.getByExternalOrderId(externalId)
        val tn = order.trackingNumber?.trim().orEmpty()
        val notYetShipped = isNotYetShipped(order.statusText)
        val received = isReceivedStatus(order.statusText)

        // All item thumbnails on the order card. Older scripts only sent the
        // first one (imageUrl), so fall back to that.
        val imageUrls = order.imageUrls.orEmpty().filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(order.imageUrl?.takeIf { it.isNotBlank() }) }
        val itemCount = maxOf(order.itemCount, imageUrls.size, 1)

        Log.d(
            TAG,
            "import order=${order.orderId} name='${order.name.take(40)}' " +
                "tn='${tn.ifBlank { "<none>" }}' existing=${existing != null} " +
                "existingTn='${existing?.trackingNumber.orEmpty()}' " +
                "cardStatus='${order.statusText.orEmpty()}' " +
                "notYetShipped=$notYetShipped received=$received " +
                "items=$itemCount images=${imageUrls.size}"
        )

        return runCatching {
            when {
                existing == null -> {
                    val localPhoto = order.imageUrl?.let {
                        imageDownloader.download(it, fileBaseName = order.orderId)
                    }
                    val itemPhotos = if (itemCount > 1) {
                        downloadItemPhotos(order.orderId, imageUrls, localPhoto)
                    } else emptyList()

                    // If a row with this tracking number already exists, this
                    // new order is joining an existing multi-group (multiple
                    // AliExpress orders shipping under one TN). Inherit the
                    // group's tracking events / delivery metadata so the new
                    // row renders the same shipping progress as its siblings
                    // — otherwise the AliExpress-completed and ready-to-ship
                    // paths skip the Cainiao refresh below and we'd ship an
                    // empty timeline. Doesn't matter which sibling we pick;
                    // they all share the same TN-derived data after a refresh.
                    val sibling = if (tn.isNotBlank()) {
                        repository.getFirstByTrackingNumber(tn)
                    } else null

                    // ORDER_PLACED requires a tracking number — without one we'd
                    // have nothing to refresh against Cainiao. If the iframe scrape
                    // failed on a shipped order, park it as NOT_YET_SENT; the
                    // upgrade path below will promote it once a re-import succeeds.
                    val baseStatus = when {
                        received -> PackageStatus.DELIVERED
                        notYetShipped || tn.isBlank() -> PackageStatus.NOT_YET_SENT
                        else -> PackageStatus.ORDER_PLACED
                    }
                    // Trust AliExpress for the boundary states (received /
                    // not yet sent); for anything in between, defer to the
                    // sibling's carrier-derived status so the multi-group's
                    // StatusBadge stays consistent.
                    val status = if (sibling != null && !received && !notYetShipped) {
                        sibling.status
                    } else baseStatus

                    val pkg = TrackedPackage(
                        trackingNumber = tn,
                        name = order.name,
                        photoUri = localPhoto,
                        status = status,
                        statusDescription = sibling?.statusDescription
                            ?: order.statusText.orEmpty(),
                        lastEvent = sibling?.lastEvent,
                        events = sibling?.events ?: emptyList(),
                        lastUpdated = sibling?.lastUpdated ?: 0L,
                        isReceived = received,
                        createdAt = order.orderCreatedAt,
                        estimatedDeliveryTime = sibling?.estimatedDeliveryTime,
                        daysInTransit = sibling?.daysInTransit,
                        originCountry = sibling?.originCountry,
                        destCountry = sibling?.destCountry,
                        externalOrderId = externalId,
                        progressRate = sibling?.progressRate,
                        itemCount = itemCount,
                        itemPhotoUris = itemPhotos
                    )
                    val newId = repository.addPackage(pkg)
                    // Carrier-status fetch is no longer triggered inline.
                    // The caller is expected to follow this import with a
                    // syncStatus pass (every reachable flow does:
                    // quickFetchThenSyncStatus / fullFetchThenSyncStatus),
                    // and that pass refreshes every eligible TN — including
                    // this brand-new one — exactly once.
                    Log.d(
                        TAG,
                        "  -> ADDED id=$newId status=$status received=$received " +
                            "sibling=${sibling != null}"
                    )
                    ImportResult.ADDED
                }

                // Existing record — apply any upgrades AliExpress now offers:
                //  - mark received if the order completed on the website
                //  - fill in tracking number if the iframe scrape succeeded this time
                //  - backfill name / image if a previous import didn't capture them
                else -> {
                    var updated = existing
                    val reasons = mutableListOf<String>()

                    if (received && !existing.isReceived) {
                        updated = updated.copy(
                            isReceived = true,
                            status = PackageStatus.DELIVERED,
                            statusDescription = order.statusText.orEmpty(),
                            receivedAt = System.currentTimeMillis()
                        )
                        reasons += "received"
                    }

                    // Tracking-number write rules:
                    //  - Quick mode: only ever fill in a blank one (current
                    //    behavior — never trample an existing value).
                    //  - Full Sync mode: also overwrite a non-blank TN if
                    //    AliExpress now reports a different value, but only
                    //    for orders that still carry an `ali:`-prefixed
                    //    externalOrderId. The user's manual edits clear that
                    //    prefix in AddEditViewModel.save(), so this path
                    //    never touches anything the user typed by hand.
                    val isAliOwned = (existing.externalOrderId ?: "")
                        .startsWith("ali:")
                    val incomingDiffers = tn.isNotBlank() &&
                        tn != existing.trackingNumber
                    val wasBlank = existing.trackingNumber.isBlank()
                    val shouldUpdateTn = incomingDiffers && (
                        wasBlank ||
                        (mode == AliImportMode.FullSync && isAliOwned)
                    )
                    val tnChanged = shouldUpdateTn && !wasBlank
                    if (shouldUpdateTn) {
                        updated = updated.copy(
                            trackingNumber = tn,
                            // don't downgrade DELIVERED above
                            status = if (updated.status == PackageStatus.DELIVERED) updated.status
                                     else PackageStatus.ORDER_PLACED,
                            // Existing events belong to the old TN — wipe
                            // them when the TN changes so the follow-up
                            // refreshPackage() repopulates with the new
                            // carrier history. Blank → non-blank: nothing
                            // to wipe (events were already empty).
                            lastEvent = if (tnChanged) null else updated.lastEvent,
                            events = if (tnChanged) emptyList() else updated.events
                        )
                        reasons += if (wasBlank) "got tn" else "tn changed"
                    }
                    if (existing.name.isBlank() && order.name.isNotBlank()) {
                        updated = updated.copy(name = order.name)
                        reasons += "name"
                    }

                    if (existing.photoUri.isNullOrBlank() && !order.imageUrl.isNullOrBlank()) {
                        val photo = imageDownloader.download(order.imageUrl, fileBaseName = order.orderId)
                        if (!photo.isNullOrBlank()) {
                            updated = updated.copy(photoUri = photo)
                            reasons += "photo"
                        }
                    }

                    // Orders imported before multi-item support (or whose card
                    // showed fewer items back then) only have the first photo.
                    // Re-download the full gallery whenever the card now shows
                    // more items than we have photos for.
                    val needsItemBackfill = itemCount > 1 && imageUrls.size > 1 &&
                        (existing.itemCount != itemCount ||
                            existing.itemPhotoUris.size < imageUrls.size)
                    if (needsItemBackfill) {
                        val itemPhotos = downloadItemPhotos(order.orderId, imageUrls, firstLocal = null)
                        if (itemPhotos.size > existing.itemPhotoUris.size ||
                            existing.itemCount != itemCount
                        ) {
                            updated = updated.copy(
                                itemCount = itemCount,
                                itemPhotoUris = itemPhotos
                            )
                            reasons += "items"
                        }
                    }

                    if (reasons.isEmpty()) {
                        Log.d(TAG, "  -> SKIPPED (already imported, no change)")
                        ImportResult.SKIPPED
                    } else {
                        repository.updatePackage(updated)
                        // No inline carrier refresh on TN changes either —
                        // the trailing syncStatus picks them up. (This used
                        // to also handle wiping the timeline before the
                        // refresh; the wipe still happens above where
                        // updated.events is set to emptyList on tnChanged.)
                        Log.d(TAG, "  -> UPGRADED id=${existing.id} (${reasons.joinToString()})")
                        ImportResult.UPGRADED
                    }
                }
            }
        }.getOrElse {
            Log.e(TAG, "  -> FAILED order=${order.orderId}", it)
            ImportResult.FAILED
        }
    }
}
