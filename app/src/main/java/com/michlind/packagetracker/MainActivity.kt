package com.michlind.packagetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.michlind.packagetracker.ui.navigation.AppNavigation
import com.michlind.packagetracker.ui.theme.PackageTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle deep link from notification
        val startPackageId = intent?.getLongExtra("package_id", -1L)
            ?.takeIf { it != -1L }

        setContent {
            PackageTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(startPackageId = startPackageId)
                }
            }
        }
    }
}
