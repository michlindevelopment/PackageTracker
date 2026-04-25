package com.michlind.packagetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.util.DateUtils
import androidx.compose.material3.Icon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PackageCard(
    pkg: TrackedPackage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val (statusColor, _) = pkg.status.colorAndIcon()
    val gradient = Brush.linearGradient(
        colors = listOf(
            statusColor.copy(alpha = 0.18f),
            statusColor.copy(alpha = 0.04f)
        ),
        start = Offset(0f, 0f),
        end = Offset(600f, 300f)
    )

    Box(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(gradient)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Photo or placeholder
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(statusColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                if (pkg.photoUri != null) {
                    AsyncImage(
                        model = pkg.photoUri,
                        contentDescription = "Package photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = statusColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = pkg.name.ifBlank { pkg.trackingNumber },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(status = pkg.status)
                }

                Spacer(Modifier.height(6.dp))

                pkg.lastEvent?.let { event ->
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = DateUtils.relativeTime(pkg.lastUpdated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    pkg.daysInTransit?.let { days ->
                        Text(
                            text = days,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PackageCardPreview() {
    com.michlind.packagetracker.ui.theme.PackageTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PackageCard(
                pkg = com.michlind.packagetracker.ui.preview.samplePackage(
                    name = "AliExpress Headphones",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.IN_TRANSIT
                ),
                onClick = {}
            )
            PackageCard(
                pkg = com.michlind.packagetracker.ui.preview.samplePackage(
                    id = 2,
                    name = "Phone case",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.OUT_FOR_DELIVERY,
                    daysInTransit = "21 days"
                ),
                onClick = {}
            )
            PackageCard(
                pkg = com.michlind.packagetracker.ui.preview.samplePackage(
                    id = 3,
                    name = "Charging cable",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.DELIVERED,
                    daysInTransit = null
                ),
                onClick = {}
            )
            PackageCard(
                pkg = com.michlind.packagetracker.ui.preview.samplePackage(
                    id = 4,
                    name = "USB hub",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.ORDER_PLACED,
                    daysInTransit = "1 day",
                    lastEvent = com.michlind.packagetracker.ui.preview.sampleEvent(
                        description = "Awaiting seller dispatch",
                        actionCode = ""
                    )
                ),
                onClick = {}
            )
        }
    }
}
