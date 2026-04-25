package com.michlind.packagetracker.ui.attach

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun AttachImageSheet(
    sharedImageUri: Uri,
    onDismiss: () -> Unit,
    viewModel: AttachImageViewModel = hiltViewModel()
) {
    val allPackages by viewModel.allPackages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingPackageId by remember { mutableStateOf<Long?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val pkgId = pendingPackageId
        if (result.isSuccessful && pkgId != null) {
            result.uriContent?.let { viewModel.attachImage(pkgId, it.toString()) }
            onDismiss()
        }
        pendingPackageId = null
    }

    fun attachWithCrop(pkg: TrackedPackage) {
        pendingPackageId = pkg.id
        cropLauncher.launch(
            CropImageContractOptions(
                uri = sharedImageUri,
                cropImageOptions = CropImageOptions(
                    fixAspectRatio = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    outputRequestWidth = 1024,
                    outputRequestHeight = 1024,
                    activityBackgroundColor = android.graphics.Color.BLACK,
                    activityMenuIconColor = android.graphics.Color.WHITE,
                    activityMenuTextColor = android.graphics.Color.WHITE
                )
            )
        )
    }

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Header with small image thumbnail
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = sharedImageUri,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        contentScale = ContentScale.Crop
                    )
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("Attach image to package", style = MaterialTheme.typography.titleMedium)
                    Text("Select a package below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search packages…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
            }

            val filtered = allPackages.filter { pkg ->
                query.isBlank() ||
                    pkg.name.contains(query, ignoreCase = true) ||
                    pkg.trackingNumber.contains(query, ignoreCase = true)
            }

            LazyColumn {
                items(filtered, key = { it.id }) { pkg ->
                    PackageRow(pkg = pkg, onClick = { attachWithCrop(pkg) })
                }
            }
        }
    }
}

@Composable
private fun PackageRow(pkg: TrackedPackage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (pkg.photoUri != null) {
                AsyncImage(
                    model = pkg.photoUri,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.name.ifBlank { pkg.trackingNumber.ifBlank { "Package #${pkg.id}" } },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StatusBadge(status = pkg.status)
        }
    }
}
