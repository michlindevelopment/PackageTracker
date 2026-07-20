package com.michlind.packagetracker.ui.detail

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.michlind.packagetracker.R
import com.michlind.packagetracker.domain.model.DestCarrierInfo
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingSms
import com.michlind.packagetracker.ui.components.SkeletonDetailHeader
import com.michlind.packagetracker.ui.components.TimelineItem
import com.michlind.packagetracker.ui.components.colorAndIcon
import com.michlind.packagetracker.util.DateUtils

private val STAGE_LABELS = listOf(
    "Order\nPlaced", "Shipped", "Export\nCustoms", "In Flight", "Import\nCustoms", "Delivery", "Delivered"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    packageId: Long,
    onEditClick: (Long) -> Unit,
    onShowRawResponse: () -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val smsList by viewModel.smsList.collectAsStateWithLifecycle()
    val hasSmsPermission by viewModel.hasSmsPermission.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSmsBlockedDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSmsPermission()
        if (granted) {
            // Don't make the user wait for the next syncStatus() to see
            // anything — kick off a one-shot scan for just this TN.
            viewModel.scanSmsForCurrent()
        } else {
            // Sideloaded APKs hit Android's "restricted settings" lock that
            // greys out the Allow toggle in App info; surface the recovery
            // steps + a deep-link to App info so the user isn't stranded.
            showSmsBlockedDialog = true
        }
    }

    LaunchedEffect(packageId) {
        viewModel.load(packageId)
        viewModel.refreshSmsPermission()
    }
    LaunchedEffect(deleted) { if (deleted) onBack() }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.delete(packageId)
                    }
                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSmsBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showSmsBlockedDialog = false },
            title = { Text("SMS access is blocked") },
            text = {
                Text(
                    "Android blocks SMS access for sideloaded apps by default. " +
                        "To enable it:\n\n" +
                        "1. Tap \"Open App info\" below\n" +
                        "2. Tap the ⋮ menu (top-right)\n" +
                        "3. Tap \"Allow restricted settings\"\n" +
                        "4. Open Permissions → SMS → Allow"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSmsBlockedDialog = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text("Open App info") }
            },
            dismissButton = {
                TextButton(onClick = { showSmsBlockedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? DetailUiState.Success)?.pkg?.name
                        ?.ifBlank { null }
                        ?: "Detail"
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isRefreshing) viewModel.refresh(packageId) },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                    IconButton(onClick = { onEditClick(packageId) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_package))
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteDialog = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                // Defer the skeleton by 80ms so the warm path (tap from
                // home — repository cache is hot — Success arrives in
                // ~1-5ms) doesn't flash a shimmer mid-slide. If Success
                // hasn't arrived by 80ms (cold path: deep link, freshly
                // opened) the skeleton fades in normally.
                var showSkeleton by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(80)
                    showSkeleton = true
                }
                if (showSkeleton) {
                    Column(modifier = Modifier.padding(paddingValues)) {
                        repeat(3) { SkeletonDetailHeader() }
                    }
                }
            }

            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, textAlign = TextAlign.Center)
                }
            }

            is DetailUiState.Success -> {
                DetailContent(
                    pkg = state.pkg,
                    isRefreshing = isRefreshing,
                    paddingValues = paddingValues,
                    smsMessages = smsList,
                    hasSmsPermission = hasSmsPermission,
                    onRequestSmsPermission = {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    },
                    onCopyMessage = {
                        snackbarScope.launch {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                        }
                    },
                    onSaveLocalTrackingNumber = { tn ->
                        viewModel.setLocalTrackingNumber(packageId, tn)
                    },
                    onClearLocalTrackingNumber = {
                        viewModel.setLocalTrackingNumber(packageId, null)
                    },
                    onShowRawResponse = onShowRawResponse
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    pkg: TrackedPackage,
    isRefreshing: Boolean,
    paddingValues: PaddingValues,
    smsMessages: List<TrackingSms>,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    onCopyMessage: () -> Unit,
    onSaveLocalTrackingNumber: (String) -> Unit,
    onClearLocalTrackingNumber: () -> Unit,
    onShowRawResponse: () -> Unit
) {
    var showLocalTnDialog by remember { mutableStateOf(false) }

    if (showLocalTnDialog) {
        LocalTrackingNumberDialog(
            initial = pkg.localTrackingNumber.orEmpty(),
            onDismiss = { showLocalTnDialog = false },
            onSave = { tn ->
                showLocalTnDialog = false
                onSaveLocalTrackingNumber(tn)
            }
        )
    }

    val nameTooltipState = rememberTooltipState(isPersistent = true)
    val tooltipScope = rememberCoroutineScope()
    // Tracking, SMS and Courier are always present. The Courier tab shows the
    // Cainiao-reported destination carrier once available, but stays visible
    // beforehand so the local-courier tracking number can always be entered
    // (and so a once-seen carrier survives a reinstall that wiped the DB).
    val trackingIndex = 0
    val smsIndex = 1
    val courierIndex = 2
    val tabCount = 3
    val pagerState = rememberPagerState(pageCount = { tabCount })
    val tabScope = rememberCoroutineScope()

    // Collapse the bulky ETA card + Shipping Progress stepper when the
    // user scrolls down inside a pager page, expand them when scrolling
    // back to the top. Status header stays pinned so the package's
    // identity is always visible. Threshold is small so the collapse
    // feels responsive but doesn't trigger on incidental fling friction.
    var topExpanded by remember { mutableStateOf(true) }
    val collapseScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < -8f && topExpanded) topExpanded = false
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 8f && !topExpanded) topExpanded = true
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(collapseScrollConnection)
    ) {
        // Status header with small square thumbnail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val (color, icon) = pkg.status.colorAndIcon()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = pkg.status.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = color
                    )
                    if (pkg.name.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
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
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Show full item name",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                if (pkg.statusDescription.isNotBlank()) {
                    Text(
                        text = pkg.statusDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (pkg.trackingNumber.isNotBlank()) {
                    Text(
                        text = pkg.trackingNumber,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                val route = listOfNotNull(pkg.originCountry, pkg.destCountry)
                    .filter { it.isNotBlank() }
                    .joinToString(" → ")
                val transitDigits = pkg.daysInTransit?.filter { it.isDigit() }
                val transit = when {
                    !transitDigits.isNullOrEmpty() -> "${transitDigits}d in transit"
                    !pkg.daysInTransit.isNullOrBlank() -> pkg.daysInTransit
                    else -> null
                }
                val summary = listOfNotNull(
                    route.ifBlank { null },
                    transit
                ).joinToString("  ·  ")
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // ETA + Shipping Progress collapse together when the user scrolls
        // inside the tab content; they re-expand when they scroll back up
        // to the top of the page.
        AnimatedVisibility(
            visible = topExpanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 220)) +
                fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 200)) +
                fadeOut(animationSpec = tween(durationMillis = 200))
        ) {
            Column {
                pkg.estimatedDeliveryTime?.let { eta ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.estimated_delivery),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = DateUtils.formatDate(eta),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val daysLeft = DateUtils.daysFromNow(eta)
                                if (daysLeft > 0) {
                                    Text(
                                        text = "In $daysLeft days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                ProgressStepper(
                    currentStatus = pkg.status,
                    onSecretTap = onShowRawResponse,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // Tabs — pinned right under Shipping Progress. Tracking, SMS and
        // Courier are all always shown.
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Tab(
                selected = pagerState.currentPage == trackingIndex,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(trackingIndex) } },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Tracking")
                    }
                }
            )
            Tab(
                selected = pagerState.currentPage == smsIndex,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(smsIndex) } },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        val label = if (smsMessages.isEmpty()) "SMS"
                            else "SMS (${smsMessages.size})"
                        Text(label)
                    }
                }
            )
            Tab(
                selected = pagerState.currentPage == courierIndex,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(courierIndex) } },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Courier")
                    }
                }
            )
        }

        // Swipeable pages — each owns its own LazyColumn so vertical
        // scroll inside one page doesn't affect another's scroll position.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                when (page) {
                    trackingIndex -> trackingHistoryItems(events = pkg.events)
                    smsIndex -> smsItems(
                        trackingNumber = pkg.trackingNumber,
                        messages = smsMessages,
                        hasPermission = hasSmsPermission,
                        onRequestPermission = onRequestSmsPermission,
                        onCopyMessage = onCopyMessage
                    )
                    courierIndex -> courierTabItems(
                        carrier = pkg.destCarrier,
                        localTrackingNumber = pkg.localTrackingNumber,
                        onAddLocal = { showLocalTnDialog = true },
                        onRemoveLocal = onClearLocalTrackingNumber
                    )
                }
            }
        }
    }
        // Refresh indicator — overlayed on top of the content so it doesn't
        // shove the header down by a few dp when it appears mid-refresh.
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ProgressStepper(
    currentStatus: PackageStatus,
    onSecretTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hidden debug shortcut: five taps on this card within ~1.5 s opens the
    // raw-response screen. Reset count whenever the gap between taps is too
    // long so accidental retaps don't accumulate over a long session.
    var tapCount by remember { mutableStateOf(0) }
    var lastTapAt by remember { mutableStateOf(0L) }
    val isException = currentStatus == PackageStatus.EXCEPTION
        || currentStatus == PackageStatus.UNKNOWN
        || currentStatus == PackageStatus.NOT_YET_SENT
    val currentStep = if (isException) 0 else currentStatus.stepIndex.coerceAtLeast(0)

    // Lock the stepper text sizes to a fixed pixel size so they don't grow when
    // the user enlarges the system font scale (the dot/spacing layout is dp-based
    // and would otherwise overflow).
    val density = LocalDensity.current
    val headerSize = with(density) { 12.dp.toSp() }
    val labelSize = with(density) { 9.dp.toSp() }
    val labelLineHeight = with(density) { 12.dp.toSp() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                tapCount = if (now - lastTapAt > 1500L) 1 else tapCount + 1
                lastTapAt = now
                if (tapCount >= 5) {
                    tapCount = 0
                    onSecretTap()
                }
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Shipping Progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = headerSize,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                STAGE_LABELS.forEachIndexed { index, label ->
                    val isActive = index <= currentStep && !isException
                    val accentColor = animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        label = "step_color"
                    ).value
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (index == currentStep && !isException) 20.dp else 14.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < currentStep && !isException) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            fontSize = labelSize,
                            lineHeight = labelLineHeight
                        )
                    }
                }
            }
            // Progress bar beneath dots
            val progress = if (isException || currentStep < 0) 0f
            else (currentStep.toFloat() / (STAGE_LABELS.size - 1))
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50)),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

