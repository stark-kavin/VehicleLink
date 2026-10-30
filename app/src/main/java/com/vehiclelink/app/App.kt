package com.vehiclelink.app

import android.app.Application
import com.vehiclelink.app.media.MediaHub

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Try to attach media now; it silently no-ops until notification access is on.
        MediaHub.ensureStarted(this)
    }
}
