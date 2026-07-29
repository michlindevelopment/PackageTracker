package com.michlind.packagetracker.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** One bar of the "Delivered per month" chart. */
data class MonthStat(
    val label: String,
    val count: Int
)

data class StatsUiState(
    // True only until the first flow emission arrives, so the screen can
    // avoid flashing the "no packages" empty state during the Room load.
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val receivedCount: Int = 0,
    val inProgressCount: Int = 0,
    // Only statuses that actually occur, most common first.
    val statusCounts: List<Pair<PackageStatus, Int>> = emptyList(),
    // Last 6 calendar months, oldest first, current month last.
    val deliveredPerMonth: List<MonthStat> = emptyList(),
    // Average days from createdAt to delivery for received packages;
    // null when nothing has been delivered yet.
    val averageDeliveryDays: Double? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: PackageRepository
) : ViewModel() {

    // The three source flows are disjoint (active = not received & sent,
    // received, not-yet-sent), so together they cover every package exactly once.
    val uiState: StateFlow<StatsUiState> = combine(
        repository.getActivePackages(),
        repository.getReceivedPackages(),
        repository.getNotYetSentPackages()
    ) { active, received, notYetSent ->
        buildStats(inProgress = active + notYetSent, received = received)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    private fun buildStats(
        inProgress: List<TrackedPackage>,
        received: List<TrackedPackage>
    ): StatsUiState {
        val all = inProgress + received

        val statusCounts = all
            .groupingBy { it.status }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        // Guard against unparseable AliExpress order dates, which are imported
        // as createdAt = 0 (epoch 1970) — see ali_import.js parseOrderDate.
        val deliveryTimes = received.map { it.deliveryTimestamp() }.filter { it > 0 }

        val averageDeliveryDays = received
            .mapNotNull { pkg ->
                // createdAt <= 0 means the order date never parsed — a delta
                // against epoch 1970 would add ~20,000 days to the average.
                if (pkg.createdAt <= 0) return@mapNotNull null
                val days = (pkg.deliveryTimestamp() - pkg.createdAt).toDouble() / DAY_MS
                // Plausibility window: nothing ships for over a year, so wilder
                // deltas are bad data, not slow packages.
                days.takeIf { it in 0.0..MAX_PLAUSIBLE_DELIVERY_DAYS }
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            // Round to 1 decimal for display.
            ?.let { (it * 10).roundToInt() / 10.0 }

        return StatsUiState(
            isLoading = false,
            totalCount = all.size,
            receivedCount = received.size,
            inProgressCount = inProgress.size,
            statusCounts = statusCounts,
            deliveredPerMonth = deliveredPerMonth(deliveryTimes),
            averageDeliveryDays = averageDeliveryDays
        )
    }

    // Best guess for when a received package was actually delivered: the most
    // recent tracking event's timestamp when there is one, else lastUpdated
    // (the moment we last saw the package change — typically when it was
    // marked received). NOTE: `events` is stored newest-first (see
    // PackageRepositoryImpl — `lastEvent = events.firstOrNull()`), so the
    // delivery event is at the FRONT of the list.
    private fun TrackedPackage.deliveryTimestamp(): Long =
        events.firstOrNull()?.time ?: lastUpdated

    // Buckets [deliveryTimes] into the last 6 calendar months (including the
    // current one), oldest first, labelled with the short month name.
    private fun deliveredPerMonth(deliveryTimes: List<Long>): List<MonthStat> {
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val calendar = Calendar.getInstance()
        return (MONTHS_SHOWN - 1 downTo 0).map { monthsAgo ->
            val month = (calendar.clone() as Calendar).apply {
                add(Calendar.MONTH, -monthsAgo)
            }
            val year = month.get(Calendar.YEAR)
            val monthOfYear = month.get(Calendar.MONTH)
            val count = deliveryTimes.count { time ->
                val c = Calendar.getInstance().apply { timeInMillis = time }
                c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == monthOfYear
            }
            MonthStat(label = monthFormat.format(month.time), count = count)
        }
    }

    private companion object {
        const val MONTHS_SHOWN = 6
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_PLAUSIBLE_DELIVERY_DAYS = 365.0
    }
}
