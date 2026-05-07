package com.michlind.packagetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.michlind.packagetracker.domain.model.TrackingEvent
import com.michlind.packagetracker.util.DateUtils

@Composable
fun TimelineItem(
    event: TrackingEvent,
    isFirst: Boolean,
    isLast: Boolean,
    // Time (epoch ms) of the next chronological event, i.e. the carrier
    // step that *followed* this one. Null means there's no follow-up step
    // yet — typically the latest event in the list — and the duration is
    // measured against "now".
    nextEventTime: Long?,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val divider = MaterialTheme.colorScheme.outlineVariant

    // The latest event (top of the list) wears the "current" badge from the
    // carrier; everything below it is past history. When the carrier didn't
    // give us icons for this trace we fall back to the legacy colored dot.
    val iconUrl = if (isFirst) event.groupCurrentIconUrl else event.groupHistoryIconUrl
    val markerSize = if (isFirst) 26.dp else 22.dp

    // Time spent in this stage — measured against the next chronological
    // step (or "now" if this is still the latest event). Rendered as a
    // compact label directly under the icon in the timeline column.
    val endTime = nextEventTime ?: System.currentTimeMillis()
    val durationLabel = DateUtils.formatDuration((endTime - event.time).coerceAtLeast(0L))

    Row(
        modifier = modifier
            .fillMaxWidth()
            // IntrinsicSize.Min lets the timeline column's `fillMaxHeight`
            // know the row height (set by the content column's intrinsic
            // measurement). Without this, weight(1f) on the connectors
            // can't compute and the marker collapses to the top.
            .height(IntrinsicSize.Min)
    ) {
        // Timeline column (line ─ marker ─ duration ─ line). The icon and
        // duration sit between two weight(1f) connector boxes, so they're
        // vertically centered against the content on the right.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
        ) {
            // Top connector — empty for the first row, line for the rest
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else divider)
            )
            // Marker — carrier-supplied icon when available, else colored dot
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(markerSize)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(if (isFirst) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(if (isFirst) accent else divider)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = subtle,
                fontSize = 10.sp
            )
            // Bottom connector — empty for the last row, line for the rest
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else divider)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Content — heightIn enforces a uniform row height so short events
        // (one-line description) don't render shorter than longer ones.
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 80.dp)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isFirst) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isFirst) onSurface else subtle,
                fontSize = 14.sp
            )
            if (event.standardDescription.isNotBlank() &&
                event.standardDescription != event.description
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.standardDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtle,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = DateUtils.formatDateTime(event.time),
                style = MaterialTheme.typography.labelSmall,
                color = subtle,
                fontSize = 11.sp
            )
        }
    }
}
