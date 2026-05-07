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
    data object Settings : Screen("settings")
    data object AliLogin : Screen("aliexpress_login")
    data object Contributors : Screen("contributors")
    data object Captcha : Screen("captcha/{trackingNumber}") {
        fun createRoute(trackingNumber: String) = "captcha/$trackingNumber"
    }
}
