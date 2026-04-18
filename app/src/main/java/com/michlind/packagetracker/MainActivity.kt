package com.michlind.packagetracker

import android.content.Intent
import android.net.Uri
import android.os.Build
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

        val startPackageId = intent?.getLongExtra("package_id", -1L)?.takeIf { it != -1L }
        val sharedImageUri = extractSharedImageUri(intent)

        setContent {
            PackageTrackerTheme {
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
