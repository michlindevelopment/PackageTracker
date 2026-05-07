package com.michlind.packagetracker.ui.detail

import android.Manifest
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
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch
import com.michlind.packagetracker.R
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingSms
import com.michlind.packagetracker.ui.components.SkeletonDetailHeader
import com.michlind.packagetracker.ui.components.TimelineItem
import com.michlind.packagetracker.ui.components.colorAndIcon
import com.michlind.packagetracker.util.DateUtils

private val STAGE_LABELS = listOf(
    "Order\nPlaced", "Shipped", "Export\nCustoms", "In Transit", "Import\nCustoms", "Delivery", "Delivered"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    packageId: Long,
    onEditClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val smsList by viewModel.smsList.collectAsStateWithLifecycle()
    val hasSmsPermission by viewModel.hasSmsPermission.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSmsPermission()
        if (granted) {
            // Don't make the user wait for the next syncStatus() to see
            // anything — kick off a one-shot scan for just this TN.
            viewModel.scanSmsForCurrent()
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
                Column(modifier = Modifier.padding(paddingValues)) {
                    repeat(3) { SkeletonDetailHeader() }
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
                    }
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
    onCopyMessage: () -> Unit
) {
    val nameTooltipState = rememberTooltipState(isPersistent = true)
    val tooltipScope = rememberCoroutineScope()
    // Pager owns the selected tab — clicking a tab animates the pager,
    // and a horizontal swipe slides between Tracking and SMS pages.
    val pagerState = rememberPagerState(pageCount = { 2 })
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .nestedScroll(collapseScrollConnection)
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
        }

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
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pkg.originCountry?.let { InfoChip(label = "From", value = it) }
                    pkg.destCountry?.let { InfoChip(label = "To", value = it) }
                    pkg.daysInTransit?.let { InfoChip(label = "Transit", value = it) }
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // Tabs — pinned right under Shipping Progress. Tap or swipe to
        // switch between Tracking history and SMS.
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(0) } },
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
                selected = pagerState.currentPage == 1,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(1) } },
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
        }

        // Swipeable pages — each owns its own LazyColumn so vertical
        // scroll inside Tracking doesn't affect SMS scroll position and
        // vice versa.
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
                if (page == 0) {
                    trackingHistoryItems(events = pkg.events)
                } else {
                    smsItems(
                        trackingNumber = pkg.trackingNumber,
                        messages = smsMessages,
                        hasPermission = hasSmsPermission,
                        onRequestPermission = onRequestSmsPermission,
                        onCopyMessage = onCopyMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressStepper(currentStatus: PackageStatus, modifier: Modifier = Modifier) {
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
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            fontSize = 12.sp
        )
    }
}
