package com.michlind.packagetracker.ui.captcha

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val TAG = "Captcha"

private const val CAINIAO_URL = "https://global.cainiao.com"

// Cainiao's baxia verification cookie. It is set only when the slider has
// been solved, so a change in its value is the "you're verified" signal.
private const val VERIFY_COOKIE = "x5sec"

// First visit to the challenge page in a fresh WebView often cannot be
// verified: the slider widget initialises before the device-id cookies the
// page sets on that very first load exist, so the first slide is rejected
// while the second open of the page works. We load the page once, let it set
// its cookies, then reload it behind a spinner so the user only ever sees the
// second, working load.
private const val WARMUP_RELOAD_DELAY_MS = 1_200L

// Never keep the spinner up longer than this if a load hangs.
private const val WARMUP_TIMEOUT_MS = 12_000L

// After the verification cookie changes, give the page a moment to finish its
// own post-verification reload before closing.
private const val SOLVED_CLOSE_DELAY_MS = 1_500L

// Cainiao's "baxia" challenge page renders a QR-code overlay on top of
// the slide-to-verify widget when the viewport is narrow (their desktop
// layout positions the two side-by-side; in a phone-width WebView they
// collide). Hide every element that looks like a QR code so only the
// slider remains. Re-runs a few times because the captcha widget is
// lazy-loaded — first paint may not have it yet.
private const val HIDE_QR_OVERLAY_JS = """
(function () {
  function hideQr() {
    var selectors = [
      '[class*="qrcode" i]',
      '[class*="qr-code" i]',
      '[class*="qr_code" i]',
      '[id*="qrcode" i]',
      'img[src*="qrcode" i]',
      'img[src*="qr_code" i]'
    ];
    selectors.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (el) {
        el.style.setProperty('display', 'none', 'important');
        el.style.setProperty('visibility', 'hidden', 'important');
      });
    });
  }
  hideQr();
  setTimeout(hideQr, 500);
  setTimeout(hideQr, 2000);
  setTimeout(hideQr, 5000);
})();
"""

private fun cookieValue(name: String): String? =
    runCatching {
        CookieManager.getInstance().getCookie(CAINIAO_URL).orEmpty()
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
    }.getOrNull()

/** Tracks the warm-up reload so duplicate onPageFinished callbacks can't skip it. */
private class WarmupState {
    var reloadScheduled = false
    var reloaded = false
}

/**
 * Loads Cainiao's tracking page in a WebView so the user can solve the
 * "baxia" slide-puzzle CAPTCHA. The cookie set on completion is shared with
 * OkHttp via [WebViewCookieJar], so the next API call goes through.
 *
 * The page is loaded twice (see [WARMUP_RELOAD_DELAY_MS]) before the user can
 * touch it. The screen closes itself when the verification cookie changes;
 * the user can also leave with back at any time. Either way the home screen
 * retries the blocked sync when it resumes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaScreen(
    trackingNumber: String,
    onBack: () -> Unit
) {
    val url = "https://global.cainiao.com/detail.htm?mailNoCode=$trackingNumber"
    var ready by remember { mutableStateOf(false) }
    val warmup = remember { WarmupState() }

    // Safety valve: a load that never finishes must not trap the user behind
    // the spinner.
    LaunchedEffect(Unit) {
        delay(WARMUP_TIMEOUT_MS)
        ready = true
    }

    // Auto-close once the verification cookie changes after the warm-up. The
    // baseline is taken when the page becomes usable so cookies written during
    // the warm-up loads don't count as a solve.
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val baseline = cookieValue(VERIFY_COOKIE)
        while (true) {
            delay(500)
            val now = cookieValue(VERIFY_COOKIE)
            if (!now.isNullOrEmpty() && now != baseline) {
                Log.d(TAG, "$VERIFY_COOKIE changed → verified, closing")
                delay(SOLVED_CLOSE_DELAY_MS)
                onBack()
                return@LaunchedEffect
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify you're not a bot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                onRelease = { it.stopLoading(); it.destroy() },
                factory = { ctx ->
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance()
                            .setAcceptThirdPartyCookies(this, true)
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode =
                                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // Pinch-zoom fallback in case the CSS hide
                            // misses an overlay element.
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String
                            ) {
                                Log.d(TAG, "onPageFinished: $url (reloaded=${warmup.reloaded})")
                                view.evaluateJavascript(HIDE_QR_OVERLAY_JS, null)
                                when {
                                    warmup.reloaded -> ready = true
                                    !warmup.reloadScheduled -> {
                                        warmup.reloadScheduled = true
                                        view.postDelayed({
                                            warmup.reloaded = true
                                            view.reload()
                                        }, WARMUP_RELOAD_DELAY_MS)
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                // Show whatever the WebView has (its error
                                // page) rather than a spinner forever.
                                if (request.isForMainFrame) {
                                    Log.w(TAG, "load failed (${error.errorCode}): ${error.description}")
                                    ready = true
                                }
                            }
                        }
                        loadUrl(url)
                    }
                }
            )

            if (!ready) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(
                        text = "Preparing verification…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
