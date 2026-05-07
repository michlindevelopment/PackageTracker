package com.michlind.packagetracker.ui.home

import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.michlind.packagetracker.domain.model.SortMode
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.ui.aliimport.BgAliImportWebView
import com.michlind.packagetracker.ui.components.EmptyState
import com.michlind.packagetracker.ui.components.PackageCard
import com.michlind.packagetracker.ui.components.StatusBadge
import com.michlind.packagetracker.ui.components.colorAndIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPackageClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignInToAliExpress: () -> Unit,
    onVerifyCaptcha: (trackingNumber: String) -> Unit,
    refreshAndShowInTransit: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val activeGroups by viewModel.activeGroups.collectAsStateWithLifecycle()
    val receivedGroups by viewModel.receivedGroups.collectAsStateWithLifecycle()
    val notYetSent by viewModel.notYetSentPackages.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshingTn by viewModel.refreshingTrackingNumber.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val captchaTrackingNumber by viewModel.captchaTrackingNumber.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val bgImportActive by viewModel.bgImportActive.collectAsStateWithLifecycle()
    val bgImportProgress by viewModel.bgImportProgress.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Default to "In Transit" (page 1) on cold start; rememberPagerState
    // is rememberSaveable internally so it survives Detail navigation.
    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    // Long-press action menu state — single source of truth for both groups and standalone packages
    var actionMenuGroup by remember { mutableStateOf<PackageGroup?>(null) }
    var actionMenuPkg by remember { mutableStateOf<TrackedPackage?>(null) }
    // Picker shown when the FAB is tapped: choose auto-import vs. manual add.
    var showAddOptions by remember { mutableStateOf(false) }
    // Picker shown when the Refresh icon is tapped: choose between just
    // syncing carrier status, a quick AliExpress fetch + sync, or a full
    // re-scan + sync.
    var showRefreshOptions by remember { mutableStateOf(false) }
    // Delete-confirmation state, shown only after the user picks "Delete" from the menu
    var pendingDeleteGroup by remember { mutableStateOf<PackageGroup?>(null) }
    var pendingDeletePkg by remember { mutableStateOf<TrackedPackage?>(null) }
    // Mark-received-confirmation state, shown only after the user picks the toggle option
    var pendingToggleGroup by remember { mutableStateOf<PackageGroup?>(null) }
    var pendingTogglePkg by remember { mutableStateOf<TrackedPackage?>(null) }
    var pendingToggleTarget by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage, captchaTrackingNumber) {
        val msg = errorMessage ?: return@LaunchedEffect
        // If the error came from Cainiao's bot wall, give the user a way to
        // resolve it: a "Verify" action that opens the captcha WebView.
        // Otherwise it's a plain dismissable error.
        val tn = captchaTrackingNumber
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = if (tn != null) "Verify" else null,
            duration = if (tn != null) SnackbarDuration.Long else SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && tn != null) {
            onVerifyCaptcha(tn)
        }
        viewModel.clearError()
    }

    // Triggered after AliExpress login completes. Switch to the In Transit
    // tab and run a Full fetch + status sync — the user just signed in, so
    // we want to pull every order (catching tracking-number changes for
    // pre-existing imports) and refresh carrier statuses afterwards. The
    // bg-import banner above the list shows live progress regardless of tab.
    LaunchedEffect(refreshAndShowInTransit) {
        if (refreshAndShowInTransit) {
            pagerState.animateScrollToPage(1)
            viewModel.fullFetchThenSyncStatus()
            onRefreshConsumed()
        }
    }

    // Update-available popup. Shown on cold launch when GitHub has a newer
    // release than the installed version. "Update" routes to Settings —
    // SettingsScreen already auto-runs checkForUpdates() on entry, lands on
    // the Available state, and shows the big Update button (which handles
    // install permission, download progress, etc.). "Later" dismisses for
    // this app session; next cold launch re-checks.
    val update = updateAvailable
    if (update != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update available") },
            text = {
                Text("Version ${update.latestVersion} is available.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdate()
                    onSettingsClick()
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) {
                    Text("Will do it later")
                }
            }
        )
    }

    // Action menu — appears on long-press; lets the user choose Delete or toggle received
    if (actionMenuGroup != null || actionMenuPkg != null) {
        val isReceived = actionMenuGroup?.packages?.all { it.isReceived }
            ?: actionMenuPkg?.isReceived
            ?: false
        val toggleLabel = if (isReceived)
            stringResource(R.string.mark_as_not_received)
        else
            stringResource(R.string.mark_as_received)
        val menuTitle = actionMenuGroup?.displayName
            ?: actionMenuPkg?.name?.ifBlank { null }
            ?: actionMenuPkg?.trackingNumber?.ifBlank { null }
            ?: stringResource(R.string.add_package)
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = menuTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                BottomSheetActionRow(
                    icon = if (isReceived) Icons.Default.Replay else Icons.Default.CheckCircle,
                    title = toggleLabel,
                    subtitle = if (isReceived) "Move back to In Transit"
                               else "Mark as delivered and move to Received",
                    onClick = {
                        val group = actionMenuGroup
                        val pkg = actionMenuPkg
                        actionMenuGroup = null
                        actionMenuPkg = null
                        pendingToggleTarget = !isReceived
                        if (group != null) pendingToggleGroup = group
                        else pkg?.let { pendingTogglePkg = it }
                    }
                )
                BottomSheetActionRow(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.delete),
                    subtitle = "Remove from your list",
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        val group = actionMenuGroup
                        val pkg = actionMenuPkg
                        actionMenuGroup = null
                        actionMenuPkg = null
                        if (group != null) pendingDeleteGroup = group
                        else pkg?.let { pendingDeletePkg = it }
                    }
                )
            }
        }
    }

    // Mark-received confirmation dialog — second step after picking the toggle from the action menu
    if (pendingToggleGroup != null || pendingTogglePkg != null) {
        val newState = pendingToggleTarget
        val titleRes = if (newState) R.string.confirm_mark_received_title
                       else R.string.confirm_mark_not_received_title
        val msgRes = if (newState) R.string.confirm_mark_received_message
                     else R.string.confirm_mark_not_received_message
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(msgRes)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val group = pendingToggleGroup
                    val pkg = pendingTogglePkg
                    pendingToggleGroup = null
                    pendingTogglePkg = null
                    if (group != null) viewModel.toggleGroupReceived(group, newState)
                    else pkg?.let { viewModel.toggleReceived(it.id, newState) }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingDeleteGroup != null || pendingDeletePkg != null) {
        val count = pendingDeleteGroup?.packages?.size ?: 1
        // Capture string resources here — @Composable scope — before passing to onClick lambdas
        val deletedMsg = if (count > 1) "$count packages deleted" else stringResource(R.string.package_deleted)
        val undoLabel = stringResource(R.string.undo)
        AlertDialog(
            onDismissRequest = { },
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

    if (showAddOptions) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAddOptions = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(R.string.add_package),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                BottomSheetActionRow(
                    icon = Icons.Default.Edit,
                    title = "Add manually",
                    subtitle = "Enter a tracking number yourself",
                    onClick = {
                        showAddOptions = false
                        onAddClick()
                    }
                )
                BottomSheetActionRow(
                    icon = Icons.Default.ShoppingCart,
                    title = "Sign in to AliExpress",
                    subtitle = "Pull your recent orders automatically",
                    onClick = {
                        showAddOptions = false
                        onSignInToAliExpress()
                    }
                )
            }
        }
    }

    if (showRefreshOptions) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showRefreshOptions = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Refresh",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                BottomSheetActionRow(
                    icon = Icons.Default.Sync,
                    title = "Sync tracking status",
                    subtitle = "Update delivery progress for tracked packages",
                    onClick = {
                        showRefreshOptions = false
                        viewModel.syncStatus()
                    }
                )
                BottomSheetActionRow(
                    icon = Icons.Default.Bolt,
                    title = "Quick",
                    subtitle = "Pull new AliExpress orders",
                    onClick = {
                        showRefreshOptions = false
                        viewModel.quickFetchThenSyncStatus()
                    }
                )
                BottomSheetActionRow(
                    icon = Icons.Default.CloudSync,
                    title = "Full",
                    subtitle = "Re-scan every order — catches tracking numbers " +
                        "that AliExpress changed after shipping",
                    onClick = {
                        showRefreshOptions = false
                        viewModel.fullFetchThenSyncStatus()
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // Hide the refresh icon entirely when the user has no
                    // packages anywhere (To Ship + In Transit + Received all
                    // empty) — there's nothing to sync. Sign-in via the FAB
                    // is the path to bring orders in for the first time.
                    val hasAnyPackages = activeGroups.isNotEmpty() ||
                        receivedGroups.isNotEmpty() ||
                        notYetSent.isNotEmpty()
                    if (hasAnyPackages) {
                        IconButton(
                            onClick = { if (!isRefreshing) showRefreshOptions = true },
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
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort"
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            SortMenuItem("Closest to delivery", SortMode.CLOSEST_TO_DELIVERY, sortMode) {
                                viewModel.setSortMode(it); sortMenuOpen = false
                            }
                            SortMenuItem("Last shipped", SortMode.LAST_SHIPPED, sortMode) {
                                viewModel.setSortMode(it); sortMenuOpen = false
                            }
                            SortMenuItem("First shipped", SortMode.FIRST_SHIPPED, sortMode) {
                                viewModel.setSortMode(it); sortMenuOpen = false
                            }
                            SortMenuItem("A → Z", SortMode.A_TO_Z, sortMode) {
                                viewModel.setSortMode(it); sortMenuOpen = false
                            }
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            // Dim and ignore taps while a bg import is running — adding a
            // new package mid-import would race the import's own writes
            // and just confuses the bg-import banner counters.
            FloatingActionButton(
                modifier = Modifier.alpha(if (bgImportActive) 0.5f else 1f),
                onClick = onClick@{
                    if (bgImportActive) return@onClick
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // If the user is already signed in to AliExpress, the
                    // "Sign in" option in the sheet has nothing to offer —
                    // background sync handles new orders automatically. Skip
                    // the sheet and go straight to manual add. Same loose
                    // sign=y heuristic used elsewhere in the app.
                    val cookies = CookieManager.getInstance()
                        .getCookie("https://www.aliexpress.com").orEmpty()
                    val signedIn = cookies.contains("sign=y")
                    if (signedIn) {
                        onAddClick()
                    } else {
                        showAddOptions = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_package))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Sits behind the Column below — children added later in a Box
            // are drawn on top, so the visible UI stays interactive while
            // this 1×1 alpha-0 WebView quietly runs the import script.
            if (bgImportActive) {
                BgAliImportWebView(
                    bridge = viewModel.bgBridge,
                    onSkipped = viewModel::onBgImportSkipped,
                    onError = viewModel::onBgImportError,
                    onAborted = viewModel::onBgImportAborted,
                    prepare = viewModel::prepareBgImport
                )
            }
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.tab_not_yet_sent)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.tab_in_transit)) }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.tab_received)) }
                )
            }

            // Cache the last non-null progress so the slide-out animation
            // still has content to render after the chain clears the flow.
            var lastProgress by remember { mutableStateOf<BgImportProgress?>(null) }
            LaunchedEffect(bgImportProgress) {
                bgImportProgress?.let { lastProgress = it }
            }
            AnimatedVisibility(
                visible = bgImportProgress != null,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(durationMillis = 240)
                ) + fadeIn(animationSpec = tween(durationMillis = 240)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 200)
                ) + fadeOut(animationSpec = tween(durationMillis = 200))
            ) {
                lastProgress?.let { BgImportProgressBanner(it) }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> NotYetSentList(
                        packages = notYetSent,
                        onPackageClick = onPackageClick,
                        onLongPress = { actionMenuPkg = it },
                        refreshingTrackingNumber = refreshingTn
                    )
                    1 -> GroupList(
                        groups = activeGroups,
                        emptyIcon = R.drawable.ic_empty_transit,
                        emptyTitle = stringResource(R.string.empty_in_transit),
                        emptySubtitle = stringResource(R.string.empty_in_transit_sub),
                        onPackageClick = onPackageClick,
                        onLongPress = { actionMenuGroup = it },
                        refreshingTrackingNumber = refreshingTn
                    )
                    2 -> GroupList(
                        groups = receivedGroups,
                        emptyIcon = R.drawable.ic_empty_received,
                        emptyTitle = stringResource(R.string.empty_received),
                        emptySubtitle = stringResource(R.string.empty_received_sub),
                        onPackageClick = onPackageClick,
                        onLongPress = { actionMenuGroup = it },
                        refreshingTrackingNumber = refreshingTn
                    )
                }
            }
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
    onLongPress: (PackageGroup) -> Unit,
    refreshingTrackingNumber: String?
) {
    if (groups.isEmpty()) {
        EmptyState(iconRes = emptyIcon, title = emptyTitle, subtitle = emptySubtitle)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(groups, key = { it.trackingNumber }) { group ->
                PackageGroupCard(
                    group = group,
                    onPackageClick = onPackageClick,
                    onLongClick = { onLongPress(group) },
                    isRefreshing = refreshingTrackingNumber != null &&
                        refreshingTrackingNumber == group.trackingNumber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .animateItem()
                )
            }
        }
    }
}

