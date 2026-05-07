package com.michlind.packagetracker.data.api

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Bridges OkHttp's cookie jar to Android's WebView CookieManager so the two
 * read and write the same cookie store. Needed because Cainiao's anti-bot
 * ("baxia") sets a "human verified" cookie when the user solves the slide
 * puzzle — that solve happens in a WebView (CaptchaScreen), but the
 * subsequent tracking calls go through OkHttp. Without this jar, OkHttp
 * never sees the verification cookie and stays rate-limited forever.
 *
 * CookieManager is a process-wide singleton, so any WebView in the app
 * automatically populates and reads from the same store.
 */
class WebViewCookieJar : CookieJar {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { cookieManager.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieHeader = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return cookieHeader
            .split(";")
            .mapNotNull { Cookie.parse(url, it.trim()) }
    }
}
