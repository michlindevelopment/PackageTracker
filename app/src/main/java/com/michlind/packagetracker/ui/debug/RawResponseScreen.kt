package com.michlind.packagetracker.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.StatusMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cainiao action codes the repo treats as advisories (forecasts/ASNs) when
// picking the latest meaningful event. Mirrors ADVISORY_ACTION_CODES in
// PackageRepositoryImpl — kept in sync by hand because that one is private.
private val ADVISORY_ACTION_CODES_FOR_DEBUG = setOf(
    "LAST_MILE_ASN_NOTIFY",
    "COMMON_INTRANSIT"
)

data class RawResponseState(
    val pkg: TrackedPackage? = null,
    val prettyJson: String? = null,
    // Parsed form of [prettyJson] used to render the collapsible tree view.
    // Kept alongside the pretty string so the toolbar's copy button stays
    // simple (no need to re-stringify on click).
    val parsedJson: JsonElement? = null,
    val latestActionCode: String? = null,
    // True iff the latest non-advisory action code has an explicit mapping
    // in StatusMapper.mapActionCode (i.e. status came from the action-code
    // table, not from the progressRate fallback bucket).
    val statusFromMapping: Boolean = false
)

@HiltViewModel
class RawResponseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PackageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RawResponseState())
    val state: StateFlow<RawResponseState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<Long>("packageId")
        if (id != null) load(id)
    }

    fun load(packageId: Long) {
        viewModelScope.launch {
            val pkg = repository.getPackageById(packageId) ?: return@launch
            val parsed = parseJson(pkg.rawApiJson)
            val pretty = parsed?.let { PRETTY_GSON.toJson(it) } ?: pkg.rawApiJson
            val latestActionCode = pkg.events
                .firstOrNull {
                    it.actionCode.isNotBlank() &&
                        it.actionCode !in ADVISORY_ACTION_CODES_FOR_DEBUG
                }
                ?.actionCode
            val fromMapping = latestActionCode != null &&
                StatusMapper.mapActionCode(latestActionCode) != PackageStatus.UNKNOWN
            _state.value = RawResponseState(
                pkg = pkg,
                prettyJson = pretty,
                parsedJson = parsed,
                latestActionCode = latestActionCode,
                statusFromMapping = fromMapping
            )
        }
    }

    private fun parseJson(json: String?): JsonElement? {
        if (json.isNullOrBlank()) return null
        return runCatching { JsonParser.parseString(json) }.getOrNull()
    }

    private companion object {
        val PRETTY_GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawResponseScreen(
    onBack: () -> Unit,
    viewModel: RawResponseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug · raw response") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val json = state.prettyJson
                    if (!json.isNullOrBlank()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(json))
                            snackbarScope.launch {
                                snackbarHostState.showSnackbar("Copied to clipboard")
                            }
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy JSON"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pkg = state.pkg
        if (pkg == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                status = pkg.status,
                latestActionCode = state.latestActionCode,
                statusFromMapping = state.statusFromMapping,
                progressRate = pkg.progressRate
            )

            JsonCard(parsedJson = state.parsedJson)
        }
    }
}

@Composable
private fun StatusCard(
    status: PackageStatus,
    latestActionCode: String?,
    statusFromMapping: Boolean,
    progressRate: Float?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.width(8.dp))
                // Tag the badge: green check if the status came directly
                // from an action-code mapping, dimmed question mark if we
                // had to fall back to the progressRate bucket.
                if (statusFromMapping) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "In mapping",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Not in mapping",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyValueRow(
                "Latest action code",
                latestActionCode ?: "(none after filtering advisories)"
            )
            KeyValueRow(
                "In action-code table",
                if (statusFromMapping) "yes" else "no — using progressRate fallback"
            )
            KeyValueRow(
                "progressRate",
                progressRate?.let { "${"%.0f".format(it * 100)}% (raw $it)" } ?: "(null)"
            )
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun JsonCard(parsedJson: JsonElement?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Full Cainiao response",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            if (parsedJson == null) {
                Text(
                    text = "No raw response cached yet. Pull-to-refresh the package " +
                        "(or hit the refresh icon in the detail toolbar) and come back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                JsonNode(element = parsedJson, name = null, depth = 0)
            }
        }
    }
}

// ── JSON tree viewer ────────────────────────────────────────────────────────
// Recursive renderer with collapsible {…} and […] containers. Top two levels
// default-expanded; deeper objects/arrays start collapsed so a long trace
// list doesn't bury the rest of the response on first open.

private val JSON_INDENT_DP = 12

