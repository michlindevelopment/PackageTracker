package com.michlind.packagetracker.domain.model

data class AliOrderImport(
    val orderId: String,
    val name: String,
    val imageUrl: String?,
    val trackingNumber: String?,
    val orderCreatedAt: Long,
    // Visible AliExpress card status, e.g. "Awaiting shipment",
    // "Awaiting delivery", "Order completed". Used to decide whether to mark
    // the package as Not Yet Sent or already received on import.
    val statusText: String? = null
)

enum class ImportResult { ADDED, UPGRADED, SKIPPED, FAILED }

/**
 * How the AliExpress importer should treat orders we've already imported.
 *
 *  - [Quick]: skip the per-order tracking-number lookup for orders we've
 *    already enriched (faster). Existing packages keep their tracking number;
 *    only blank tracking numbers ever get filled in.
 *  - [FullSync]: re-read the tracking number for every order, including ones
 *    we've already imported, and overwrite ours if AliExpress now reports a
 *    different value. Used to detect tracking-number changes (e.g. seller
 *    re-shipped under a different label). Slower.
 *
 * Manual edits are never overwritten — full sync only updates packages that
 * still carry an `ali:`-prefixed externalOrderId (i.e. nothing the user
 * touched in the Add/Edit screen, which clears externalOrderId).
 */
enum class AliImportMode { Quick, FullSync }
