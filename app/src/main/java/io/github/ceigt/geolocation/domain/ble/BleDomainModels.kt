package io.github.ceigt.geolocation.domain.ble

enum class BleDeliveryMode {
    HOOK_VIRTUAL,
    PHYSICAL_PERIPHERAL
}

enum class BleAdvertiseMode(val mode: Int) {
    LOW_POWER(android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_POWER),
    BALANCED(android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_BALANCED),
    LOW_LATENCY(android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
}

enum class BleTxPowerLevel(val level: Int) {
    ULTRA_LOW(android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW),
    LOW(android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_LOW),
    MEDIUM(android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM),
    HIGH(android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
}

data class BleManufacturerData(
    val manufacturerId: Int,
    val payloadHex: String
)

data class BleServiceData(
    val uuid: String,
    val payloadHex: String
)

data class BleAdvertisingProfile(
    val connectable: Boolean = false,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: List<BleManufacturerData> = emptyList(),
    val serviceData: List<BleServiceData> = emptyList(),
    val includeDeviceName: Boolean = true,
    val includeTxPower: Boolean = true,
    val legacyPreferred: Boolean = true,
    val intervalMode: BleAdvertiseMode = BleAdvertiseMode.BALANCED
)

data class BleSignalProfile(
    val baseRssi: Int = -58,
    val txPower: BleTxPowerLevel = BleTxPowerLevel.HIGH,
    val variationEnabled: Boolean = true
)

data class BleGattCharacteristicProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uuid: String,
    val properties: Set<String> = setOf("READ"),
    val permissions: Set<String> = setOf("READ"),
    val fixedReadValueHex: String = ""
)

data class BleGattServiceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uuid: String,
    val primary: Boolean = true,
    val characteristics: List<BleGattCharacteristicProfile> = emptyList()
)

data class BleDeviceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val displayName: String = "Virtual Beacon",
    val virtualAddress: String = "FA:CE:88:99:00:11",
    val advertising: BleAdvertisingProfile = BleAdvertisingProfile(),
    val signal: BleSignalProfile = BleSignalProfile(),
    val gattServices: List<BleGattServiceProfile> = emptyList()
)

data class BleProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "默认蓝牙配置",
    val devices: List<BleDeviceProfile> = emptyList(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
