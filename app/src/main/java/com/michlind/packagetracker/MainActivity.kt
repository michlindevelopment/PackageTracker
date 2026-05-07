package com.michlind.packagetracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.michlind.packagetracker.data.preferences.ThemePreferenceRepository
import com.michlind.packagetracker.domain.model.ThemePreference
import com.michlind.packagetracker.ui.navigation.AppNavigation
import com.michlind.packagetracker.ui.theme.PackageTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

//TODO
/*

* Packages already imported skip import by pack number

* Improve understanding for ali import (main screen plus) and text - V
* Disable add from view all when nothing shopped - V
* Hide tamir in settings - V
* Better UI order - V
* Better login screen - X
* Long click delete mark as received - V
* Bug - package both in no yet sent and delivered - V
* During import keep screen on - V
* Done and Start sometimes not working - V
* Update items that received but in received status - V
* Add option to reimport for items without tracking number
* */

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeRepo: ThemePreferenceRepository

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startPackageId = intent?.getLongExtra("package_id", -1L)?.takeIf { it != -1L }
        val sharedImageUri = extractSharedImageUri(intent)

        setContent {
            val themePref by themeRepo.theme.collectAsStateWithLifecycle()
            val isDark = when (themePref) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            PackageTrackerTheme(darkTheme = isDark) {
                // Keep the system status / navigation bar icon colors in sync
                // with the app theme — otherwise white icons disappear on a
                // light app background and dark icons disappear on a dark one.
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !isDark
                            isAppearanceLightNavigationBars = !isDark
                        }
                    }
                }

                // Ask for the runtime permissions the app needs at launch:
                //   - POST_NOTIFICATIONS (API 33+): status-change pings.
                //   - READ_SMS: scanning the inbox for tracking-number hits.
                // Bundling them via rememberMultiplePermissionsState avoids
                // the "second prompt is silently suppressed" race we'd hit
                // if MainActivity and HomeScreen each fired their own
                // single-permission requests in quick succession.
                val permissions = buildList {
                    add(Manifest.permission.READ_SMS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val multiPermissions = rememberMultiplePermissionsState(permissions)
                LaunchedEffect(Unit) {
                    if (!multiPermissions.allPermissionsGranted) {
                        multiPermissions.launchMultiplePermissionRequest()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        startPackageId = startPackageId,
                        sharedImageUri = sharedImageUri
                    )
                }
            }
        }
    }

    private fun extractSharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }
}
