package com.michlind.packagetracker.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.michlind.packagetracker.ui.add.AddEditScreen
import com.michlind.packagetracker.ui.alilogin.AliLoginScreen
import com.michlind.packagetracker.ui.attach.AttachImageSheet
import com.michlind.packagetracker.ui.captcha.CaptchaScreen
import com.michlind.packagetracker.ui.contributors.ContributorsScreen
import com.michlind.packagetracker.ui.detail.DetailScreen
import com.michlind.packagetracker.ui.home.HomeScreen
import com.michlind.packagetracker.ui.settings.SettingsScreen

@Composable
fun AppNavigation(startPackageId: Long? = null, sharedImageUri: Uri? = null) {
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
        composable(Screen.Home.route) { backStackEntry ->
            val refreshSignal by backStackEntry.savedStateHandle
                .getStateFlow("aliImportDone", false)
                .collectAsStateWithLifecycle()
            HomeScreen(
                onPackageClick = { id ->
                    navController.navigate(Screen.Detail.createRoute(id))
                },
                onAddClick = {
                    navController.navigate(Screen.AddEdit.createRoute())
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onSignInToAliExpress = {
                    navController.navigate(Screen.AliLogin.route)
                },
                onVerifyCaptcha = { trackingNumber ->
                    navController.navigate(Screen.Captcha.createRoute(trackingNumber))
                },
                refreshAndShowInTransit = refreshSignal,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["aliImportDone"] = false
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onContributorsClick = {
                    navController.navigate(Screen.Contributors.route)
                }
            )
        }

        composable(Screen.Contributors.route) {
            ContributorsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AliLogin.route) {
            AliLoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    // Signal Home that login just succeeded so it switches
                    // to In Transit and triggers fullFetchThenSyncStatus().
                    navController.getBackStackEntry(Screen.Home.route)
                        .savedStateHandle["aliImportDone"] = true
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.Captcha.route,
            arguments = listOf(navArgument("trackingNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val tn = backStackEntry.arguments?.getString("trackingNumber").orEmpty()
            CaptchaScreen(
                trackingNumber = tn,
                onBack = { navController.popBackStack() }
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
                    if (packageId != null) {
                        // Edit mode — Detail is already in the back stack; just pop AddEdit
                        navController.popBackStack()
                    } else {
                        // Add mode — pop AddEdit then navigate to the new Detail
                        navController.popBackStack()
                        navController.navigate(Screen.Detail.createRoute(id))
                    }
                }
            )
        }
    }

    var pendingSharedUri by remember { mutableStateOf(sharedImageUri) }
    pendingSharedUri?.let { uri ->
        AttachImageSheet(
            sharedImageUri = uri,
            onDismiss = { pendingSharedUri = null }
        )
    }
}
