package com.michlind.packagetracker.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.Money
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

/** One bar of the "Spent per month" chart. */
data class SpendMonth(
    val label: String,
    val amount: Double,
    val orders: Int
)

data class StoreStat(
    val name: String,
    val orders: Int,
    // Spent with this store in the main currency; 0 when no order has a price.
    val spent: Double
)

data class BigOrder(
    val name: String,
    val amount: Double
)

/** The order that has been waiting the longest, by days since it was placed. */
data class WaitingStat(
    val name: String,
    val days: Int
)

data class StatsUiState(
    // True only until the first flow emission arrives, so the screen can
    // avoid flashing the "no packages" empty state during the Room load.
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val receivedCount: Int = 0,
    val inProgressCount: Int = 0,
    // Every product across all orders (a multi-item order counts each item).
    val totalItems: Int = 0,
    val storeCount: Int = 0,
    // Only statuses that actually occur, most common first.
    val statusCounts: List<Pair<PackageStatus, Int>> = emptyList(),
    // Last 6 calendar months, oldest first, current month last.
    val deliveredPerMonth: List<MonthStat> = emptyList(),

    // ── Delivery time: order date to delivery, over received packages whose
    // order date is known. [deliverySample] says how many that is.
    val averageDeliveryDays: Double? = null,
    val medianDeliveryDays: Double? = null,
    val fastestDeliveryDays: Double? = null,
    val slowestDeliveryDays: Double? = null,
    val deliverySample: Int = 0,

    // ── Spending. Totals cover the main currency only (the one most orders
    // use); [otherCurrencyOrders] counts the priced orders left out.
    val currencyToken: String? = null,
    val pricedOrders: Int = 0,
    val otherCurrencyOrders: Int = 0,
    val spentTotal: Double = 0.0,
    val spentThisMonth: Double = 0.0,
    val spentLastMonth: Double = 0.0,
    val ordersThisMonth: Int = 0,
    val averageOrderValue: Double = 0.0,
    // Last 12 calendar months, oldest first, current month last.
    val spentPerMonth: List<SpendMonth> = emptyList(),
    val topStores: List<StoreStat> = emptyList(),
    val biggestOrders: List<BigOrder> = emptyList(),

    // ── Orders still on the way
    val longestWaiting: WaitingStat? = null,
    val averageWaitingDays: Double? = null
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

        val deliveryTimes = received.map { it.deliveryTimestamp() }.filter { it > 0 }

        // ── Delivery time ────────────────────────────────────────────────
        val deliveryDays = received
            .mapNotNull { pkg ->
                // createdAt <= 0 means the order date never parsed; a delta
                // against epoch 1970 would add ~20,000 days to the average.
                if (pkg.createdAt <= 0) return@mapNotNull null
                val days = (pkg.deliveryTimestamp() - pkg.createdAt).toDouble() / DAY_MS
                // Plausibility window: nothing ships for over a year, so wilder
                // deltas are bad data, not slow packages.
                days.takeIf { it in 0.0..MAX_PLAUSIBLE_DELIVERY_DAYS }
            }
            .sorted()

        // ── Spending (main currency only) ─────────────────────────────────
        val priced = all.filter { (it.priceAmount ?: 0.0) > 0.0 }
        val byCurrency = priced.groupBy { Money.canonical(it.priceCurrency) }
        val main = byCurrency.values.maxByOrNull { it.size }.orEmpty()
        val currencyToken = main.firstOrNull { !it.priceCurrency.isNullOrBlank() }?.priceCurrency
        val spentTotal = main.sumOf { it.priceAmount ?: 0.0 }

        val now = Calendar.getInstance()
        val thisMonthKey = monthKey(now.timeInMillis)
        val lastMonthKey = thisMonthKey - 1
        val mainDated = main.filter { it.createdAt > 0 }
        val spentThisMonth = mainDated
            .filter { monthKey(it.createdAt) == thisMonthKey }
            .sumOf { it.priceAmount ?: 0.0 }
        val spentLastMonth = mainDated
            .filter { monthKey(it.createdAt) == lastMonthKey }
            .sumOf { it.priceAmount ?: 0.0 }
        val ordersThisMonth = all.count { it.createdAt > 0 && monthKey(it.createdAt) == thisMonthKey }

        val topStores = all
            .filter { !it.storeName.isNullOrBlank() }
            .groupBy { it.storeName!!.trim() }
            .map { (name, list) ->
                StoreStat(
                    name = name,
                    orders = list.size,
                    spent = list
                        .filter { Money.canonical(it.priceCurrency) == Money.canonical(currencyToken) }
                        .sumOf { it.priceAmount ?: 0.0 }
                )
            }
            .sortedWith(compareByDescending<StoreStat> { it.spent }.thenByDescending { it.orders })
            .take(TOP_LIST_SIZE)

        val biggestOrders = main
            .sortedByDescending { it.priceAmount ?: 0.0 }
            .take(BIGGEST_ORDERS_SHOWN)
            .map { BigOrder(name = it.name.ifBlank { "Package" }, amount = it.priceAmount ?: 0.0) }

        // ── Orders still on the way ───────────────────────────────────────
        val waiting = inProgress
            .filter { it.createdAt > 0 }
            .map { it to ((System.currentTimeMillis() - it.createdAt) / DAY_MS).toInt() }
            .filter { it.second >= 0 }

        return StatsUiState(
            isLoading = false,
            totalCount = all.size,
            receivedCount = received.size,
            inProgressCount = inProgress.size,
            totalItems = all.sumOf { it.itemCount.coerceAtLeast(1) },
            storeCount = all.mapNotNull { it.storeName?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .distinct().size,
            statusCounts = statusCounts,
            deliveredPerMonth = deliveredPerMonth(deliveryTimes),
            averageDeliveryDays = deliveryDays.takeIf { it.isNotEmpty() }?.average()?.round1(),
            medianDeliveryDays = median(deliveryDays)?.round1(),
            fastestDeliveryDays = deliveryDays.firstOrNull()?.round1(),
            slowestDeliveryDays = deliveryDays.lastOrNull()?.round1(),
            deliverySample = deliveryDays.size,
            currencyToken = currencyToken,
            pricedOrders = priced.size,
            otherCurrencyOrders = priced.size - main.size,
            spentTotal = spentTotal,
            spentThisMonth = spentThisMonth,
            spentLastMonth = spentLastMonth,
            ordersThisMonth = ordersThisMonth,
            averageOrderValue = if (main.isEmpty()) 0.0 else spentTotal / main.size,
            spentPerMonth = spentPerMonth(mainDated),
            topStores = topStores,
            biggestOrders = biggestOrders,
            longestWaiting = waiting.maxByOrNull { it.second }
                ?.let { (pkg, days) -> WaitingStat(pkg.name.ifBlank { "Package" }, days) },
            averageWaitingDays = waiting.takeIf { it.isNotEmpty() }
                ?.map { it.second.toDouble() }?.average()?.round1()
        )
    }

    // Best guess for when a received package was actually delivered: its
    // newest tracking event, else the moment it was marked received, else the
    // last time we saw it change. Takes the newest event by time rather than
    // trusting list order.
    private fun TrackedPackage.deliveryTimestamp(): Long =
        events.maxOfOrNull { it.time }?.takeIf { it > 0 }
            ?: receivedAt.takeIf { it > 0 }
            ?: lastUpdated

    private fun monthKey(time: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    private fun median(sorted: List<Double>): Double? = when {
        sorted.isEmpty() -> null
        sorted.size % 2 == 1 -> sorted[sorted.size / 2]
        else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }

    // Round to 1 decimal for display.
    private fun Double.round1(): Double = (this * 10).roundToInt() / 10.0

    // Buckets [deliveryTimes] into the last 6 calendar months (including the
    // current one), oldest first, labelled with the short month name.
    private fun deliveredPerMonth(deliveryTimes: List<Long>): List<MonthStat> {
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val calendar = Calendar.getInstance()
        return (DELIVERED_MONTHS_SHOWN - 1 downTo 0).map { monthsAgo ->
            val month = (calendar.clone() as Calendar).apply {
                add(Calendar.MONTH, -monthsAgo)
            }
            val key = month.get(Calendar.YEAR) * 12 + month.get(Calendar.MONTH)
            val count = deliveryTimes.count { time -> monthKey(time) == key }
            MonthStat(label = monthFormat.format(month.time), count = count)
        }
    }

    // Spend per calendar month of the order date, last 12 months, oldest first.
    private fun spentPerMonth(datedPriced: List<TrackedPackage>): List<SpendMonth> {
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val calendar = Calendar.getInstance()
        return (SPEND_MONTHS_SHOWN - 1 downTo 0).map { monthsAgo ->
            val month = (calendar.clone() as Calendar).apply {
                add(Calendar.MONTH, -monthsAgo)
            }
            val key = month.get(Calendar.YEAR) * 12 + month.get(Calendar.MONTH)
            val inMonth = datedPriced.filter { monthKey(it.createdAt) == key }
            SpendMonth(
                label = monthFormat.format(month.time),
                amount = inMonth.sumOf { it.priceAmount ?: 0.0 },
                orders = inMonth.size
            )
        }
    }

    private companion object {
        const val DELIVERED_MONTHS_SHOWN = 6
        const val SPEND_MONTHS_SHOWN = 12
        const val TOP_LIST_SIZE = 5
        const val BIGGEST_ORDERS_SHOWN = 3
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_PLAUSIBLE_DELIVERY_DAYS = 365.0
    }
}
