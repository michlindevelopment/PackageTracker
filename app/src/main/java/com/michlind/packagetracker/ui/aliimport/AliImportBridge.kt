package com.michlind.packagetracker.ui.aliimport

import android.webkit.JavascriptInterface

sealed interface AliImportEvent {
    data class Progress(val message: String) : AliImportEvent
    data class Total(val total: Int) : AliImportEvent
    data class Order(val json: String, val index: Int, val total: Int) : AliImportEvent
    data object Complete : AliImportEvent
    data class Error(val message: String) : AliImportEvent
}

class AliImportBridge(private val sink: (AliImportEvent) -> Unit) {
    @JavascriptInterface
    fun onProgress(message: String) { sink(AliImportEvent.Progress(message)) }

    @JavascriptInterface
    fun onTotal(total: Int) { sink(AliImportEvent.Total(total)) }

    @JavascriptInterface
    fun onOrder(json: String, index: Int, total: Int) { sink(AliImportEvent.Order(json, index, total)) }

    @JavascriptInterface
    fun onComplete() { sink(AliImportEvent.Complete) }

    @JavascriptInterface
    fun onError(message: String) { sink(AliImportEvent.Error(message)) }
}
