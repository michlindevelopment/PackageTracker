package com.michlind.packagetracker.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.michlind.packagetracker.data.api.CainiaoApiService
import com.michlind.packagetracker.data.db.PackageDao
import com.michlind.packagetracker.data.db.PackageEntity
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingEvent
import com.michlind.packagetracker.domain.model.TrackingResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.StatusMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageRepositoryImpl @Inject constructor(
    private val dao: PackageDao,
    private val api: CainiaoApiService,
    private val gson: Gson
) : PackageRepository {

    override fun getActivePackages(): Flow<List<TrackedPackage>> =
        dao.getActivePackages().map { list -> list.map { it.toDomain() } }

    override fun getReceivedPackages(): Flow<List<TrackedPackage>> =
        dao.getReceivedPackages().map { list -> list.map { it.toDomain() } }

    override suspend fun getPackageById(id: Long): TrackedPackage? =
        dao.getById(id)?.toDomain()

    override suspend fun getNonReceivedPackages(): List<TrackedPackage> =
        dao.getNonReceivedPackages().map { it.toDomain() }

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
            val response = api.trackPackage(trackingNumber)
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
                    groupDescription = trace.group?.nodeDesc
                )
            } ?: emptyList()

            val latestActionCode = events.firstOrNull()?.actionCode
            val status = StatusMapper.map(data.status, latestActionCode)

            Result.success(
                TrackingResult(
                    status = status,
                    statusDescription = data.statusDesc.orEmpty(),
                    events = events,
                    estimatedDeliveryTime = data.estimatedDeliveryTime,
                    daysInTransit = data.daysNumber?.replace("\t", " "),
                    originCountry = data.originCountry,
                    destCountry = data.destCountry
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshPackage(id: Long): Result<Boolean> {
        val existing = dao.getById(id) ?: return Result.failure(Exception("Package not found"))
        val result = trackPackage(existing.trackingNumber)
        return result.map { tracking ->
            val statusChanged = existing.status != tracking.status.name
            val updatedEntity = existing.copy(
                status = tracking.status.name,
                statusDescription = tracking.statusDescription,
                lastEventDescription = tracking.events.firstOrNull()?.description.orEmpty(),
                lastEventTime = tracking.events.firstOrNull()?.time ?: existing.lastEventTime,
                lastUpdated = System.currentTimeMillis(),
                eventsJson = gson.toJson(tracking.events),
                estimatedDeliveryTime = tracking.estimatedDeliveryTime,
                daysInTransit = tracking.daysInTransit,
                originCountry = tracking.originCountry,
                destCountry = tracking.destCountry
            )
            dao.update(updatedEntity)
            statusChanged
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
            destCountry = destCountry
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
        destCountry = destCountry
    )
}
