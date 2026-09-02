package com.michlind.packagetracker.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Detail : Screen("detail/{packageId}") {
        fun createRoute(packageId: Long) = "detail/$packageId"
    }
    data object AddEdit : Screen("add_edit?packageId={packageId}") {
        fun createRoute(packageId: Long? = null) =
            if (packageId != null) "add_edit?packageId=$packageId" else "add_edit"
    }
    data object Search : Screen("search")
    data object Statistics : Screen("statistics")
    data object Settings : Screen("settings")
    data object AliLogin : Screen("aliexpress_login")
    data object Captcha : Screen("captcha/{trackingNumber}") {
        fun createRoute(trackingNumber: String) = "captcha/$trackingNumber"
    }
    data object RawResponse : Screen("raw_response/{packageId}") {
        fun createRoute(packageId: Long) = "raw_response/$packageId"
    }
}