@Composable
private fun NotYetSentList(
    packages: List<TrackedPackage>,
    onPackageClick: (Long) -> Unit,
    onLongPress: (TrackedPackage) -> Unit,
    refreshingTrackingNumber: String?
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
                PackageCard(
                    pkg = pkg,
                    onClick = { onPackageClick(pkg.id) },
                    onLongClick = { onLongPress(pkg) },
                    isRefreshing = refreshingTrackingNumber != null &&
                        refreshingTrackingNumber == pkg.trackingNumber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .animateItem()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PackageGroupCard(
    group: PackageGroup,
    onPackageClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isRefreshing: Boolean = false
) {
    if (!group.isMultiple) {
        PackageCard(
            pkg = group.packages.first(),
            onClick = { onPackageClick(group.packages.first().id) },
            onLongClick = onLongClick,
            isRefreshing = isRefreshing,
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
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(19.dp), clip = false)
            .clip(RoundedCornerShape(19.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(gradient)
            // Long-press anywhere on the card outside a sub-row deletes the
            // whole group; sub-rows have their own .clickable for tapping into
            // an individual package.
            .combinedClickable(
                onClick = { onPackageClick(first.id) },
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — mirrors PackageCard layout. The outer combinedClickable
            // already handles tap → first package, so the header itself
            // doesn't need an extra clickable.
            // Photo stays 82.dp square and is vertically centered. When font
            // scale grows the column grows, the row gets taller, and the photo
            // just sits centered while title pins to top, bottom row to bottom.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(19.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(statusColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Multi-item header always shows the package icon, never a photo —
                    // sub-rows below show each package's individual photo.
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = statusColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(38.dp)
                    )
                    // Count badge overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .background(statusColor, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
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
                Spacer(Modifier.width(17.dp))
                // Column min-matches the photo's height so a short title still pins
                // to the top and the bottom row to the bottom (aligned with the
                // photo's top and bottom edges); a longer title can grow the column.
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 82.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = 20.sp
                    )
                    first.lastEvent?.let { event ->
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
                        // DateUtils.relativeTime(first.lastUpdated)?.let { updated ->
                        //     Text(
                        //         text = updated,
                        //         style = MaterialTheme.typography.labelSmall,
                        //         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        //     )
                        // }
                        first.daysInTransit?.let { days ->
                            val n = days.filter { it.isDigit() }
                            Text(
                                text = if (n.isNotEmpty()) "${n}d in transit" else days,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        } ?: Spacer(Modifier.width(0.dp))
                        StatusBadge(status = group.status, isRefreshing = isRefreshing)
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                modifier = Modifier.padding(horizontal = 19.dp)
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
            .padding(horizontal = 19.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(53.dp)
                .clip(RoundedCornerShape(12.dp))
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
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = pkg.name.ifBlank { "Package" },
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    mode: SortMode,
    current: SortMode,
    onPick: (SortMode) -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onPick(mode) },
        trailingIcon = if (mode == current) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else null
    )
}

@Composable
private fun BottomSheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}

// Banner shown under the tab row while a Quick / Full sync's AliExpress
// scrape is in progress. Mirrors the manual import overlay's content
// (status text, progress bar, added/upgraded/skipped/failed counters) so
// the user gets the same feedback in the home flow.
@Composable
private fun BgImportProgressBanner(progress: BgImportProgress) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        // Always render the same set of children — spinner+text, progress
        // bar, counters — so the banner has a constant height regardless of
        // which phase we're in. Indeterminate progress while total is
        // unknown; counters show 0 until the first order completes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = progress.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            val total = progress.total
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (progress.current.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                BgImportCounter("Added", progress.added, MaterialTheme.colorScheme.primary)
                BgImportCounter("Upgraded", progress.upgraded, MaterialTheme.colorScheme.tertiary)
                BgImportCounter("Skipped", progress.skipped, MaterialTheme.colorScheme.onSurfaceVariant)
                BgImportCounter("Failed", progress.failed, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BgImportCounter(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
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
