package com.vehiclelink.app

import android.app.Application
import com.vehiclelink.app.media.MediaHub

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.vehiclelink.app.core.AppSettings.init(this)
        com.vehiclelink.app.core.VehicleStore.init(this)
        // Try to attach media now; it silently no-ops until notification access is on.
        MediaHub.ensureStarted(this)
    }
}
