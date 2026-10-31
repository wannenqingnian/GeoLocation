package io.github.ceigt.geolocation.data.model.environment

enum class EnvironmentSimulationTab {
    WIFI,
    CELLULAR,
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

data class EnvironmentProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "默认环境配置",
    val wifiEnabled: Boolean = true,
    val cellEnabled: Boolean = true,
    val suppressRealScan: Boolean = true,
    val wifiAps: List<VirtualWifiAp> = emptyList(),
    val cellTowers: List<VirtualCellTower> = emptyList()
)
