package com.michlind.packagetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.michlind.packagetracker.domain.model.TrackedPackage

/**
 * The photo area of a package, filling whatever box the caller sized and
 * clipped. Shows:
 *  - a mosaic of the item thumbnails for a multi-item order (up to four
 *    tiles; when there are more items the last tile reads "+N"),
 *  - the single photo otherwise, with a small "×N" badge if the order has
 *    several items but only one image is available,
 *  - the package icon when there is no photo at all.
 */
@Composable
fun PackagePhoto(
    pkg: TrackedPackage,
    placeholderTint: Color,
    placeholderSize: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val itemUris = pkg.itemPhotoUris
        when {
            pkg.isMultiItem && itemUris.size > 1 -> ItemMosaic(
                uris = itemUris,
                totalItems = pkg.itemCount
            )

            pkg.photoUri != null -> {
                AsyncImage(
                    model = pkg.photoUri,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
                if (pkg.isMultiItem) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "×${pkg.itemCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            else -> Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = placeholderTint,
                modifier = Modifier.size(placeholderSize)
            )
        }
    }
}

private val TileGap = 1.5.dp

@Composable
private fun ItemMosaic(uris: List<String>, totalItems: Int) {
    val shown = uris.take(4)
    // The fourth tile stands in for every item beyond the first three.
    val hiddenBehindLast = if (totalItems > 4 && shown.size == 4) totalItems - 3 else 0

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(TileGap)
    ) {
        when (shown.size) {
            2 -> {
                Tile(shown[0])
                Tile(shown[1])
            }

            3 -> {
                Tile(shown[0])
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TileGap)
                ) {
                    ColumnTile(shown[1])
                    ColumnTile(shown[2])
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TileGap)
                ) {
                    ColumnTile(shown[0])
                    ColumnTile(shown[2])
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TileGap)
                ) {
                    ColumnTile(shown[1])
                    ColumnTile(shown[3], hiddenCount = hiddenBehindLast)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tile(uri: String) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        modifier = Modifier
            .weight(1f)
            .fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun ColumnScope.ColumnTile(
    uri: String,
    hiddenCount: Int = 0
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (hiddenCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$hiddenCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
