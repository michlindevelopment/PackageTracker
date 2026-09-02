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
        if (granted()) {
            status.text = "✓ SMS access granted"
            status.setTextColor(Color.parseColor("#2e7d32"))
            action.text = "Open app settings"
        } else {
            status.text = "SMS access not granted"
            status.setTextColor(Color.parseColor("#c62828"))
            action.text = "Grant SMS access"
        }
    }

    private fun onActionClicked() {
        if (granted()) {
            openAppInfo()
            return
        }
        // shouldShowRequestPermissionRationale() goes false once the user has
        // permanently denied — and it is also false on a first ask, so only
        // trust it after at least one request. Simplest correct behaviour: ask,
        // and if the system returns instantly without a dialog (the toggle is
        // locked by Restricted Settings, or permanently denied), send them to
        // App info where both are fixable.
        requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_READ_SMS) return
        render()
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            status.text = "Blocked. Open App info → ⋮ → Allow restricted " +
                "settings, then Permissions → SMS → Allow."
        }
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
