package com.michlind.packagetracker.ui.aliimport

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
// All import logging — page loads, load errors, the `[Ali]` JS console stream,
// the bridge's dlog channel, and the wall-clock summaries — shares this one tag
// so `adb logcat -s DTAG` captures the entire import in a single stream.
private const val TAG = "DTAG"

private fun fmtElapsed(ms: Long): String =
    if (ms < 1000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)

// URLs worth logging in shouldInterceptRequest while we hunt the tracking-data
// endpoint. `mtop` catches AliExpress's API gateway; the rest are logistics
// hints in case the data comes from a non-mtop host.
private val TRACKING_REQ_HINT =
    Regex("mtop|track|logistic|mailno|waybill|delivery|shipment", RegexOption.IGNORE_CASE)

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
    // Wall-clock t0 for this mount — captured before the orders page loads so
    // the elapsed logged on every terminal outcome covers page load + JS.
    val mountedAt = remember { SystemClock.elapsedRealtime() }
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
                Log.d(TAG, "IMPORT WALL-CLOCK ABORTED elapsed=" +
                    fmtElapsed(SystemClock.elapsedRealtime() - mountedAt))
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

                    // Fires for EVERY request the WebView makes — including ones
                    // inside the hidden tracking iframe, which the page-side JS
                    // network spy can't observe. Logging-only for now: we're
                    // hunting the backend call the tracking page uses to fetch
                    // the mail-no, so we can call it directly and skip the whole
                    // SPA load. Returning null lets the request proceed normally.
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val url = request.url?.toString().orEmpty()
                        // Only log likely data endpoints — logging every image,
                        // font and analytics ping would drown the signal.
                        if (TRACKING_REQ_HINT.containsMatchIn(url)) {
                            Log.d(TAG, "REQ ${request.method} ${url.take(300)}")
                        }
                        return null
                    }

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
                            Log.d(TAG, "IMPORT WALL-CLOCK SKIPPED(login) elapsed=" +
                                fmtElapsed(SystemClock.elapsedRealtime() - mountedAt))
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
                                Log.d(TAG, "IMPORT WALL-CLOCK SKIPPED(no-session) elapsed=" +
                                    fmtElapsed(SystemClock.elapsedRealtime() - mountedAt))
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
                                Log.d(TAG, "injecting import script (" + js.length +
                                    " chars) elapsed=" +
                                    fmtElapsed(SystemClock.elapsedRealtime() - mountedAt))
                                view.evaluateJavascript(js, null)
                            } else if (!settled.value) {
                                settled.value = true
                                Log.d(TAG, "IMPORT WALL-CLOCK ERROR(asset-read) elapsed=" +
                                    fmtElapsed(SystemClock.elapsedRealtime() - mountedAt))
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
                            Log.d(TAG, "IMPORT WALL-CLOCK ERROR(load ${error.errorCode}) elapsed=" +
                                fmtElapsed(SystemClock.elapsedRealtime() - mountedAt))
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
                // Hand the bridge the same t0 so its onComplete/onError logs
                // measure from mount, matching the composable-side outcomes.
                bridge.importStartRealtimeMs = mountedAt
                Log.d(TAG, "IMPORT WALL-CLOCK START — loading orders page")
                loadUrl(ORDERS_URL)
                webViewRef.value = this
            }
        }
    )
}
