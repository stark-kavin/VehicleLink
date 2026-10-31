package com.vehiclelink.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.vehiclelink.app.media.MediaHub
import com.vehiclelink.app.ui.VehicleLinkScreen
import com.vehiclelink.app.ui.theme.VehicleLinkTheme

class MainActivity : ComponentActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw under the status/navigation bars; the screen pads itself with the system-bar insets.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        requestRuntimePerms()
        setContent {
            VehicleLinkTheme {
                VehicleLinkScreen(
                    onOpenNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-attach media in case notification access was just granted.
        MediaHub.ensureStarted(applicationContext)
        MediaHub.refresh()
    }

    private fun requestRuntimePerms() {
        val perms = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += android.Manifest.permission.BLUETOOTH_SCAN
            perms += android.Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += android.Manifest.permission.POST_NOTIFICATIONS
        }
        perms += android.Manifest.permission.READ_PHONE_STATE
        perms += android.Manifest.permission.ANSWER_PHONE_CALLS
        perms += android.Manifest.permission.READ_CALL_LOG
        perms += android.Manifest.permission.READ_CONTACTS
        // SMS counter is on by default; SEND_SMS is only asked for when "text the caller" is switched on.
        if (com.vehiclelink.app.core.AppSettings.current.smsCounter) perms += android.Manifest.permission.RECEIVE_SMS
        permLauncher.launch(perms.toTypedArray())
    }
}
