package com.vehiclelink.app.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.vehiclelink.app.core.VehicleBus

/**
 * Counts incoming SMS for the bike's message counter (original SmsReceiver -> onMessageReceiveListener).
 * Only the count is used; message contents are never read. Needs RECEIVE_SMS.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        VehicleBus.addSms()
    }
}
