package com.michlind.packagetracker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {

    fun relativeTime(epochMs: Long): String? {
        if (epochMs <= 0L) return null
        val now = System.currentTimeMillis()
        val diff = now - epochMs
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> formatDate(epochMs)
        }
    }

    fun formatDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun daysFromNow(epochMs: Long): Long {
        val diff = epochMs - System.currentTimeMillis()
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    /**
     * Compact duration string for "time spent in this status" labels.
     * Picks the largest non-zero unit (days → hours → minutes) so the
     * label is always one short token like "3d", "5h", "12m".
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0m"
        val days = TimeUnit.MILLISECONDS.toDays(ms)
        if (days >= 1) return "${days}d"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        if (hours >= 1) return "${hours}h"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        return "${minutes.coerceAtLeast(1)}m"
    }
}
