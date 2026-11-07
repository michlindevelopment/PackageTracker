package com.michlind.packagetracker.util

import android.webkit.CookieManager

/**
 * The app's view of the AliExpress web session, in one place.
 *
 * "Signed in" is inferred from the `sign=y` cookie in the WebView cookie
 * store. That cookie can outlive the server-side session, so it is only a
 * hint: the authoritative signal is AliExpress redirecting the orders page to
 * its login page. When that happens call [clearSessionCookies] so the hint
 * stops lying to every screen that reads it.
 */
object AliSession {

    private const val SESSION_URL = "https://www.aliexpress.com"
    private const val EXPIRED = "Thu, 01 Jan 1970 00:00:00 GMT"

    // Hosts AliExpress sets session cookies for. getCookie(url) only returns
    // what a request to that host would send, so each one is swept.
    private val COOKIE_URLS = listOf(
        "https://www.aliexpress.com",
        "https://aliexpress.com",
        "https://login.aliexpress.com",
        "https://passport.aliexpress.com",
        "https://m.aliexpress.com"
    )

    // Hosts that are part of signing in (AliExpress itself plus the OAuth
    // providers it hands off to). Seeing one means the user is mid-login.
    private val AUTH_HOST_HINTS = listOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "appleid.apple.com",
        "facebook.com/dialog/oauth",
        "facebook.com/v",
        "login.aliexpress.com",
        "passport.aliexpress.com"
    )

    /** Raw cookie header the WebView would send to aliexpress.com. */
    fun cookieHeader(): String =
        runCatching { CookieManager.getInstance().getCookie(SESSION_URL).orEmpty() }
            .getOrDefault("")

    /** True if the login marker cookie is present. May be stale; see class doc. */
    fun hasSessionCookie(): Boolean = cookieHeader().contains("sign=y")

    /** True for AliExpress's own login page (the "session is gone" redirect). */
    fun isLoginUrl(url: String): Boolean =
        url.contains("login.aliexpress", ignoreCase = true) ||
            url.contains("passport.aliexpress", ignoreCase = true)

    /** True for any page that belongs to the sign-in flow, OAuth providers included. */
    fun isAuthFlowUrl(url: String): Boolean =
        AUTH_HOST_HINTS.any { url.contains(it, ignoreCase = true) }

    /**
     * Expires every AliExpress cookie (best effort: the cookie store can't be
     * enumerated by domain, so each name is expired for the host-only and the
     * `.aliexpress.com` variants at path `/`). Leaves other sites' cookies
     * alone, notably Cainiao's verification cookie and Google's login.
     */
    fun clearSessionCookies() {
        runCatching {
            val cm = CookieManager.getInstance()
            COOKIE_URLS.forEach { url ->
                cm.getCookie(url).orEmpty()
                    .split(';')
                    .map { it.substringBefore('=').trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { name ->
                        cm.setCookie(url, "$name=; Max-Age=0; Expires=$EXPIRED; Path=/")
                        cm.setCookie(
                            url,
                            "$name=; Max-Age=0; Expires=$EXPIRED; Path=/; Domain=.aliexpress.com"
                        )
                    }
            }
            cm.flush()
        }
    }
}
