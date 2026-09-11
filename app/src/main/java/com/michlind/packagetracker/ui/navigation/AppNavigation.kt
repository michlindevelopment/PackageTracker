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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.michlind.packagetracker.ui.add.AddEditScreen
import com.michlind.packagetracker.ui.alilogin.AliLoginScreen
import com.michlind.packagetracker.ui.attach.AttachImageSheet
import com.michlind.packagetracker.ui.captcha.CaptchaScreen
import com.michlind.packagetracker.ui.debug.RawResponseScreen
import com.michlind.packagetracker.ui.detail.DetailScreen
import com.michlind.packagetracker.ui.home.HomeScreen
import com.michlind.packagetracker.ui.search.SearchScreen
import com.michlind.packagetracker.ui.settings.SettingsScreen
import com.michlind.packagetracker.ui.statistics.StatisticsScreen

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
                    navController.navigateFromUser(Screen.Detail.createRoute(id))
                },
                onAddClick = {
                    navController.navigateFromUser(Screen.AddEdit.createRoute())
                },
                onSettingsClick = {
                    navController.navigateFromUser(Screen.Settings.route)
                },
                onSearchClick = {
                    navController.navigateFromUser(Screen.Search.route)
                },
                onStatisticsClick = {
                    navController.navigateFromUser(Screen.Statistics.route)
                },
                onSignInToAliExpress = {
                    navController.navigateFromUser(Screen.AliLogin.route)
                },
                onVerifyCaptcha = { trackingNumber ->
                    navController.navigateFromUser(Screen.Captcha.createRoute(trackingNumber))
                },
                refreshAndShowInTransit = refreshSignal,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["aliImportDone"] = false
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onPackageClick = { id ->
                    navController.navigateFromUser(Screen.Detail.createRoute(id))
                }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
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
                    navController.navigateFromUser(Screen.AddEdit.createRoute(id))
                },
                onShowRawResponse = {
                    navController.navigateFromUser(Screen.RawResponse.createRoute(packageId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RawResponse.route,
            arguments = listOf(navArgument("packageId") { type = NavType.LongType })
        ) {
            RawResponseScreen(
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

/**
 * [NavController.navigate] for taps: only fires while the current screen is
 * fully settled ([Lifecycle.State.RESUMED]).
 *
 * The moment a navigation starts, the source entry drops to STARTED for the
 * length of the transition, so the second and third tap of a fast double- or
 * triple-tap arrive while the screen is not RESUMED and are dropped instead of
 * pushing duplicate destinations onto the back stack. Programmatic navigation
 * (notification deep link, pop-then-push after saving) must keep using plain
 * [NavController.navigate] — it runs while nothing is RESUMED yet.
 */
private fun NavController.navigateFromUser(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}
