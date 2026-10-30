package com.vehiclelink.app.ble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vehiclelink.app.MainActivity

/**
 * Quick Settings tile: turns the vehicle link on (reconnect to the last bike) and off
 * (disconnect + stop the foreground service). Opens the app when there is no bike yet.
 */
class VehicleLinkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(BleVehicleService.running)
    }

    override fun onClick() {
        super.onClick()
        if (BleVehicleService.running) {
            BleVehicleService.disconnect(this)
            render(false)
            return
        }
        if (BleVehicleService.lastBike(this) == null || !hasBlePerms()) { openApp(); return }
        try {
            BleVehicleService.resume(this)
            render(true)
        } catch (_: Exception) {
            // Android 12+ can refuse a background foreground-service start; let the app start it.
            openApp()
        }
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "VehicleLink"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (on) "On" else "Off"
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    private fun hasBlePerms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** Ask System UI to re-query the tile (no-op if the panel isn't showing it). */
        fun refresh(ctx: Context) {
            try {
                requestListeningState(ctx, ComponentName(ctx, VehicleLinkTileService::class.java))
            } catch (_: Exception) {}
        }
    }
}
