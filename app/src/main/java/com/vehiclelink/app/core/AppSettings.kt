package com.vehiclelink.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences, persisted in SharedPreferences and observable from Compose.
 * Read by the BLE service each time it needs a value, so changes apply immediately.
 */
object AppSettings {

    data class Values(
        /** Bike's answer button picks up a ringing call. */
        val bikeAnswer: Boolean = true,
        /** Bike's reject button declines a ringing call / ends the current one. */
        val bikeReject: Boolean = true,
        /** First reject press shows "Press again N" on the bike; only a second press within 5 s rejects. */
        val confirmReject: Boolean = false,
        /** After rejecting a ringing call from the bike, text the caller [rejectText]. */
        val rejectSms: Boolean = false,
        val rejectText: String = DEFAULT_REJECT_TEXT,
        /** Show the missed-call counter on the bike. */
        val missedCalls: Boolean = true,
        /** Show the new-SMS counter on the bike. */
        val smsCounter: Boolean = true,
        /** Raise the "no GPS signal" flag in navigation packets. */
        val gpsWarning: Boolean = true,
    )

    const val DEFAULT_REJECT_TEXT = "I'm riding right now. I'll call you back."

    private var prefs: SharedPreferences? = null
    private val _values = MutableStateFlow(Values())
    val values: StateFlow<Values> = _values.asStateFlow()
    val current: Values get() = _values.value

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("vehicle_link_settings", Context.MODE_PRIVATE)
        prefs = p
        val d = Values()
        _values.value = Values(
            bikeAnswer = p.getBoolean("bikeAnswer", d.bikeAnswer),
            bikeReject = p.getBoolean("bikeReject", d.bikeReject),
            confirmReject = p.getBoolean("confirmReject", d.confirmReject),
            rejectSms = p.getBoolean("rejectSms", d.rejectSms),
            rejectText = p.getString("rejectText", d.rejectText) ?: d.rejectText,
            missedCalls = p.getBoolean("missedCalls", d.missedCalls),
            smsCounter = p.getBoolean("smsCounter", d.smsCounter),
            gpsWarning = p.getBoolean("gpsWarning", d.gpsWarning),
        )
    }

    fun update(change: (Values) -> Values) {
        val v = change(_values.value)
        _values.value = v
        prefs?.edit()
            ?.putBoolean("bikeAnswer", v.bikeAnswer)
            ?.putBoolean("bikeReject", v.bikeReject)
            ?.putBoolean("confirmReject", v.confirmReject)
            ?.putBoolean("rejectSms", v.rejectSms)
            ?.putString("rejectText", v.rejectText)
            ?.putBoolean("missedCalls", v.missedCalls)
            ?.putBoolean("smsCounter", v.smsCounter)
            ?.putBoolean("gpsWarning", v.gpsWarning)
            ?.apply()
    }
}