private fun LazyListScope.trackingHistoryItems(
    events: List<com.michlind.packagetracker.domain.model.TrackingEvent>
) {
    if (events.isEmpty()) {
        item {
            Text(
                text = "No events yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    } else {
        items(events.indices.toList()) { index ->
            val event = events[index]
            // Events are reverse-chronological (newest at index 0), so the
            // *next* chronological step for events[i] is the row above it:
            // events[i - 1]. Top of the list has no next step.
            val nextEventTime = if (index == 0) null else events[index - 1].time
            TimelineItem(
                event = event,
                isFirst = index == 0,
                isLast = index == events.lastIndex,
                nextEventTime = nextEventTime,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

private fun LazyListScope.courierTabItems(
    carrier: DestCarrierInfo?,
    localTrackingNumber: String?,
    onAddLocal: () -> Unit,
    onRemoveLocal: () -> Unit
) {
    item {
        CourierCard(
            carrier = carrier,
            localTrackingNumber = localTrackingNumber,
            onAddLocalTrackingNumber = onAddLocal,
            onRemoveLocalTrackingNumber = onRemoveLocal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private fun LazyListScope.smsItems(
    trackingNumber: String,
    messages: List<TrackingSms>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCopyMessage: () -> Unit
) {
    if (!hasPermission) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Allow SMS access",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Grant SMS read access so we can match incoming " +
                        "carrier notifications to this tracking number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRequestPermission) { Text("Allow access") }
            }
        }
        return
    }

    if (messages.isEmpty()) {
        item {
            Text(
                text = "No SMS found yet for $trackingNumber",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            )
        }
        return
    }

    items(messages, key = { it.id }) { sms ->
        SmsCard(sms = sms, onCopied = onCopyMessage)
    }
    item {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Long-press a message to copy it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )
    }
}

// Hebrew U+0590-U+05FF, Arabic U+0600-U+06FF — flip the layout for
// messages whose body has any RTL characters so sender / timestamp /
// body all align as a real-world SMS app would render them.
private fun isRtlText(text: String): Boolean =
    text.any { it.code in 0x0590..0x05FF || it.code in 0x0600..0x06FF }

private val URL_REGEX = Regex("""https?://\S+""")

// Matches the three link-like fragments couriers cram into the free-text
// `destCpInfo.phone` field: URLs, intl phone numbers, and emails. Order
// matters in the alternation so URLs (which can also contain `+`) win over
// raw phone numbers, and emails win before a stray `@user` look-alike.
private val COURIER_LINK_REGEX = Regex(
    """(https?://\S+)|([\w.+-]+@[\w.-]+\.[A-Za-z]{2,})|(\+\d[\d\s\-]{6,}\d)"""
)

// Browser-style link blue, readable on both light and dark surfaces.
private val LINK_COLOR = Color(0xFF1A73E8)

private fun annotatedSmsBody(body: String): AnnotatedString = buildAnnotatedString {
    val matches = URL_REGEX.findAll(body).toList()
    if (matches.isEmpty()) {
        append(body)
        return@buildAnnotatedString
    }
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = LINK_COLOR,
            textDecoration = TextDecoration.Underline
        )
    )
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) {
            append(body.substring(cursor, match.range.first))
        }
        // Strip trailing slashes from the displayed URL only — they're
        // bidi-weak and Unicode pushes them to the visual start of the
        // line when the surrounding text is RTL (Hebrew/Arabic), making
        // the link read like "/https://example.com" instead of
        // "https://example.com/". The click target keeps the full URL.
        val display = match.value.trimEnd('/')
        withLink(LinkAnnotation.Url(match.value, linkStyle)) {
            append(display)
        }
        cursor = match.range.last + 1
    }
    if (cursor < body.length) {
        append(body.substring(cursor))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmsCard(sms: TrackingSms, onCopied: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val isRtl = remember(sms.body) { isRtlText(sms.body) }
    val annotated = remember(sms.body) { annotatedSmsBody(sms.body) }

    val cardContent = @Composable {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(sms.body))
                        onCopied()
                    }
                ),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sms.sender.ifBlank { "(unknown)" },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = DateUtils.formatDateTime(sms.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.Content
                    )
                )
            }
        }
    }

    if (isRtl) {
        // Flip the whole card so the sender lands on the right and the
        // timestamp on the left, matching what the user expects from a
        // Hebrew-language SMS app.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            cardContent()
        }
    } else {
        cardContent()
    }
}

