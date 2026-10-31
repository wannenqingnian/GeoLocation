package io.github.ceigt.geolocation.data.model.environment

enum class EnvironmentSimulationTab {
    WIFI,
    CELLULAR,
    SATELLITES,
    BLE,
    COLLECTION
}

data class VirtualWifiAp(
    val ssid: String,
    val bssid: String,
    val capabilities: String = "[WPA2-PSK-CCMP][ESS]",
    val levelDbm: Int = -55,
    val frequencyMhz: Int = 2437
)

data class VirtualCellTower(
    val radioType: String = "LTE",
    val mcc: Int = 460,
    val mnc: Int = 0,
    val lacOrTac: Int = 4192,
    val cidOrCi: Long = 135792468L,
    val pci: Int = 100,
    val arfcn: Int = 1850,
    val rssiDbm: Int = -75
)

data class VirtualSatellite(
    val svid: Int,
    val constellationType: Int, // 1: GPS, 3: GLONASS, 5: BEIDOU, 6: GALILEO
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean = true
)

data class EnvironmentProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "默认环境配置",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val wifiEnabled: Boolean = true,
    val cellEnabled: Boolean = true,
    val bleEnabled: Boolean = true,
    val gnssEnabled: Boolean = true,
    val suppressRealScan: Boolean = true,
    val wifiAps: List<VirtualWifiAp> = emptyList(),
    val cellTowers: List<VirtualCellTower> = emptyList(),
    val bleBeacons: List<io.github.ceigt.geolocation.domain.ble.BleDeviceProfile> = emptyList(),
    val satellites: List<VirtualSatellite> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
