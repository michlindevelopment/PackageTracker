package com.michlind.packagetracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.michlind.packagetracker.R
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.ui.components.EmptyState
import com.michlind.packagetracker.ui.components.PackageCard
import com.michlind.packagetracker.ui.components.StatusBadge
import com.michlind.packagetracker.ui.components.colorAndIcon
import com.michlind.packagetracker.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPackageClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImportFromAliExpress: () -> Unit,
    refreshAndShowInTransit: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val activeGroups by viewModel.activeGroups.collectAsStateWithLifecycle()
    val receivedGroups by viewModel.receivedGroups.collectAsStateWithLifecycle()
    val notYetSent by viewModel.notYetSentPackages.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Default to "In Transit" (tab 1) on cold start; rememberSaveable preserves
    // the user's choice when navigating to Detail and back.
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }

    // Confirmation dialog state
    var pendingDeleteGroup by remember { mutableStateOf<PackageGroup?>(null) }
    var pendingDeletePkg by remember { mutableStateOf<TrackedPackage?>(null) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage!!)
            viewModel.clearError()
        }
    }

    // Triggered when the user finishes an AliExpress import — switch to the
    // In Transit tab and refresh tracking statuses for the newly-imported items.
    LaunchedEffect(refreshAndShowInTransit) {
        if (refreshAndShowInTransit) {
            selectedTab = 1
            viewModel.refreshAll()
            onRefreshConsumed()
        }
    }

    if (pendingDeleteGroup != null || pendingDeletePkg != null) {
        val count = pendingDeleteGroup?.packages?.size ?: 1
        // Capture string resources here — @Composable scope — before passing to onClick lambdas
        val deletedMsg = if (count > 1) "$count packages deleted" else stringResource(R.string.package_deleted)
        val undoLabel = stringResource(R.string.undo)
        AlertDialog(
            onDismissRequest = { pendingDeleteGroup = null; pendingDeletePkg = null },
            title = { Text(if (count > 1) "Delete $count packages?" else "Delete package?") },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val group = pendingDeleteGroup
                    val pkg = pendingDeletePkg
                    pendingDeleteGroup = null
                    pendingDeletePkg = null
                    if (group != null) viewModel.deleteGroup(group) else pkg?.let { viewModel.delete(it) }
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = deletedMsg,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                    }
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDeleteGroup = null
                    pendingDeletePkg = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = { if (!isRefreshing) viewModel.refreshAll() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                    IconButton(onClick = onImportFromAliExpress) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = "Import from AliExpress"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAddClick()
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_package))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tab_not_yet_sent)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tab_in_transit)) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.tab_received)) })
            }

            when (selectedTab) {
                0 -> NotYetSentList(
                    packages = notYetSent,
                    onPackageClick = onPackageClick,
                    onDeleteRequested = { pendingDeletePkg = it }
                )
                1 -> GroupList(
                    groups = activeGroups,
                    emptyIcon = R.drawable.ic_empty_transit,
                    emptyTitle = stringResource(R.string.empty_in_transit),
                    emptySubtitle = stringResource(R.string.empty_in_transit_sub),
                    onPackageClick = onPackageClick,
                    onDeleteRequested = { pendingDeleteGroup = it }
                )
                2 -> GroupList(
                    groups = receivedGroups,
                    emptyIcon = R.drawable.ic_empty_received,
                    emptyTitle = stringResource(R.string.empty_received),
                    emptySubtitle = stringResource(R.string.empty_received_sub),
                    onPackageClick = onPackageClick,
                    onDeleteRequested = { pendingDeleteGroup = it }
                )
            }
        }
    }
}

