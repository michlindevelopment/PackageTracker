package com.michlind.packagetracker.ui.aliimport

import android.annotation.SuppressLint
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val ORDERS_URL = "https://www.aliexpress.com/p/order/index.html"
private const val BRIDGE_NAME = "AliBridge"
private const val TAG = "AliImport"

// Real desktop Chrome UA so AliExpress serves the desktop orders page (not mobile).
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AliImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit = onBack,
    viewModel: AliImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from AliExpress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(context).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = DESKTOP_UA
                        }
                        addJavascriptInterface(viewModel.bridge, BRIDGE_NAME)
                        // If the session cookie is already set, the orders page
                        // will load directly with no login interaction needed —
                        // flip to Authenticating so the overlay covers the
                        // WebView during that load (otherwise the user briefly
                        // sees the half-rendered page).
                        val initialCookies = CookieManager.getInstance()
                            .getCookie("https://www.aliexpress.com").orEmpty()
                        if (initialCookies.contains("sign=y")) {
                            viewModel.onAuthenticated()
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = false

                            override fun onPageFinished(view: WebView, url: String) {
                                Log.d(TAG, "onPageFinished: $url")
                                val isOrdersPage = url.contains("/p/order/")
                                val isLoginPage = url.contains("login.aliexpress")
                                if (isLoginPage) {
                                    // Cookie expired or never logged in — drop back
                                    // to Idle so the user can interact with the form.
                                    viewModel.onLoginPageShown()
                                    return
                                }
                                if (!isOrdersPage) {
                                    val cookies = CookieManager.getInstance()
                                        .getCookie("https://www.aliexpress.com").orEmpty()
                                    if (cookies.contains("sign=y")) {
                                        Log.d(TAG, "Logged in, not on orders → bouncing to orders")
                                        viewModel.onAuthenticated()
                                        view.loadUrl(ORDERS_URL)
                                        return
                                    }
                                }
                                if (isOrdersPage) {
                                    viewModel.onOrdersPageLoaded()
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) {
                                    val msg = "Load failed (${error.errorCode}): ${error.description}"
                                    Log.w(TAG, "$msg — ${request.url}")
                                    viewModel.onLoadError(msg)
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                                Log.d(TAG, "[${cm.messageLevel()}] ${cm.message()} @ ${cm.sourceId()}:${cm.lineNumber()}")
                                return true
                            }

                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message
                            ): Boolean {
                                val transport = resultMsg.obj as? WebView.WebViewTransport
                                    ?: return false
                                transport.webView = view
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        loadUrl(ORDERS_URL)
                        webViewRef.value = this
                    }
                }
            )

            // Half-transparent overlay covering the WebView once we know the
            // user is past the login page. Idle = login page is showing → no
            // overlay so the user can interact with AliExpress's form.
            if (state !is AliImportState.Idle) {
                ImportOverlay(
                    state = state,
                    onStart = {
                        viewModel.beginImport()
                        val js = runCatching {
                            val cfg = context.assets.open("ali_import_config.js")
                                .bufferedReader().use { it.readText() }
                            val main = context.assets.open("ali_import.js")
                                .bufferedReader().use { it.readText() }
                            cfg + "\n" + main
                        }.getOrNull()
                        if (js != null) {
                            webViewRef.value?.evaluateJavascript(js, null)
                        }
                    },
                    onDone = onDone,
                    onRetry = { viewModel.reset() }
                )
            }
        }
    }
}

@Composable
private fun ImportOverlay(
    state: AliImportState,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Swallow all touches so the WebView underneath can't be poked
            // while we're loading orders / importing.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = headingFor(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (state is AliImportState.Error) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                ProgressIndicator(state)

                if (state is AliImportState.Importing) {
                    CountersRow(state.added, state.upgraded, state.skipped, state.failed)
                } else if (state is AliImportState.Completed) {
                    CountersRow(state.added, state.upgraded, state.skipped, state.failed)
                }

                ActionButton(
                    state = state,
                    onStart = onStart,
                    onDone = onDone,
                    onRetry = onRetry
                )
            }
        }
    }
}

private fun headingFor(state: AliImportState): String = when (state) {
    AliImportState.Authenticating -> "Loading your orders…"
    AliImportState.ReadyToImport -> "Ready to import"
    is AliImportState.Importing -> state.statusText
    is AliImportState.Completed -> "Import complete"
    is AliImportState.Error -> "Error"
    AliImportState.Idle -> ""
}

@Composable
private fun ProgressIndicator(state: AliImportState) {
    when (state) {
        AliImportState.Authenticating -> CircularProgressIndicator()
        is AliImportState.Importing -> {
            val total = state.total
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { state.current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CircularProgressIndicator()
            }
        }
        else -> {}
    }
}

@Composable
private fun ActionButton(
    state: AliImportState,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        AliImportState.Idle, AliImportState.Authenticating -> Button(
            onClick = {},
            enabled = false
        ) { Text("Start import") }

        AliImportState.ReadyToImport -> Button(onClick = onStart) {
            Text("Start import")
        }

        is AliImportState.Importing -> Button(onClick = {}, enabled = false) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text("Importing…")
        }

        is AliImportState.Completed -> Button(onClick = onDone) { Text("Done") }
        is AliImportState.Error -> Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun CountersRow(added: Int, upgraded: Int, skipped: Int, failed: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Counter("Added", added, MaterialTheme.colorScheme.primary)
        Counter("Upgraded", upgraded, MaterialTheme.colorScheme.tertiary)
        Counter("Skipped", skipped, MaterialTheme.colorScheme.onSurfaceVariant)
        Counter("Failed", failed, MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = color)
    }
}
