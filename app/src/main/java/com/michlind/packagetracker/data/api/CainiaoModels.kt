package com.michlind.packagetracker.data.api

import com.google.gson.annotations.SerializedName

data class CainiaoResponse(
    @SerializedName("module") val module: List<CainiaoPackageData>?,
    @SerializedName("success") val success: Boolean = false
)

data class CainiaoPackageData(
    @SerializedName("mailNo") val mailNo: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("statusDesc") val statusDesc: String?,
    @SerializedName("detailList") val detailList: List<CainiaoTraceEvent>?,
    @SerializedName("latestTrace") val latestTrace: CainiaoTraceEvent?,
    @SerializedName("daysNumber") val daysNumber: String?,
    @SerializedName("originCountry") val originCountry: String?,
    @SerializedName("destCountry") val destCountry: String?,
    @SerializedName("estimatedDeliveryTime") val estimatedDeliveryTime: Long?,
    @SerializedName("processInfo") val processInfo: ProcessInfo?,
    @SerializedName("logisticsAlert") val logisticsAlert: LogisticsAlert?
)

data class CainiaoTraceEvent(
    @SerializedName("time") val time: Long?,
    @SerializedName("timeStr") val timeStr: String?,
    @SerializedName("desc") val desc: String?,
    @SerializedName("standerdDesc") val standerdDesc: String?,
    @SerializedName("descTitle") val descTitle: String?,
    @SerializedName("timeZone") val timeZone: String?,
    @SerializedName("actionCode") val actionCode: String?,
    @SerializedName("group") val group: TraceGroup?
)

data class TraceGroup(
    @SerializedName("nodeCode") val nodeCode: String?,
    @SerializedName("nodeDesc") val nodeDesc: String?,
    // Icon to show on the timeline for the *latest* event in this group
    // (the one currently being rendered as the head of the trace). 48×48
    // PNG hosted on alicdn — pass straight to AsyncImage.
    @SerializedName("currentIconUrl") val currentIconUrl: String?,
    // Icon for past events of the same group — same source, drawn in a
    // muted "completed" style on alicdn's side.
    @SerializedName("historyIconUrl") val historyIconUrl: String?
)

data class ProcessInfo(
    @SerializedName("progressStatus") val progressStatus: String?,
    @SerializedName("progressRate") val progressRate: Float?,
    @SerializedName("progressPointList") val progressPointList: List<ProgressPoint>?
)

data class ProgressPoint(
    @SerializedName("pointName") val pointName: String?,
    @SerializedName("light") val light: Boolean?
)

data class LogisticsAlert(
    @SerializedName("title") val title: String?,
    @SerializedName("desc") val desc: String?,
    @SerializedName("type") val type: String?
)