private val JSON_KEY_COLOR        = Color(0xFFAB47BC) // purple
private val JSON_STRING_COLOR     = Color(0xFF2E7D32) // green
private val JSON_NUMBER_COLOR     = Color(0xFF1565C0) // blue
private val JSON_BOOL_COLOR       = Color(0xFFEF6C00) // orange
private val JSON_NULL_COLOR       = Color(0xFF9E9E9E) // grey

@Composable
private fun JsonNode(element: JsonElement, name: String?, depth: Int) {
    when {
        element.isJsonObject -> JsonObjectNode(element.asJsonObject, name, depth)
        element.isJsonArray -> JsonArrayNode(element.asJsonArray, name, depth)
        else -> JsonLeafNode(element, name, depth)
    }
}

@Composable
private fun JsonObjectNode(obj: JsonObject, name: String?, depth: Int) {
    // Auto-expand top three levels (root → "module" → "module[0]") so the
    // interesting data is visible on first open. Anything deeper starts
    // collapsed — keeps long `detailList` arrays out of the way.
    var expanded by remember { mutableStateOf(depth < 3) }
    val keys = remember(obj) { obj.keySet().toList() }
    JsonContainerHeader(
        name = name,
        depth = depth,
        expanded = expanded,
        openBrace = "{",
        closeBrace = "}",
        size = keys.size,
        unit = "key",
        onToggle = { expanded = !expanded }
    )
    if (expanded) {
        keys.forEach { k ->
            JsonNode(obj[k], k, depth + 1)
        }
        JsonClosingBrace("}", depth)
    }
}

@Composable
private fun JsonArrayNode(arr: JsonArray, name: String?, depth: Int) {
    var expanded by remember { mutableStateOf(depth < 3) }
    JsonContainerHeader(
        name = name,
        depth = depth,
        expanded = expanded,
        openBrace = "[",
        closeBrace = "]",
        size = arr.size(),
        unit = "item",
        onToggle = { expanded = !expanded }
    )
    if (expanded) {
        arr.forEachIndexed { i, child ->
            JsonNode(child, "[$i]", depth + 1)
        }
        JsonClosingBrace("]", depth)
    }
}

@Composable
private fun JsonContainerHeader(
    name: String?,
    depth: Int,
    expanded: Boolean,
    openBrace: String,
    closeBrace: String,
    size: Int,
    unit: String,
    onToggle: () -> Unit
) {
    val label = buildAnnotatedString {
        if (name != null) {
            withStyle(SpanStyle(color = JSON_KEY_COLOR)) {
                append(if (name.startsWith("[")) name else "\"$name\"")
            }
            append(": ")
        }
        if (expanded) {
            append(openBrace)
        } else {
            append("$openBrace … $closeBrace  ")
            withStyle(SpanStyle(color = JSON_NULL_COLOR)) {
                append("$size $unit${if (size == 1) "" else "s"}")
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(start = (depth * JSON_INDENT_DP).dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(text = label, style = JSON_TEXT_STYLE)
    }
}

@Composable
private fun JsonClosingBrace(brace: String, depth: Int) {
    Text(
        text = brace,
        style = JSON_TEXT_STYLE,
        modifier = Modifier.padding(
            start = (depth * JSON_INDENT_DP + 18).dp,
            top = 1.dp,
            bottom = 1.dp
        )
    )
}

@Composable
private fun JsonLeafNode(element: JsonElement, name: String?, depth: Int) {
    val annotated = buildAnnotatedString {
        if (name != null) {
            withStyle(SpanStyle(color = JSON_KEY_COLOR)) {
                append(if (name.startsWith("[")) name else "\"$name\"")
            }
            append(": ")
        }
        if (element.isJsonNull) {
            withStyle(SpanStyle(color = JSON_NULL_COLOR)) { append("null") }
        } else {
            val p = element.asJsonPrimitive
            when {
                p.isBoolean -> withStyle(SpanStyle(color = JSON_BOOL_COLOR)) {
                    append(p.asBoolean.toString())
                }
                p.isNumber -> withStyle(SpanStyle(color = JSON_NUMBER_COLOR)) {
                    append(p.asNumber.toString())
                }
                else -> withStyle(SpanStyle(color = JSON_STRING_COLOR)) {
                    append("\"${p.asString}\"")
                }
            }
        }
    }
    Text(
        text = annotated,
        style = JSON_TEXT_STYLE,
        modifier = Modifier.padding(
            start = (depth * JSON_INDENT_DP + 18).dp,
            top = 1.dp,
            bottom = 1.dp
        )
    )
}

private val JSON_TEXT_STYLE = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = androidx.compose.ui.unit.TextUnit.Unspecified
)
