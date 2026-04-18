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
}
