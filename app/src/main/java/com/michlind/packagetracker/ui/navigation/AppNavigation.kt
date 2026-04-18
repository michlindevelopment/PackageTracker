package com.michlind.packagetracker.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.michlind.packagetracker.ui.add.AddEditScreen
import com.michlind.packagetracker.ui.detail.DetailScreen
import com.michlind.packagetracker.ui.home.HomeScreen

@Composable
fun AppNavigation(startPackageId: Long? = null) {
    val navController = rememberNavController()

    // Navigate to detail if opened from notification
    LaunchedEffect(startPackageId) {
        if (startPackageId != null) {
            navController.navigate(Screen.Detail.createRoute(startPackageId))
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(300)
            )
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onPackageClick = { id ->
                    navController.navigate(Screen.Detail.createRoute(id))
                },
                onAddClick = {
                    navController.navigate(Screen.AddEdit.createRoute())
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("packageId") { type = NavType.LongType })
        ) { backStackEntry ->
            val packageId = backStackEntry.arguments?.getLong("packageId") ?: return@composable
            DetailScreen(
                packageId = packageId,
                onEditClick = { id ->
                    navController.navigate(Screen.AddEdit.createRoute(id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddEdit.route,
            arguments = listOf(
                navArgument("packageId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val packageId = backStackEntry.arguments?.getLong("packageId")
                ?.takeIf { it != -1L }
            AddEditScreen(
                packageId = packageId,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate(Screen.Detail.createRoute(id))
                }
            )
        }
    }
}
