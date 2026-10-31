package com.vehiclelink.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rider's garage: every vehicle VehicleLink has connected to, most recent first.
 * A vehicle is saved the first time a connection to it completes (the original app does the same
 * with its SaveNdConnectVdsMapping list after pairing).
 */
object VehicleStore {

    data class Vehicle(
        val address: String,
        /** Raw Bluetooth name the vehicle advertises, e.g. "MCU01************". */
        val bleName: String,
        /** What the rider calls it; editable. */
        val nickname: String,
        val lastConnected: Long,
    )

    /** Default name for a new vehicle. Hero cluster names start with "MCU". */
    fun defaultName(bleName: String): String = when {
        bleName.startsWith("MCU", ignoreCase = true) -> "Hero Karizma XMR CE"
        bleName.isNotBlank() && !bleName.matches(Regex("[A-Z0-9_\\-]{4,}")) -> bleName
        else -> "My vehicle"
    }

    private var prefs: SharedPreferences? = null
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("vehicle_link_garage", Context.MODE_PRIVATE)
        prefs = p
        _vehicles.value = runCatching {
            val arr = JSONArray(p.getString("vehicles", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Vehicle(o.getString("address"), o.optString("bleName"), o.optString("nickname"), o.optLong("lastConnected"))
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.lastConnected }
    }

    /** Called by the BLE service when a connection to [address] completes. */
    fun recordConnected(address: String, bleName: String) {
        val now = System.currentTimeMillis()
        val cur = _vehicles.value
        val existing = cur.firstOrNull { it.address.equals(address, true) }
        val updated = existing?.copy(lastConnected = now, bleName = bleName.ifBlank { existing.bleName })
            ?: Vehicle(address, bleName, defaultName(bleName), now)
        save(listOf(updated) + cur.filterNot { it.address.equals(address, true) })
    }

    fun rename(address: String, nickname: String) {
        val name = nickname.trim().ifBlank { return }
        save(_vehicles.value.map { if (it.address.equals(address, true)) it.copy(nickname = name.take(40)) else it })
    }

    fun forget(address: String) = save(_vehicles.value.filterNot { it.address.equals(address, true) })

    fun find(address: String?): Vehicle? =
        address?.let { a -> _vehicles.value.firstOrNull { it.address.equals(a, true) } }

    private fun save(list: List<Vehicle>) {
        val sorted = list.sortedByDescending { it.lastConnected }
        _vehicles.value = sorted
        val arr = JSONArray()
        sorted.forEach {
            arr.put(JSONObject().put("address", it.address).put("bleName", it.bleName)
                .put("nickname", it.nickname).put("lastConnected", it.lastConnected))
        }
        prefs?.edit()?.putString("vehicles", arr.toString())?.apply()
    }
}
