package com.michlind.packagetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PackageCard(
    pkg: TrackedPackage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val nameTooltipState = rememberTooltipState(isPersistent = true)
    val tooltipScope = rememberCoroutineScope()
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
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(19.dp), clip = false)
            .clip(RoundedCornerShape(19.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(gradient)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Photo stays a fixed 82.dp square and is vertically centered inside
        // the row. When the user increases system font scale, the column's
        // text grows and the row taller than 82.dp — the photo just sits
        // centered while the title pins to the top and the bottom row pins
        // to the bottom of the (taller) column.
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo or placeholder
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(statusColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                if (pkg.photoUri != null) {
                    AsyncImage(
                        model = pkg.photoUri,
                        contentDescription = "Package photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = statusColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Min-height = photo height so a short title still pins the bottom
            // row to the photo's bottom edge; longer / larger-font titles let
            // the column grow naturally past 82.dp.
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 82.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = pkg.name.ifBlank { pkg.trackingNumber },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (pkg.name.isNotBlank()) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
                            tooltip = {
                                RichTooltip {
                                    Text(pkg.name)
                                }
                            },
                            state = nameTooltipState
                        ) {
                            IconButton(
                                onClick = { tooltipScope.launch { nameTooltipState.show() } },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Show full item name",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                pkg.lastEvent?.let { event ->
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Last updated" relative time — hidden for now; keep for future re-enable.
                    // DateUtils.relativeTime(pkg.lastUpdated)?.let { updated ->
                    //     Text(
                    //         text = updated,
                    //         style = MaterialTheme.typography.labelSmall,
                    //         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    //     )
                    // }
                    pkg.daysInTransit?.let { days ->
                        val n = days.filter { it.isDigit() }
                        Text(
                            text = if (n.isNotEmpty()) "${n}d in transit" else days,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    } ?: Spacer(Modifier.width(0.dp))
                    StatusBadge(status = pkg.status)
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
                    name = "AliExpress Headphones with a very long name that should be truncated",
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
