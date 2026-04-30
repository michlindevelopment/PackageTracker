package com.michlind.packagetracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.ui.theme.StatusAwaitingPickup
import com.michlind.packagetracker.ui.theme.StatusCustoms
import com.michlind.packagetracker.ui.theme.StatusCustomsExport
import com.michlind.packagetracker.ui.theme.StatusCustomsImport
import com.michlind.packagetracker.ui.theme.StatusDelivered
import com.michlind.packagetracker.ui.theme.StatusException
import com.michlind.packagetracker.ui.theme.StatusInTransit
import com.michlind.packagetracker.ui.theme.StatusNotYetSent
import com.michlind.packagetracker.ui.theme.StatusOrderPlaced
import com.michlind.packagetracker.ui.theme.StatusOutForDelivery
import com.michlind.packagetracker.ui.theme.StatusShipped
import com.michlind.packagetracker.ui.theme.StatusUnknown

@Composable
fun StatusBadge(
    status: PackageStatus,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false
) {
    val (color, icon) = status.colorAndIcon()
    if (isRefreshing) {
        // Replace the text with the same status icon animating left → right
        // (a "moving forward" hint) while THIS package is being refreshed.
        // An invisible Text with the same style as the non-refreshing branch
        // reserves the badge's height so the surrounding row doesn't reflow
        // when refresh toggles.
        BoxWithConstraints(
            modifier = modifier
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .width(64.dp)
        ) {
            Text(
                text = " ",
                fontSize = 11.sp,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(0f)
            )
            val transition = rememberInfiniteTransition(label = "status_anim")
            val fraction by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = LinearEasing)
                ),
                label = "status_anim_x"
            )
            val iconSize = 14.dp
            val travel = maxWidth + iconSize
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = (travel * fraction) - iconSize)
                    .align(Alignment.CenterStart)
            )
        }
    } else {
        Row(
            modifier = modifier
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = status.displayName,
                color = color,
                fontSize = 11.sp,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun StatusBadgePreview() {
    com.michlind.packagetracker.ui.theme.PackageTrackerTheme(dynamicColor = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            PackageStatus.entries.forEach { status ->
                StatusBadge(status = status)
            }
        }
    }
}

fun PackageStatus.colorAndIcon(): Pair<Color, ImageVector> = when (this) {
    PackageStatus.NOT_YET_SENT     -> StatusNotYetSent     to Icons.Default.HourglassEmpty
    PackageStatus.ORDER_PLACED     -> StatusOrderPlaced    to Icons.Default.ShoppingBag
    PackageStatus.SHIPPED          -> StatusShipped        to Icons.Default.LocalPostOffice
    PackageStatus.IN_TRANSIT       -> StatusInTransit      to Icons.Default.FlightTakeoff
    PackageStatus.CUSTOMS_EXPORT   -> StatusCustomsExport  to Icons.Default.ArrowCircleUp
    PackageStatus.CUSTOMS_IMPORT   -> StatusCustomsImport  to Icons.Default.ArrowCircleDown
    PackageStatus.CUSTOMS          -> StatusCustoms        to Icons.Default.GppBad
    PackageStatus.OUT_FOR_DELIVERY -> StatusOutForDelivery to Icons.Default.LocalShipping
    PackageStatus.AWAITING_PICKUP  -> StatusAwaitingPickup to Icons.Default.Inbox
    PackageStatus.DELIVERED        -> StatusDelivered      to Icons.Default.CheckCircle
    PackageStatus.EXCEPTION        -> StatusException      to Icons.Default.Error
    PackageStatus.UNKNOWN          -> StatusUnknown        to Icons.Default.Help
}
