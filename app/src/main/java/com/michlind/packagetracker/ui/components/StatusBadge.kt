package com.michlind.packagetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.ui.theme.StatusCustoms
import com.michlind.packagetracker.ui.theme.StatusDelivered
import com.michlind.packagetracker.ui.theme.StatusException
import com.michlind.packagetracker.ui.theme.StatusInTransit
import com.michlind.packagetracker.ui.theme.StatusOrderPlaced
import com.michlind.packagetracker.ui.theme.StatusOutForDelivery
import com.michlind.packagetracker.ui.theme.StatusShipped
import com.michlind.packagetracker.ui.theme.StatusUnknown

@Composable
fun StatusBadge(status: PackageStatus, modifier: Modifier = Modifier) {
    val (color, icon) = status.colorAndIcon()
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = status.displayName,
            color = color,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

fun PackageStatus.colorAndIcon(): Pair<Color, ImageVector> = when (this) {
    PackageStatus.ORDER_PLACED -> StatusOrderPlaced to Icons.Default.ShoppingBag
    PackageStatus.SHIPPED -> StatusShipped to Icons.Default.FlightTakeoff
    PackageStatus.IN_TRANSIT -> StatusInTransit to Icons.Default.LocalShipping
    PackageStatus.CUSTOMS -> StatusCustoms to Icons.Default.GppBad
    PackageStatus.OUT_FOR_DELIVERY -> StatusOutForDelivery to Icons.Default.LocalShipping
    PackageStatus.DELIVERED -> StatusDelivered to Icons.Default.CheckCircle
    PackageStatus.EXCEPTION -> StatusException to Icons.Default.Error
    PackageStatus.UNKNOWN -> StatusUnknown to Icons.Default.Help
}
