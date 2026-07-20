package com.michlind.packagetracker.ui.aliimport

import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface

sealed interface AliImportEvent {
    data class Progress(val message: String) : AliImportEvent
    data class Total(val total: Int) : AliImportEvent
    data class Order(val json: String, val index: Int, val total: Int) : AliImportEvent
    data object Complete : AliImportEvent
    data class Error(val message: String) : AliImportEvent
}

class AliImportBridge(private val sink: (AliImportEvent) -> Unit) {
    // JSON array of AliExpress orderIds we've already imported with a known
    // tracking number. The ViewModel populates this before injecting the
    // import script; the script reads it via getKnownOrderIds() and skips
    // the iframe tracking-number lookup for matching orders.
    @Volatile
    var knownOrderIdsJson: String = "[]"

    // JSON object of __AliImportConfig keys to override at runtime — populated
    // by the ViewModel from user settings (per-tab expand-pass budgets, etc.)
    // before the import script is injected.
    @Volatile
    var configOverridesJson: String = "{}"

    // Wall-clock t0 (SystemClock.elapsedRealtime) for the entire import, set by
    // the host WebView at mount — i.e. BEFORE the orders page even loads. The
    // terminal events log elapsed time under DTAG, so the true end-to-end cost
    // (page load + redirects + JS) is greppable next to the JS-side IMPORT
    // SUMMARY, which can only start counting once the script is injected.
    @Volatile
    var importStartRealtimeMs: Long = 0L

    private fun logWallClock(outcome: String) {
        val start = importStartRealtimeMs
        if (start <= 0L) return
        val elapsedMs = SystemClock.elapsedRealtime() - start
        val fmt = if (elapsedMs < 1000) "${elapsedMs}ms"
        else String.format("%.1fs", elapsedMs / 1000.0)
        Log.d("DTAG", "IMPORT WALL-CLOCK $outcome elapsed=$fmt")
    }

    @JavascriptInterface
    fun getKnownOrderIds(): String = knownOrderIdsJson

    @JavascriptInterface
    fun getConfigOverrides(): String = configOverridesJson

    // Diagnostic channel. The import script calls this at each tab/page
    // boundary so the scrape volume shows up under a single greppable logcat
    // tag (`adb logcat -s DTAG`). The host WebView also routes its own logs
    // (page loads, load errors, the `[Ali]` JS console stream) under DTAG, so
    // one `adb logcat -s DTAG` captures the entire import.
    @JavascriptInterface
    fun dlog(message: String) { Log.d("DTAG", message) }

    @JavascriptInterface
    fun onProgress(message: String) { sink(AliImportEvent.Progress(message)) }

    @JavascriptInterface
    fun onTotal(total: Int) { sink(AliImportEvent.Total(total)) }

    @JavascriptInterface
    fun onOrder(json: String, index: Int, total: Int) { sink(AliImportEvent.Order(json, index, total)) }

    @JavascriptInterface
    fun onComplete() {
        logWallClock("COMPLETE")
        sink(AliImportEvent.Complete)
    }

    @JavascriptInterface
    fun onError(message: String) {
        logWallClock("ERROR($message)")
        sink(AliImportEvent.Error(message))
    }
}
