package com.vehiclelink.app.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.vehiclelink.app.core.VehicleBus

/**
 * Tracks call state for the cluster. Needs READ_PHONE_STATE (+ READ_CALL_LOG for the
 * number on Android 9+, READ_CONTACTS to resolve a name).
 */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        // Android 9+ delivers PHONE_STATE twice; only the READ_CALL_LOG copy has the number.
        // Like the original, ignore the number-less copy so it can't overwrite the resolved name.
        if (number.isNullOrBlank() && state != TelephonyManager.EXTRA_STATE_IDLE &&
            context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val who = resolveName(context, number) ?: "Unknown"
                VehicleBus.setCall(VehicleBus.CallInfo(ringing = true, active = false, name = who))
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val cur = VehicleBus.call.value
                if (cur?.active == true) return
                // Rang first -> answered incoming call; otherwise we placed the call.
                val outgoing = cur?.ringing != true
                val who = if (outgoing) resolveName(context, number) ?: "Unknown" else cur!!.name
                VehicleBus.setCall(
                    VehicleBus.CallInfo(ringing = false, active = true, name = who, outgoing = outgoing)
                )
            }
            TelephonyManager.EXTRA_STATE_IDLE -> VehicleBus.setCall(null)
        }
    }

    private fun resolveName(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        return try {
            if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return null
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
    }
}