// Builds an AnnotatedString from a free-text blob with phone numbers,
// emails, and URLs auto-detected and wired to the appropriate `tel:` /
// `mailto:` / `https://` link annotations. Used by the destination
// courier card where Cainiao crams everything into a single text field.
private fun annotatedCourierBlob(body: String): AnnotatedString = buildAnnotatedString {
    val matches = COURIER_LINK_REGEX.findAll(body).toList()
    if (matches.isEmpty()) {
        append(body)
        return@buildAnnotatedString
    }
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = LINK_COLOR,
            textDecoration = TextDecoration.Underline
        )
    )
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) {
            append(body.substring(cursor, match.range.first))
        }
        val raw = match.value
        val href = when {
            raw.startsWith("http", ignoreCase = true) -> raw
            raw.contains('@') -> "mailto:$raw"
            else -> "tel:" + raw.filter { it == '+' || it.isDigit() }
        }
        withLink(LinkAnnotation.Url(href, linkStyle)) {
            append(raw)
        }
        cursor = match.range.last + 1
    }
    if (cursor < body.length) {
        append(body.substring(cursor))
    }
}

@Composable
private fun CourierCard(
    carrier: DestCarrierInfo?,
    localTrackingNumber: String?,
    onAddLocalTrackingNumber: () -> Unit,
    onRemoveLocalTrackingNumber: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRtl = remember(carrier?.phone) { carrier?.phone?.let(::isRtlText) ?: false }
    val phoneAnnotated = remember(carrier?.phone) {
        carrier?.phone?.let { annotatedCourierBlob(it) }
    }
    val urlAnnotated = remember(carrier?.url) {
        carrier?.url?.let { url ->
            val href = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
            val display = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Url(
                        href,
                        TextLinkStyles(
                            style = SpanStyle(
                                color = LINK_COLOR,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) { append(display) }
            }
        }
    }
    val emailAnnotated = remember(carrier?.email) {
        carrier?.email?.takeIf { it.isNotBlank() }?.let { email ->
            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Url(
                        "mailto:$email",
                        TextLinkStyles(
                            style = SpanStyle(
                                color = LINK_COLOR,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) { append(email) }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Local courier",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = carrier?.name ?: stringResource(R.string.courier_not_reported),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (carrier != null) Color.Unspecified
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Before Cainiao reports a destination carrier, explain why the
            // card is empty — but keep the local-TN entry below available.
            if (carrier == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.courier_not_reported_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // User-supplied local-courier TN. Sits directly under the carrier
            // emblem because that's the conceptual owner of this number, even
            // though storage lives on TrackedPackage. The TN is only used as
            // an extra needle for SMS scanning — nothing is queried from
            // Cainiao or any other API with it.
            Spacer(Modifier.height(10.dp))
            if (localTrackingNumber.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onAddLocalTrackingNumber,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_local_courier_tn))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.local_courier_tn_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = localTrackingNumber,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    TextButton(onClick = onRemoveLocalTrackingNumber) {
                        Text(
                            stringResource(R.string.remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            phoneAnnotated?.let { phone ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = phone,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = if (hasRtl) TextDirection.Content
                            else TextDirection.Ltr
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
            // Email isn't repeated if it already appears in the phone blob —
            // many couriers mash both into one field and surfacing it twice
            // is just noise.
            emailAnnotated?.let { email ->
                val emailRaw = carrier?.email
                val phoneText = carrier?.phone.orEmpty()
                if (emailRaw != null && !phoneText.contains(emailRaw, ignoreCase = true)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            urlAnnotated?.let { url ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LocalTrackingNumberDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_local_courier_tn_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_local_courier_tn_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.local_courier_tn_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.trim().isNotEmpty()
            ) { Text(stringResource(R.string.save_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
