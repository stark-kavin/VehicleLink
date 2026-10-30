package com.vehiclelink.app.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vehiclelink.app.media.MediaHub
import com.vehiclelink.app.nav.MapsNavParser

/**
 * Parses Google Maps navigation notifications into VehicleBus, and — because being
 * connected means media sessions are now readable — (re)starts the MediaHub.
 */
class CompanionListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaHub.ensureStarted(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        MapsNavParser.handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        MapsNavParser.onRemoved(sbn)
    }
}
