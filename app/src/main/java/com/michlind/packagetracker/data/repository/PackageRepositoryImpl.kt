package com.michlind.packagetracker.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.michlind.packagetracker.BuildConfig
import com.michlind.packagetracker.data.api.CainiaoApiService
import com.michlind.packagetracker.data.api.MockCainiaoResponseGenerator
import com.michlind.packagetracker.data.db.PackageDao
import com.michlind.packagetracker.data.db.PackageEntity
import com.michlind.packagetracker.data.preferences.MockTrackingPreferenceRepository
import com.michlind.packagetracker.domain.model.DestCarrierInfo
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingEvent
import com.michlind.packagetracker.domain.model.TrackingResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.StatusMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Cainiao action codes that are forecasts/advisory notifications rather than
// real package progress — they show up at the top of the trace list but the
// package may be far earlier in its journey. Skip these when deriving status.
private val ADVISORY_ACTION_CODES = setOf(
    // Destination carrier was notified the package will eventually arrive,
    // but the package itself is typically still at origin.
    "LAST_MILE_ASN_NOTIFY"
)

@Singleton
class PackageRepositoryImpl @Inject constructor(
    private val dao: PackageDao,
    private val api: CainiaoApiService,
    private val gson: Gson,
    private val mockPrefs: MockTrackingPreferenceRepository
) : PackageRepository {

    override fun getActivePackages(): Flow<List<TrackedPackage>> =
        dao.getActivePackages().map { list -> list.map { it.toDomain() } }

    override fun getReceivedPackages(): Flow<List<TrackedPackage>> =
        dao.getReceivedPackages().map { list -> list.map { it.toDomain() } }

    override fun getNotYetSentPackages(): Flow<List<TrackedPackage>> =
        dao.getNotYetSentPackages().map { list -> list.map { it.toDomain() } }

    override suspend fun getPackageById(id: Long): TrackedPackage? =
        dao.getById(id)?.toDomain()

    override suspend fun getNonReceivedPackages(): List<TrackedPackage> =
        dao.getNonReceivedPackages().map { it.toDomain() }

    override suspend fun getPackagesEligibleForRefresh(): List<TrackedPackage> =
        dao.getPackagesEligibleForRefresh().map { it.toDomain() }

    override suspend fun addPackage(pkg: TrackedPackage): Long {
        val entity = pkg.toEntity()
        return dao.insert(entity)
    }

    override suspend fun updatePackage(pkg: TrackedPackage) {
        dao.update(pkg.toEntity())
    }

    override suspend fun deletePackage(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun markAsReceived(id: Long, isReceived: Boolean) {
        dao.setReceived(id, isReceived, System.currentTimeMillis())
    }

    override suspend fun trackPackage(trackingNumber: String): Result<TrackingResult> {
        return try {
            // Test mode (Settings → "Mock Cainiao responses"): skip the real
            // network call and synthesize a randomized but plausible payload.
            // Useful for exercising the UI without tripping Cainiao's
            // anti-bot CAPTCHA from rapid back-to-back requests.
            val response = if (BuildConfig.DEBUG && mockPrefs.enabled.value) {
                // Simulate network latency so the per-card refresh animation
                // is actually visible (the real API takes ~1s; mock gen is
                // instant, which makes the badge animation flash by).
                delay(1500)
                MockCainiaoResponseGenerator.generate(trackingNumber)
            } else {
                api.trackPackage(trackingNumber)
            }
            if (!response.success || response.module.isNullOrEmpty()) {
                return Result.failure(Exception("Tracking number not found"))
            }
            val data = response.module[0]
            val events = data.detailList?.map { trace ->
                TrackingEvent(
                    time = trace.time ?: 0L,
                    timeStr = trace.timeStr.orEmpty(),
                    description = trace.desc.orEmpty(),
                    standardDescription = trace.standerdDesc.orEmpty(),
                    actionCode = trace.actionCode.orEmpty(),
                    groupDescription = trace.group?.nodeDesc,
                    groupCurrentIconUrl = trace.group?.currentIconUrl,
                    groupHistoryIconUrl = trace.group?.historyIconUrl
                )
            } ?: emptyList()

            // Cainiao sometimes prepends advisory entries (e.g. an
            // Advance Shipping Notice from the destination courier) that don't
            // reflect actual package movement. Skip those when picking the
            // latest meaningful action code so a forecast doesn't get treated
            // as the real state.
            val latestActionCode = events.firstOrNull {
                it.actionCode.isNotBlank() && it.actionCode !in ADVISORY_ACTION_CODES
            }?.actionCode
            val progressPoints = data.processInfo?.progressPointList.orEmpty()
            val status = StatusMapper.map(
                data.status,
                latestActionCode,
                data.processInfo?.progressRate,
                progressPointsLit = progressPoints.count { it.light == true },
                progressPointsTotal = progressPoints.size
            )

            val destCarrier = data.destCpInfo?.let { cp ->
                val name = cp.cpName?.trim().orEmpty()
                if (name.isBlank()) null
                else DestCarrierInfo(
                    name = name,
                    phone = cp.phone?.trim()?.ifBlank { null },
                    url = cp.url?.trim()?.ifBlank { null },
                    email = cp.email?.trim()?.ifBlank { null }
                )
            }

            Result.success(
                TrackingResult(
                    status = status,
                    statusDescription = data.statusDesc.orEmpty(),
                    events = events,
                    estimatedDeliveryTime = data.estimatedDeliveryTime,
                    daysInTransit = data.daysNumber?.replace("\t", " "),
                    originCountry = data.originCountry,
                    destCountry = data.destCountry,
                    progressRate = data.processInfo?.progressRate,
                    destCarrier = destCarrier
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshPackage(id: Long): Result<Boolean> {
        val existing = dao.getById(id) ?: return Result.failure(Exception("Package not found"))
        return refreshTrackingNumber(existing.trackingNumber).map { changes ->
            changes[id] ?: false
        }
    }

    override suspend fun getByExternalOrderId(externalOrderId: String): TrackedPackage? =
        dao.getByExternalOrderId(externalOrderId)?.toDomain()

    override suspend fun getImportedAliOrderIdsWithTracking(): Set<String> =
        dao.getAliExternalOrderIdsWithTracking()
            .asSequence()
            .map { it.removePrefix("ali:") }
            .filter { it.isNotBlank() }
            .toSet()

    override suspend fun getBlankTrackingPackageIds(): List<Long> =
        dao.getBlankTrackingPackageIds()

    override suspend fun getNonReceivedTrackingSnapshot(): Map<Long, String> =
        dao.getNonReceivedTrackingSnapshot().associate { it.id to it.trackingNumber }

    override suspend fun getFirstByTrackingNumber(trackingNumber: String): TrackedPackage? =
        dao.getByTrackingNumber(trackingNumber).firstOrNull()?.toDomain()

    override suspend fun refreshTrackingNumber(trackingNumber: String): Result<Map<Long, Boolean>> {
        val existingRows = dao.getByTrackingNumber(trackingNumber)
        if (existingRows.isEmpty()) return Result.failure(Exception("Package not found"))
        return trackPackage(trackingNumber).map { tracking ->
            val now = System.currentTimeMillis()
            val eventsJson = gson.toJson(tracking.events)
            val firstEvent = tracking.events.firstOrNull()
            existingRows.associate { existing ->
                val statusChanged = existing.status != tracking.status.name
                // Once Cainiao reports DELIVERED, automatically promote the
                // package to "received" so it leaves the active list — saves
                // the user from manually flipping the toggle on every parcel.
                val nowDelivered = tracking.status == PackageStatus.DELIVERED
                val updated = existing.copy(
                    status = tracking.status.name,
                    statusDescription = tracking.statusDescription,
                    lastEventDescription = firstEvent?.description.orEmpty(),
                    lastEventTime = firstEvent?.time ?: existing.lastEventTime,
                    lastUpdated = now,
                    eventsJson = eventsJson,
                    estimatedDeliveryTime = tracking.estimatedDeliveryTime,
                    daysInTransit = tracking.daysInTransit,
                    originCountry = tracking.originCountry,
                    destCountry = tracking.destCountry,
                    progressRate = tracking.progressRate,
                    destCarrierName = tracking.destCarrier?.name ?: existing.destCarrierName,
                    destCarrierPhone = tracking.destCarrier?.phone ?: existing.destCarrierPhone,
                    destCarrierUrl = tracking.destCarrier?.url ?: existing.destCarrierUrl,
                    destCarrierEmail = tracking.destCarrier?.email ?: existing.destCarrierEmail,
                    isReceived = existing.isReceived || nowDelivered
                )
                dao.update(updated)
                existing.id to statusChanged
            }
        }
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private fun PackageEntity.toDomain(): TrackedPackage {
        val eventsType = object : TypeToken<List<TrackingEvent>>() {}.type
        val events: List<TrackingEvent> = try {
            gson.fromJson(eventsJson, eventsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val status = try {
            PackageStatus.valueOf(this.status)
        } catch (_: Exception) {
            PackageStatus.UNKNOWN
        }
        val destCarrier = destCarrierName?.takeIf { it.isNotBlank() }?.let { name ->
            DestCarrierInfo(
                name = name,
                phone = destCarrierPhone,
                url = destCarrierUrl,
                email = destCarrierEmail
            )
        }
        return TrackedPackage(
            id = id,
            trackingNumber = trackingNumber,
            name = name,
            photoUri = photoUri,
            status = status,
            statusDescription = statusDescription,
            lastEvent = events.firstOrNull(),
            events = events,
            lastUpdated = lastUpdated,
            isReceived = isReceived,
            createdAt = createdAt,
            estimatedDeliveryTime = estimatedDeliveryTime,
            daysInTransit = daysInTransit,
            originCountry = originCountry,
            destCountry = destCountry,
            externalOrderId = externalOrderId,
            progressRate = progressRate,
            destCarrier = destCarrier
        )
    }

    private fun TrackedPackage.toEntity(): PackageEntity = PackageEntity(
        id = id,
        trackingNumber = trackingNumber,
        name = name,
        photoUri = photoUri,
        status = status.name,
        statusDescription = statusDescription,
        lastEventDescription = lastEvent?.description.orEmpty(),
        lastEventTime = lastEvent?.time ?: 0L,
        lastUpdated = lastUpdated,
        isReceived = isReceived,
        createdAt = createdAt,
        eventsJson = gson.toJson(events),
        estimatedDeliveryTime = estimatedDeliveryTime,
        daysInTransit = daysInTransit,
        originCountry = originCountry,
        destCountry = destCountry,
        externalOrderId = externalOrderId,
        progressRate = progressRate,
        destCarrierName = destCarrier?.name,
        destCarrierPhone = destCarrier?.phone,
        destCarrierUrl = destCarrier?.url,
        destCarrierEmail = destCarrier?.email
    )
}
