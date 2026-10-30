package com.vehiclelink.app.ble

import java.util.UUID

object BleConstants {
    // --- ESP32 VehicleLink firmware (Nordic UART) ---
    val UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val UART_RX_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val UART_TX_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    // --- Hero Karizma XMR cluster (Telit TIO UART) ---
    val CLUSTER_SERVICE: UUID = UUID.fromString("e837d9a2-9c49-4493-9547-8e6918a59ca8")
    val CLUSTER_DATA_RX: UUID = UUID.fromString("64aecb40-849a-44f1-934f-addc4b316423")  // write-no-resp
    val CLUSTER_CREDITS_RX: UUID = UUID.fromString("f535dd6e-7975-4abd-9719-491e38a81179") // write
    val CLUSTER_DATA_TX: UUID = UUID.fromString("b792a4bb-db87-436a-9066-db63c5fb3f00")  // notify
    val CLUSTER_CREDITS_TX: UUID = UUID.fromString("36e8614b-2ded-45c1-9ad3-c9f59a01f21e") // indicate
    const val CLUSTER_CREDITS = 10

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val DEFAULT_NAME_FILTER = "MCU"
    const val DEFAULT_MTU = 65
}
