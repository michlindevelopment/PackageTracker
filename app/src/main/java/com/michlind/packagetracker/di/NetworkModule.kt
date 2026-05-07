package com.michlind.packagetracker.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.michlind.packagetracker.data.api.CainiaoApiService
import com.michlind.packagetracker.data.api.GitHubReleaseService
import com.michlind.packagetracker.data.api.WebViewCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Thrown when Cainiao's anti-bot wall serves us a CAPTCHA page instead of
 * tracking JSON (header `bxpunish: 1`). Catching it explicitly lets the UI
 * tell the user "you've been rate-limited, try again later" instead of
 * showing the generic "failed to refresh" message.
 */
class CainiaoRateLimitException(message: String) : IOException(message)

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://global.cainiao.com/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            // Share the cookie jar with the WebView so the bot-detection
            // verification cookie set by CaptchaScreen lands here too.
            .cookieJar(WebViewCookieJar())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "https://global.cainiao.com/")
                    .build()
                val response = chain.proceed(request)
                // Cainiao's anti-bot ("baxia") replies with `bxpunish: 1`
                // and a CAPTCHA HTML body when it decides we're hammering
                // the endpoint. Detect it here and turn it into a typed
                // exception so the UI can show a sensible message instead
                // of letting Gson explode on the HTML body.
                if (response.header("bxpunish") == "1") {
                    response.close()
                    throw CainiaoRateLimitException(
                        "Cainiao temporarily blocked the request (bot " +
                            "detection). Wait a few minutes and try again."
                    )
                }
                response
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("cainiao")
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideCainiaoApiService(@Named("cainiao") retrofit: Retrofit): CainiaoApiService =
        retrofit.create(CainiaoApiService::class.java)

    // Separate Retrofit for GitHub: api.github.com base URL, no Cainiao
    // browser-spoofing headers (GitHub blocks requests with cross-site Referers).
    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(gson: Gson): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubReleaseService(@Named("github") retrofit: Retrofit): GitHubReleaseService =
        retrofit.create(GitHubReleaseService::class.java)
}
