package com.michlind.packagetracker.ui.aliimport

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private const val ORDERS_URL = "https://www.aliexpress.com/p/order/index.html"
private const val BRIDGE_NAME = "AliBridge"
private const val TAG = "BgAliImport"

// Real desktop Chrome UA so AliExpress serves the desktop orders page.
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

/**
 * 1×1 alpha-0 WebView that runs the AliExpress import script silently in
 * the background. Mount it on a host screen only while a bg import should be
 * in progress — the host removes it (triggering DisposableEffect) once the
 * outcome lands. The script's progress events flow through [bridge] directly
 * to whoever owns it (typically a ViewModel).
 *
 * Outcome callbacks are mutually exclusive — exactly one of them fires per
 * mount:
 *  - [onSkipped]: AliExpress redirected us to the login page, so we have no
 *    session and cannot import.
 *  - [onError]: main-frame load failure or JS-asset read failure.
 *  - [onAborted]: composable left composition before the script finished
 *    (e.g. user navigated away, or refreshAll() timed out and tore us down).
 *
 * Successful completion is signalled via the bridge's `onComplete()` event,
 * not via a composable callback — the host listens to bridge events.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BgAliImportWebView(
    bridge: AliImportBridge,
    onSkipped: () -> Unit,
    onError: () -> Unit,
    onAborted: () -> Unit,
    prepare: suspend () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // Composable-side terminal state. Bridge-driven completion (the JS firing
    // onComplete) doesn't flip this — only the composable's own short-circuits
    // do — so DisposableEffect's onAborted still fires even after a clean
    // bridge completion. That's harmless: the host's outcome deferred is
    // already settled and `complete()` is idempotent.
    val settled = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (!settled.value) {
                settled.value = true
                onAborted()
            }
            webViewRef.value?.destroy()
        }
    }

    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
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
                addJavascriptInterface(bridge, BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = false

                    override fun onPageFinished(view: WebView, url: String) {
                        if (settled.value) return
                        Log.d(TAG, "onPageFinished: $url")
                        val isLoginPage = url.contains("login.aliexpress")
                        val isOrdersPage = url.contains("/p/order/")

                        if (isLoginPage) {
                            // No active session — bg import is opt-in by
                            // virtue of the user already having logged in via
                            // the manual import screen at some point. Skip
                            // silently; the user can sign in there if they
                            // want to opt in again.
                            settled.value = true
                            onSkipped()
                            return
                        }

                        if (!isOrdersPage) {
                            // Interstitial / redirect. If we still have a
                            // session, bounce to orders; otherwise skip.
                            val cookies = CookieManager.getInstance()
                                .getCookie("https://www.aliexpress.com").orEmpty()
                            if (cookies.contains("sign=y")) {
                                view.loadUrl(ORDERS_URL)
                            } else {
                                settled.value = true
                                onSkipped()
                            }
                            return
                        }

                        // Orders page rendered — prep the bridge with seed
                        // ids + config overrides, then inject the import
                        // script. The script's progress + completion events
                        // flow through the bridge from here on.
                        scope.launch {
                            prepare()
                            val js = runCatching {
                                val cfg = context.assets.open("ali_import_config.js")
                                    .bufferedReader().use { it.readText() }
                                val main = context.assets.open("ali_import.js")
                                    .bufferedReader().use { it.readText() }
                                cfg + "\n" + main
                            }.getOrNull()
                            if (js != null) {
                                view.evaluateJavascript(js, null)
                            } else if (!settled.value) {
                                settled.value = true
                                onError()
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame && !settled.value) {
                            Log.w(TAG, "load failed (${error.errorCode}): ${error.description}")
                            settled.value = true
                            onError()
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        Log.d(TAG, "[${cm.messageLevel()}] ${cm.message()} @ ${cm.sourceId()}:${cm.lineNumber()}")
                        return true
                    }
                }
                loadUrl(ORDERS_URL)
                webViewRef.value = this
            }
        }
    )
}
