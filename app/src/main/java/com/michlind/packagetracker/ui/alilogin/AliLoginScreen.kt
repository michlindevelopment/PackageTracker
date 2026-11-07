package com.michlind.packagetracker.ui.alilogin

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.michlind.packagetracker.util.AliSession
import kotlinx.coroutines.delay

// Land on the desktop orders page; AliExpress redirects unauthenticated
// visitors to login.aliexpress.com. We use the desktop layout (not the
// m.aliexpress.com mobile one) because the mobile surfaces are flaky
// inside a WebView — they push us into native-app deep links and mid-flow
// JS-redirect loops we couldn't reliably tame.
private const val LOGIN_URL = "https://www.aliexpress.com/p/order/index.html"
private const val TAG = "AliLogin"

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

// Hosts where a hardcoded desktop UA gets us 403'd ("This browser or app
// may not be secure"). On these we fall back to the device's native
// WebView UA with the `; wv` marker stripped — that's the canonical
// workaround for Google's `disallowed_useragent` block, and equivalent
// fingerprint problems on Apple/Facebook OAuth. AliExpress's own login
// host is included because the OAuth handshake for "Sign in with Google"
// originates there before bouncing to Google.
private val AUTH_HOST_HINTS = listOf(
    "accounts.google.com",
    "accounts.youtube.com",
    "appleid.apple.com",
    "facebook.com/dialog/oauth",
    "facebook.com/v",
    "login.aliexpress.com",
    "passport.aliexpress.com"
)

private fun isAuthFlowUrl(url: String): Boolean = AliSession.isAuthFlowUrl(url)

private fun userAgentFor(url: String, deviceUaNoWv: String): String =
    if (isAuthFlowUrl(url)) deviceUaNoWv else DESKTOP_UA

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AliLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit
) {
    val context = LocalContext.current
    val deviceUaNoWv = remember {
        WebSettings.getDefaultUserAgent(context).replace("; wv", "")
    }
    // Latch the success callback so a redirect chain that crosses
    // login.aliexpress a few times can't fire the listener twice.
    val signaled = remember { mutableStateOf(false) }

    // What the WebView is showing right now, and whether it has been on a
    // login page at any point. Together with the cookie snapshot taken on
    // entry they separate "the user just signed in" from "an expired session
    // left a `sign=y` cookie behind".
    val currentUrl = remember { mutableStateOf<String?>(null) }
    val sawLogin = remember { mutableStateOf(false) }
    val initialCookies = remember { AliSession.cookieHeader() }

    // Success detector, polled because AliExpress's login can complete via
    // AJAX with no navigation. A bare `sign=y` is NOT enough: that cookie can
    // outlive the server session, and treating it as success bounced the user
    // straight back to Home without ever showing the login page, with no way
    // to reconnect. Instead require the cookie PLUS proof the server accepted
    // it, held steady for ~2s:
    //   - the orders page is showing (AliExpress did not redirect us to
    //     login), or
    //   - we passed through a login page and the cookies have since changed.
    LaunchedEffect(Unit) {
        var steadyTicks = 0
        while (!signaled.value) {
            delay(500)
            val url = currentUrl.value
            val signed = AliSession.hasSessionCookie()
            val trusted = signed && url != null && !AliSession.isAuthFlowUrl(url) && (
                url.contains("/p/order/") ||
                    (sawLogin.value && AliSession.cookieHeader() != initialCookies)
                )
            steadyTicks = if (trusted) steadyTicks + 1 else 0
            if (steadyTicks >= 4) {
                Log.d(TAG, "poll: signed in (url=$url sawLogin=${sawLogin.value}) → firing onLoggedIn")
                signaled.value = true
                onLoggedIn()
                break
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to AliExpress") },
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
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode =
                                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = DESKTOP_UA
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                val scheme = request.url.scheme?.lowercase()

                                // AliExpress's mobile site tries to push the
                                // user into their native app via custom
                                // schemes (`aliexpress://...`). WebView would
                                // hit ERR_UNKNOWN_URL_SCHEME and surface an
                                // error page; we just drop these on the floor.
                                if (scheme != "http" && scheme != "https" &&
                                    scheme != "about" && scheme != "data"
                                ) {
                                    Log.d(TAG, "Swallowing non-http nav: $url")
                                    return true
                                }

                                // Swap the WebView's UA per destination so
                                // "Sign in with Google" doesn't get a 403
                                // ("disallowed_useragent") while AliExpress
                                // surfaces still see a mobile UA.
                                val targetUa = userAgentFor(url, deviceUaNoWv)
                                if (view.settings.userAgentString != targetUa) {
                                    view.settings.userAgentString = targetUa
                                    view.loadUrl(url)
                                    return true
                                }
                                return false
                            }

                            // Also fires for in-page (pushState) navigations,
                            // which onPageFinished does not.
                            override fun doUpdateVisitedHistory(
                                view: WebView,
                                url: String,
                                isReload: Boolean
                            ) {
                                currentUrl.value = url
                                if (isAuthFlowUrl(url)) sawLogin.value = true
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String
                            ) {
                                currentUrl.value = url
                                if (isAuthFlowUrl(url)) sawLogin.value = true
                                Log.d(
                                    TAG,
                                    "onPageFinished: $url (sign=y? ${AliSession.hasSessionCookie()})"
                                )
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) {
                                    Log.w(
                                        TAG,
                                        "load failed (${error.errorCode}): " +
                                            "${error.description} — ${request.url}"
                                    )
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(
                                cm: ConsoleMessage
                            ): Boolean {
                                Log.d(
                                    TAG,
                                    "[${cm.messageLevel()}] ${cm.message()} " +
                                        "@ ${cm.sourceId()}:${cm.lineNumber()}"
                                )
                                return true
                            }

                            // AliExpress's "Sign in with Google" opens the
                            // OAuth handshake in a new window. Funnel that
                            // back into the existing WebView so the cookie
                            // flow lands on the same instance.
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message
                            ): Boolean {
                                val transport =
                                    resultMsg.obj as? WebView.WebViewTransport
                                        ?: return false
                                transport.webView = view
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                }
            )
        }
    }
}