@Composable
private fun GroupList(
    groups: List<PackageGroup>,
    emptyIcon: Int,
    emptyTitle: String,
    emptySubtitle: String,
    onPackageClick: (Long) -> Unit,
    onDeleteRequested: (PackageGroup) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyState(iconRes = emptyIcon, title = emptyTitle, subtitle = emptySubtitle)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(groups, key = { it.trackingNumber }) { group ->
                SwipeToDismissGroupItem(
                    group = group,
                    onPackageClick = onPackageClick,
                    onDeleteRequested = onDeleteRequested,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun NotYetSentList(
    packages: List<TrackedPackage>,
    onPackageClick: (Long) -> Unit,
    onDeleteRequested: (TrackedPackage) -> Unit
) {
    if (packages.isEmpty()) {
        EmptyState(
            iconRes = R.drawable.ic_empty_transit,
            title = stringResource(R.string.empty_not_yet_sent),
            subtitle = stringResource(R.string.empty_not_yet_sent_sub)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(packages, key = { it.id }) { pkg ->
                SwipeToDismissPackageItem(
                    pkg = pkg,
                    onPackageClick = onPackageClick,
                    onDeleteRequested = onDeleteRequested,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

// Bug 2 fix: wrapContentHeight prevents vertical drag; Bug 3 fix: confirmValueChange returns false,
// dialog shown in parent instead of deleting immediately.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissGroupItem(
    group: PackageGroup,
    onPackageClick: (Long) -> Unit,
    onDeleteRequested: (PackageGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteRequested(group)
            }
            false // always snap back — dialog handles the actual deletion
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        PackageGroupCard(
            group = group,
            onPackageClick = onPackageClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissPackageItem(
    pkg: TrackedPackage,
    onPackageClick: (Long) -> Unit,
    onDeleteRequested: (TrackedPackage) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteRequested(pkg)
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        PackageCard(
            pkg = pkg,
            onClick = { onPackageClick(pkg.id) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PackageGroupCard(
    group: PackageGroup,
    onPackageClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!group.isMultiple) {
        PackageCard(
            pkg = group.packages.first(),
            onClick = { onPackageClick(group.packages.first().id) },
            modifier = modifier
        )
        return
    }

    // Flat multi-item card: header matches single PackageCard, then a list of
    // sub-items below with just image + title (status is same across the group).
    val first = group.packages.first()
    val (statusColor, _) = group.status.colorAndIcon()
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
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — mirrors PackageCard layout, clickable opens first package
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPackageClick(first.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(statusColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Multi-item header always shows the package icon, never a photo —
                    // sub-rows below show each package's individual photo.
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = statusColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    // Count badge overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(statusColor, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = group.packages.size.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.surface,
                                fontWeight = FontWeight.Bold
                            )
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
                            text = group.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(status = group.status)
                    }
                    Spacer(Modifier.height(6.dp))
                    first.lastEvent?.let { event ->
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
                            text = DateUtils.relativeTime(first.lastUpdated),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        first.daysInTransit?.let { days ->
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Sub-items — image + title only, no status
            group.packages.forEach { pkg ->
                SubPackageRow(pkg = pkg, onClick = { onPackageClick(pkg.id) })
            }
        }
    }
}

@Composable
private fun SubPackageRow(pkg: TrackedPackage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (pkg.photoUri != null) {
                AsyncImage(
                    model = pkg.photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = pkg.name.ifBlank { "Package" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun PackageGroupCardPreview() {
    com.michlind.packagetracker.ui.theme.PackageTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val pkgs = listOf(
                com.michlind.packagetracker.ui.preview.samplePackage(
                    id = 1,
                    name = "Headphones",
                    trackingNumber = "CNG00811858377424",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.IN_TRANSIT
                ),
                com.michlind.packagetracker.ui.preview.samplePackage(
                    id = 2,
                    name = "Charging cable",
                    trackingNumber = "CNG00811858377424",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.IN_TRANSIT
                ),
                com.michlind.packagetracker.ui.preview.samplePackage(
                    id = 3,
                    name = "Phone case",
                    trackingNumber = "CNG00811858377424",
                    status = com.michlind.packagetracker.domain.model.PackageStatus.IN_TRANSIT
                )
            )
            PackageGroupCard(
                group = PackageGroup(
                    trackingNumber = pkgs.first().trackingNumber,
                    packages = pkgs,
                    status = pkgs.first().status,
                    lastUpdated = pkgs.maxOf { it.lastUpdated }
                ),
                onPackageClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
