package com.michlind.packagetracker.ui.captcha

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "Captcha"

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

/**
 * Loads Cainiao's tracking page in a WebView so the user can solve the
 * "baxia" slide-puzzle CAPTCHA. The cookie set on completion is shared with
 * OkHttp via [WebViewCookieJar], so the next API call goes through.
 *
 * No completion detection — when the user is satisfied they've solved it,
 * they tap back. Auto-detecting "is the puzzle solved" is fragile; trusting
 * the user works fine and keeps this screen tiny.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaScreen(
    trackingNumber: String,
    onBack: () -> Unit
) {
    val url = "https://global.cainiao.com/detail.htm?mailNoCode=$trackingNumber"

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
                                Log.d(TAG, "onPageFinished: $url")
                                view.evaluateJavascript(HIDE_QR_OVERLAY_JS, null)
                            }
                        }
                        loadUrl(url)
                    }
                }
            )
        }
    }
}
