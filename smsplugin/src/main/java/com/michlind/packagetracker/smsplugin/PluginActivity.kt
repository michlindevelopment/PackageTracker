package com.michlind.packagetracker.smsplugin

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

private const val REQUEST_READ_SMS = 1

/**
 * Android blocks SMS access for apps that didn't come from an app store, and
 * the only way through is a menu the system deliberately doesn't advertise.
 * Spelling it out beats leaving people at a switch that refuses to move.
 */
private const val BLOCKED_STEPS =
    "Android blocked it.\n\n" +
        "Apps installed outside the Play Store need one extra approval:\n\n" +
        "1. Tap \"Open App info\" below\n" +
        "2. Tap ⋮ (top-right)\n" +
        "3. Tap \"Allow restricted settings\"\n" +
        "4. Permissions → SMS → Allow\n\n" +
        "You only do this once."

/**
 * The plugin's entire UI: say whether SMS access is granted, and offer the one
 * button that fixes it. Everything else about this app happens in
 * [TrackingSmsProvider], invisibly, at the main app's request.
 *
 * Views are built in code rather than XML to keep the APK as small as possible
 * — every kilobyte here is one the user has to re-download through a Play
 * Protect warning if this ever needs updating.
 */
class PluginActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var action: Button

    /** Set once a permission request comes back refused — see [render]. */
    private var wasBlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        root.addView(TextView(this).apply {
            text = "AliTrack SMS Plugin"
            textSize = 22f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "This plugin lets AliTrack find delivery messages for your " +
                "parcels. It has no other purpose and nothing else to show — " +
                "you can leave it installed and forget about it."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        })

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad / 2)
        }
        root.addView(status)

        action = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            setOnClickListener { onActionClicked() }
        }
        root.addView(action)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun granted(): Boolean =
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun render() {
        when {
            granted() -> {
                status.text = "✓ SMS access granted\n\nNothing else to do here."
                status.setTextColor(Color.parseColor("#2e7d32"))
                action.text = "Open app settings"
            }
            // Android refused the request without the user ever seeing a dialog.
            // That is the Restricted Settings lock, and no amount of re-asking
            // clears it — it can only be lifted from App info, so stop offering
            // a button that cannot work and give the actual steps instead.
            wasBlocked -> {
                status.text = BLOCKED_STEPS
                status.setTextColor(Color.parseColor("#c62828"))
                action.text = "Open App info"
            }
            else -> {
                status.text = "SMS access not granted"
                status.setTextColor(Color.parseColor("#c62828"))
                action.text = "Grant SMS access"
            }
        }
    }

    private fun onActionClicked() {
        // Granted or blocked, App info is the only useful destination; only a
        // fresh, never-refused state is worth spending a permission request on.
        if (granted() || wasBlocked) {
            openAppInfo()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_READ_SMS) return
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            wasBlocked = true
        }
        render()
    }

    private fun openAppInfo() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}
