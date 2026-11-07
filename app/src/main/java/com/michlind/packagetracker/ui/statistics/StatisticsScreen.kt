package com.michlind.packagetracker.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.ui.components.colorAndIcon
import com.michlind.packagetracker.util.Money
import kotlin.math.roundToInt

private val MAX_BAR_HEIGHT = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!state.isLoading && state.totalCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No packages yet.\nAdd a package and its stats will show up here.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OverviewCard(state)
            SpendingCard(state)
            SpentPerMonthCard(state)
            DeliveryTimeCard(state)
            DeliveredPerMonthCard(months = state.deliveredPerMonth)
            WaitingCard(state)
            TopStoresCard(state)
            BiggestOrdersCard(state)
            StatusBreakdownCard(statusCounts = state.statusCounts)
        }
    }
}

// ─── Overview ───────────────────────────────────────────────────────────────

@Composable
private fun OverviewCard(state: StatsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat("${state.totalCount}", "Total", Modifier.weight(1f))
                OverviewStat("${state.inProgressCount}", "On the way", Modifier.weight(1f))
                OverviewStat("${state.receivedCount}", "Received", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat("${state.totalItems}", "Items", Modifier.weight(1f))
                OverviewStat("${state.storeCount}", "Stores", Modifier.weight(1f))
                OverviewStat(
                    value = if (state.pricedOrders > 0) {
                        Money.symbolFor(state.currencyToken) + Money.formatCompact(state.spentTotal)
                    } else "-",
                    label = "Spent",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OverviewStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// A smaller value + caption pair, used inside the detail cards.
@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

// ─── Spending ───────────────────────────────────────────────────────────────

@Composable
private fun SpendingCard(state: StatsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            CardTitle("Spending")
            Spacer(Modifier.height(12.dp))

            if (state.pricedOrders == 0) {
                Text(
                    text = "No prices yet. They're read from AliExpress on your next sync, " +
                        "or you can add one when editing a package.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val token = state.currencyToken
            Muted("This month")
            Text(
                text = Money.format(state.spentThisMonth, token),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Muted(monthComparison(state))
            if (state.ordersThisMonth > 0) {
                Muted(
                    text = "${state.ordersThisMonth} order${if (state.ordersThisMonth == 1) "" else "s"} placed this month"
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MiniStat(Money.format(state.spentTotal, token), "Total spent", Modifier.weight(1f))
                MiniStat(Money.format(state.averageOrderValue, token), "Average order", Modifier.weight(1f))
                MiniStat(
                    Money.format(state.biggestOrders.firstOrNull()?.amount ?: 0.0, token),
                    "Biggest order",
                    Modifier.weight(1f)
                )
            }

            // Be upfront about how much of the picture this is.
            val notes = buildList {
                if (state.pricedOrders < state.totalCount) {
                    add("Based on ${state.pricedOrders} of ${state.totalCount} orders that have a price.")
                }
                if (state.otherCurrencyOrders > 0) {
                    add(
                        "${state.otherCurrencyOrders} order${if (state.otherCurrencyOrders == 1) "" else "s"} " +
                            "in another currency not counted."
                    )
                }
            }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Muted(notes.joinToString(" "))
            }
        }
    }
}

// "+12% vs last month", "-30% vs last month", or why there's nothing to compare.
private fun monthComparison(state: StatsUiState): String {
    val last = state.spentLastMonth
    if (last <= 0.0) return "Nothing spent last month"
    val pct = ((state.spentThisMonth - last) / last * 100).roundToInt()
    val sign = if (pct > 0) "+" else ""
    return "$sign$pct% vs last month (${Money.format(last, state.currencyToken)})"
}

@Composable
private fun SpentPerMonthCard(state: StatsUiState) {
    if (state.pricedOrders == 0) return
    // 6 months reads best; 12 shows the whole year.
    var months by rememberSaveable { mutableIntStateOf(6) }
    val shown = state.spentPerMonth.takeLast(months)

    BarChartCard(
        title = "Spent per month",
        labels = shown.map { it.label },
        values = shown.map { it.amount },
        valueLabel = { if (it > 0.0) Money.formatCompact(it) else "" },
        subtitle = "By order date, in ${Money.symbolFor(state.currencyToken).ifBlank { "your currency" }}",
        headerContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = months == 6,
                    onClick = { months = 6 },
                    label = { Text("6 months") }
                )
                FilterChip(
                    selected = months == 12,
                    onClick = { months = 12 },
                    label = { Text("12 months") }
                )
            }
        }
    )
}

// ─── Delivery ───────────────────────────────────────────────────────────────

@Composable
private fun DeliveryTimeCard(state: StatsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Delivery time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            val average = state.averageDeliveryDays
            if (average == null) {
                Text(
                    // The average needs each package's order date. Say so when
                    // there ARE received packages, instead of "none yet".
                    text = if (state.receivedCount == 0) {
                        "No delivered packages yet"
                    } else {
                        "Not enough order dates yet. They fill in as your AliExpress orders sync."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(
                text = formatDays(average),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "days on average",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MiniStat(state.fastestDeliveryDays?.let { "${formatDays(it)} d" } ?: "-", "Fastest", Modifier.weight(1f))
                MiniStat(state.medianDeliveryDays?.let { "${formatDays(it)} d" } ?: "-", "Typical", Modifier.weight(1f))
                MiniStat(state.slowestDeliveryDays?.let { "${formatDays(it)} d" } ?: "-", "Slowest", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Muted(
                text = "From order date to delivery, over ${state.deliverySample} " +
                    "delivered package${if (state.deliverySample == 1) "" else "s"} with a known order date."
            )
        }
    }
}

@Composable
private fun DeliveredPerMonthCard(months: List<MonthStat>) {
    BarChartCard(
        title = "Delivered per month",
        labels = months.map { it.label },
        values = months.map { it.count.toDouble() },
        valueLabel = { "${it.toInt()}" }
    )
}

// "18.0" reads better as "18"; keep one decimal otherwise.
private fun formatDays(days: Double): String =
    if (days % 1.0 == 0.0) "${days.toInt()}" else "$days"

// ─── Still on the way ───────────────────────────────────────────────────────

@Composable
private fun WaitingCard(state: StatsUiState) {
    val longest = state.longestWaiting ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            CardTitle("Still on the way")
            Spacer(Modifier.height(12.dp))
            Muted("Waiting the longest")
            Text(
                text = longest.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${longest.days} day${if (longest.days == 1) "" else "s"} since you ordered it",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            state.averageWaitingDays?.let { average ->
                Spacer(Modifier.height(8.dp))
                Muted("Open orders have waited ${formatDays(average)} days on average")
            }
        }
    }
}

// ─── Stores and biggest orders ──────────────────────────────────────────────

@Composable
private fun TopStoresCard(state: StatsUiState) {
    if (state.topStores.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardTitle("Top stores")
            // Bars follow spend when any store has a price, else order count.
            val bySpend = state.topStores.any { it.spent > 0.0 }
            val max = state.topStores.maxOf { if (bySpend) it.spent else it.orders.toDouble() }
            state.topStores.forEach { store ->
                val value = if (bySpend) store.spent else store.orders.toDouble()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = store.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = buildString {
                                append("${store.orders} order${if (store.orders == 1) "" else "s"}")
                                if (store.spent > 0.0) {
                                    append("  ·  ")
                                    append(Money.format(store.spent, state.currencyToken))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ProportionBar(
                        fraction = if (max > 0) (value / max).toFloat() else 0f,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun BiggestOrdersCard(state: StatsUiState) {
    if (state.biggestOrders.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CardTitle("Biggest orders")
            state.biggestOrders.forEachIndexed { index, order ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = order.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = Money.format(order.amount, state.currencyToken),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Status ─────────────────────────────────────────────────────────────────

@Composable
private fun StatusBreakdownCard(statusCounts: List<Pair<PackageStatus, Int>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardTitle("By status")
            val maxCount = statusCounts.maxOfOrNull { it.second } ?: 0
            statusCounts.forEach { (status, count) ->
                StatusRow(status = status, count = count, maxCount = maxCount)
            }
        }
    }
}

@Composable
private fun StatusRow(status: PackageStatus, count: Int, maxCount: Int) {
    val (color, icon) = status.colorAndIcon()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        ProportionBar(
            fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f,
            color = color
        )
    }
}

// Thin horizontal bar, [fraction] of the full width.
@Composable
private fun ProportionBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(3.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color = color, shape = RoundedCornerShape(3.dp))
        )
    }
}

// ─── Bar chart ──────────────────────────────────────────────────────────────

/**
 * A titled column chart. The last bar (the current month) is drawn in the
 * strong colour and earlier ones softer, so "now" reads at a glance.
 */
@Composable
private fun BarChartCard(
    title: String,
    labels: List<String>,
    values: List<Double>,
    valueLabel: (Double) -> String,
    subtitle: String? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            CardTitle(title)
            subtitle?.let { Muted(it) }
            headerContent?.let {
                Spacer(Modifier.height(10.dp))
                it()
            }
            Spacer(Modifier.height(16.dp))
            val max = values.maxOrNull() ?: 0.0
            // Narrower bars and labels once there are many months to fit.
            val crowded = labels.size > 8
            val barWidth: Dp = if (crowded) 12.dp else 20.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                labels.forEachIndexed { index, label ->
                    val value = values.getOrElse(index) { 0.0 }
                    val isLast = index == labels.lastIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = valueLabel(value),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (crowded) 9.sp else 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(4.dp))
                        val barHeight = if (max > 0) {
                            MAX_BAR_HEIGHT * (value / max).toFloat()
                        } else 0.dp
                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                // Empty months keep a faint 2dp stub so the
                                // baseline reads as a continuous axis.
                                .height(if (barHeight > 2.dp) barHeight else 2.dp)
                                .background(
                                    color = when {
                                        value <= 0.0 -> MaterialTheme.colorScheme.surfaceVariant
                                        isLast -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    },
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = if (crowded) 10.sp else 12.sp
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
