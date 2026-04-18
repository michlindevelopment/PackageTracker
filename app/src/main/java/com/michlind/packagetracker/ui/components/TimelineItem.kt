package com.michlind.packagetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michlind.packagetracker.domain.model.TrackingEvent
import com.michlind.packagetracker.util.DateUtils

@Composable
fun TimelineItem(
    event: TrackingEvent,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val divider = MaterialTheme.colorScheme.outlineVariant

    Row(modifier = modifier.fillMaxWidth()) {
        // Timeline column (dot + line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            // Top connector
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(divider)
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
            // Dot
            Box(
                modifier = Modifier
                    .size(if (isFirst) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) accent else divider)
            )
            // Bottom connector
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f, fill = true)
                        .background(divider)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp)
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
