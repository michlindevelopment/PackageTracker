package com.michlind.packagetracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.michlind.packagetracker.domain.model.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onContributorsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val isAliConnected by viewModel.isAliConnected.collectAsStateWithLifecycle()
    val toShipPages by viewModel.toShipPages.collectAsStateWithLifecycle()
    val shippedPages by viewModel.shippedPages.collectAsStateWithLifecycle()
    val processedPages by viewModel.processedPages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Re-check connection state every time the screen is shown — the user may
    // have logged in via the import flow since they last visited Settings.
    LaunchedEffect(Unit) {
        viewModel.refreshAliConnection()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect from AliExpress?") },
            text = {
                Text(
                    "This signs you out of AliExpress in the import browser. " +
                        "Already imported packages stay in the app; the next " +
                        "import will ask you to log in again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    viewModel.disconnectFromAliExpress()
                }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (updateState is UpdateUiState.NeedsInstallPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateState() },
            title = { Text("Permission needed") },
            text = {
                Text(
                    "Android needs your OK before AliTrack can install updates. " +
                        "Tap Open Settings, enable \"Allow from this source\", " +
                        "then come back and tap Update again."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.openInstallPermissionSettings() }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateState() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            SectionTitle("Theme")
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption(
                    label = "System default",
                    icon = Icons.Default.PhoneAndroid,
                    selected = theme == ThemePreference.SYSTEM,
                    onSelect = { viewModel.setTheme(ThemePreference.SYSTEM) }
                )
                ThemeOption(
                    label = "Light",
                    icon = Icons.Default.LightMode,
                    selected = theme == ThemePreference.LIGHT,
                    onSelect = { viewModel.setTheme(ThemePreference.LIGHT) }
                )
                ThemeOption(
                    label = "Dark",
                    icon = Icons.Default.DarkMode,
                    selected = theme == ThemePreference.DARK,
                    onSelect = { viewModel.setTheme(ThemePreference.DARK) }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("Notifications")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Send a sample notification using a random package " +
                    "from your list — useful to confirm Android allows them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { viewModel.sendTestNotification() }) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Send test notification")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("AliExpress")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = if (isAliConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                Icon(
                    imageVector = if (isAliConnected) Icons.Default.CheckCircle
                    else Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (isAliConnected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isAliConnected)
                    "Sign out of AliExpress in the import browser. Already " +
                        "imported packages stay; the next import will ask for login."
                else
                    "You're not signed in to AliExpress. Use \"Import from " +
                        "AliExpress\" on the home screen to log in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDisconnectDialog = true },
                enabled = isAliConnected
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Disconnect from AliExpress")
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Import page budgets")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "How many \"View more\" clicks per tab during an import. " +
                    "Set to 0 to skip a tab entirely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(12.dp))
            PageBudgetRow(
                label = "To ship",
                value = toShipPages,
                onChange = viewModel::setToShipPages
            )
            PageBudgetRow(
                label = "Shipped",
                value = shippedPages,
                onChange = viewModel::setShippedPages
            )
            PageBudgetRow(
                label = "Processed",
                value = processedPages,
                onChange = viewModel::setProcessedPages
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("About")
            Spacer(Modifier.height(8.dp))
            UpdateSection(
                currentVersion = viewModel.currentVersion,
                state = updateState,
                onCheck = { viewModel.checkForUpdates() },
                onUpdate = { viewModel.startUpdate() }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = false,
                        onClick = onContributorsClick,
                        role = Role.Button
                    )
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Contributors",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun UpdateSection(
    currentVersion: String,
    state: UpdateUiState,
    onCheck: () -> Unit,
    onUpdate: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text("Installed version", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            text = "v$currentVersion",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text("Latest on GitHub", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        when (state) {
            is UpdateUiState.Checking -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            is UpdateUiState.UpToDate -> Text(
                text = "v$currentVersion",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            is UpdateUiState.Available -> Text(
                text = "v${state.latestVersion}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            is UpdateUiState.Downloading,
            is UpdateUiState.ReadyToInstall -> { /* covered below */ }
            else -> Text(
                text = "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    when (state) {
        is UpdateUiState.Idle, is UpdateUiState.Error,
        is UpdateUiState.UpToDate, is UpdateUiState.NeedsInstallPermission -> {
            OutlinedButton(
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Check for updates")
            }
        }
        is UpdateUiState.Checking -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(8.dp))
                Text("Checking…")
            }
        }
        is UpdateUiState.Available -> {
            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Update to v${state.latestVersion} (${formatSize(state.sizeBytes)})")
            }
        }
        is UpdateUiState.Downloading -> {
            Column {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Downloading… ${state.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
        is UpdateUiState.ReadyToInstall -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Download complete — finish in the system installer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (state is UpdateUiState.Error) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "?"
    val mb = bytes / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

// Compact stepper: − / count / +. Clamps to 0..100 in the repository.
@Composable
private fun PageBudgetRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onChange(value - 1) },
            enabled = value > 0
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        IconButton(
            onClick = { onChange(value + 1) },
            enabled = value < 100
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
