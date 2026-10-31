package io.github.ceigt.geolocation.domain.environment

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile
import io.github.ceigt.geolocation.data.model.environment.VirtualCellTower
import io.github.ceigt.geolocation.data.model.environment.VirtualSatellite
import io.github.ceigt.geolocation.data.model.environment.VirtualWifiAp
import io.github.ceigt.geolocation.domain.ble.BleDeviceProfile
import io.github.ceigt.geolocation.domain.ble.BleSignalProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class EnvironmentCollection(
    val profile: EnvironmentProfile,
    val warnings: List<String>
)

/**
 * Collects the current physical environment from Android's public APIs.
 *
 * Every source is sampled independently and in parallel. A source that is unavailable (no
 * permission, radio disabled, or no callback within its timeout) contributes an empty list plus a
 * human-readable warning; it never contributes fabricated sample data.
 */
class EnvironmentCollector(context: Context) {
    private companion object {
        const val LOCATION_TIMEOUT_MS = 5_000L
        const val WIFI_TIMEOUT_MS = 6_000L
        const val BLE_TIMEOUT_MS = 5_000L
        const val GNSS_TIMEOUT_MS = 6_000L
        const val UNKNOWN_SIGNAL_DBM = -127
    }

    private val appContext = context.applicationContext
    private val fineLocationGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    suspend fun collect(): EnvironmentCollection = coroutineScope {
        val locationTask = async(Dispatchers.IO) { collectLocation() }
        val wifiTask = async(Dispatchers.IO) { collectWifi() }
        val cellTask = async(Dispatchers.IO) { collectCells() }
        val bleTask = async(Dispatchers.IO) { collectBle() }
        val gnssTask = async(Dispatchers.IO) { collectSatellites() }

        val location = locationTask.await()
        val wifi = wifiTask.await()
        val cells = cellTask.await()
        val ble = bleTask.await()
        val gnss = gnssTask.await()

        val profile = EnvironmentProfile(
            name = "全要素快照-${SimpleDateFormat("MMdd-HHmmss", Locale.ROOT).format(Date())}",
            latitude = location.value?.latitude,
            longitude = location.value?.longitude,
            altitude = location.value?.altitude,
            wifiAps = wifi.value,
            cellTowers = cells.value,
            bleBeacons = ble.value,
            satellites = gnss.value
        )

        EnvironmentCollection(
            profile = profile,
            warnings = listOfNotNull(
                location.warning,
                wifi.warning,
                cells.warning,
                ble.warning,
                gnss.warning
            )
        )
    }

    // region Location

    private suspend fun collectLocation(): Capture<Location?> {
        if (!fineLocationGranted) {
            return Capture(null, "未授予精准定位权限，坐标未采集")
        }
        val manager = appContext.getSystemService(LocationManager::class.java)
            ?: return Capture(null, "定位服务不可用")

        val lastKnown = runCatching {
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.FUSED_PROVIDER
            ).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
        }.getOrNull()

        val startedAt = SystemClock.elapsedRealtime()
        var current: Location? = null
        val providers = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).distinct()
        for (provider in providers) {
            val remaining = LOCATION_TIMEOUT_MS - (SystemClock.elapsedRealtime() - startedAt)
            if (remaining <= 0L) break
            val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled && provider != LocationManager.FUSED_PROVIDER) continue
            current = runCatching { awaitCurrentLocation(manager, provider, remaining) }.getOrNull()
            if (current != null) break
        }

        val newest = listOfNotNull(current, lastKnown).maxByOrNull { it.time }
        return if (newest == null) {
            Capture(null, "未获取到实时坐标（定位开关关闭、无信号或超时），已使用上次已知位置为空")
        } else {
            Capture(newest, null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(
        manager: LocationManager,
        provider: String,
        timeoutMs: Long
    ): Location? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
    }

    // endregion

    // region Wi-Fi

    @SuppressLint("MissingPermission")
    private suspend fun collectWifi(): Capture<List<VirtualWifiAp>> {
        if (!fineLocationGranted) {
            return Capture(emptyList(), "未授予精准定位权限，Wi-Fi 未采集")
        }
        val manager = appContext.getSystemService(WifiManager::class.java)
            ?: return Capture(emptyList(), "Wi-Fi 服务不可用")
        val enabled = runCatching { manager.isWifiEnabled }.getOrDefault(false)
        if (!enabled) {
            return Capture(emptyList(), "Wi-Fi 已关闭，未采集热点")
        }

        val scanned = runCatching {
            withTimeoutOrNull(WIFI_TIMEOUT_MS) {
                callbackFlow {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                                trySend(runCatching { manager.scanResults.orEmpty() }.getOrDefault(emptyList()))
                            }
                        }
                    }
                    ContextCompat.registerReceiver(
                        appContext,
                        receiver,
                        IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    runCatching { manager.startScan() }
                    awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
                }.firstOrNull()
            }
        }.getOrNull()

        // Some ROMs throttle startScan without sending the broadcast; cached results are still
        // real measurements and substantially better than an empty snapshot.
        val results = scanned ?: runCatching { manager.scanResults.orEmpty() }.getOrDefault(emptyList())
        val aps = results.map { result ->
            VirtualWifiAp(
                ssid = result.SSID?.takeIf { it.isNotBlank() } ?: "Hidden_SSID",
                bssid = result.BSSID ?: "00:00:00:00:00:00",
                capabilities = result.capabilities ?: "[ESS]",
                levelDbm = result.level,
                frequencyMhz = result.frequency
            )
        }.distinctBy { it.bssid }

        return if (aps.isEmpty()) {
            Capture(emptyList(), "Wi-Fi 扫描无结果（系统定位开关关闭或扫描被限流）")
        } else {
            Capture(aps, null)
        }
    }

    // endregion

    // region Cellular

    @SuppressLint("MissingPermission")
    private fun collectCells(): Capture<List<VirtualCellTower>> {
        if (!fineLocationGranted) {
            return Capture(emptyList(), "未授予精准定位权限，基站未采集")
        }
        val manager = appContext.getSystemService(TelephonyManager::class.java)
            ?: return Capture(emptyList(), "电话服务不可用")
        val infos = runCatching { manager.allCellInfo }.getOrNull().orEmpty()
        val towers = infos.mapNotNull(::toVirtualTower).distinct()
        return if (towers.isEmpty()) {
            Capture(emptyList(), "未读取到基站信息（可能为飞行模式、无 SIM 或系统定位关闭）")
        } else {
            Capture(towers, null)
        }
    }

    private fun toVirtualTower(info: CellInfo): VirtualCellTower? = when (info) {
        is CellInfoGsm -> info.cellIdentity.toTower(info.cellSignalStrength.dbm)
        is CellInfoCdma -> info.cellIdentity.toTower(info.cellSignalStrength.dbm)
        is CellInfoWcdma -> info.cellIdentity.toTower(info.cellSignalStrength.dbm)
        is CellInfoLte -> info.cellIdentity.toTower(info.cellSignalStrength.rsrp.takeIfAvailable()
            ?: info.cellSignalStrength.dbm.takeIfAvailable())
        is CellInfoNr -> (info.cellIdentity as? CellIdentityNr)
            ?.toTower(info.cellSignalStrength.dbm.takeIfAvailable())
        else -> null
    }

    private fun CellIdentityGsm.toTower(dbm: Int?) = VirtualCellTower(
        radioType = "GSM",
        mcc = mccString?.toIntOrNull() ?: 0,
        mnc = mncString?.toIntOrNull() ?: 0,
        lacOrTac = lac,
        cidOrCi = cid.toLong(),
        pci = bsic,
        arfcn = arfcn,
        rssiDbm = dbm.takeIfAvailable() ?: UNKNOWN_SIGNAL_DBM
    )

    private fun CellIdentityCdma.toTower(dbm: Int?) = VirtualCellTower(
        radioType = "CDMA",
        mcc = 0,
        mnc = networkId,
        lacOrTac = systemId,
        cidOrCi = basestationId.toLong(),
        pci = 0,
        arfcn = 0,
        rssiDbm = dbm.takeIfAvailable() ?: UNKNOWN_SIGNAL_DBM
    )

    private fun CellIdentityWcdma.toTower(dbm: Int?) = VirtualCellTower(
        radioType = "WCDMA",
        mcc = mccString?.toIntOrNull() ?: 0,
        mnc = mncString?.toIntOrNull() ?: 0,
        lacOrTac = lac,
        cidOrCi = cid.toLong(),
        pci = psc,
        arfcn = uarfcn,
        rssiDbm = dbm.takeIfAvailable() ?: UNKNOWN_SIGNAL_DBM
    )

    private fun CellIdentityLte.toTower(dbm: Int?) = VirtualCellTower(
        radioType = "LTE",
        mcc = mccString?.toIntOrNull() ?: 0,
        mnc = mncString?.toIntOrNull() ?: 0,
        lacOrTac = tac,
        cidOrCi = ci.toLong(),
        pci = pci,
        arfcn = earfcn,
        rssiDbm = dbm.takeIfAvailable() ?: UNKNOWN_SIGNAL_DBM
    )

    private fun CellIdentityNr.toTower(dbm: Int?) = VirtualCellTower(
        radioType = "NR (5G)",
        mcc = mccString?.toIntOrNull() ?: 0,
        mnc = mncString?.toIntOrNull() ?: 0,
        lacOrTac = tac,
        cidOrCi = nci.takeIf { it != CellInfo.UNAVAILABLE_LONG } ?: 0L,
        pci = pci,
        arfcn = nrarfcn,
        rssiDbm = dbm.takeIfAvailable() ?: UNKNOWN_SIGNAL_DBM
    )

    private fun Int?.takeIfAvailable(): Int? =
        this?.takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE }

    // endregion

    // region Bluetooth LE

    @SuppressLint("MissingPermission")
    private suspend fun collectBle(): Capture<List<BleDeviceProfile>> {
        if (!hasBleScanPermission()) {
            return Capture(emptyList(), "未授予蓝牙扫描权限，BLE 未采集")
        }
        val manager = appContext.getSystemService(BluetoothManager::class.java)
            ?: return Capture(emptyList(), "蓝牙服务不可用")
        val adapter = manager.adapter ?: return Capture(emptyList(), "蓝牙适配器不可用")
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) {
            return Capture(emptyList(), "蓝牙已关闭，未采集 BLE 信标")
        }
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull()
            ?: return Capture(emptyList(), "BLE 扫描器不可用")

        val found = ConcurrentHashMap<String, BleDeviceProfile>()
        runCatching {
            withTimeoutOrNull(BLE_TIMEOUT_MS) {
                callbackFlow<Unit> {
                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult?) {
                            result?.let { addBleResult(found, it) }
                        }

                        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                            results?.forEach { addBleResult(found, it) }
                        }
                    }
                    runCatching { scanner.startScan(callback) }
                    awaitClose { runCatching { scanner.stopScan(callback) } }
                }.collect { }
            }
        }

        val beacons = found.values.sortedByDescending { it.signal.baseRssi }
        return if (beacons.isEmpty()) {
            Capture(emptyList(), "未发现 BLE 信标（蓝牙扫描被限流或周边无广播）")
        } else {
            Capture(beacons, null)
        }
    }

    private fun hasBleScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun addBleResult(
        found: ConcurrentHashMap<String, BleDeviceProfile>,
        result: ScanResult
    ) {
        val address = runCatching { result.device.address }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
            ?: return
        val name = runCatching { result.scanRecord?.deviceName }.getOrNull()
            ?: runCatching { result.device.name }.getOrNull()
            ?: "BLE Beacon"
        found[address] = BleDeviceProfile(
            displayName = name,
            virtualAddress = address,
            signal = BleSignalProfile(baseRssi = result.rssi)
        )
    }

    // endregion

    // region GNSS

    @SuppressLint("MissingPermission")
    private suspend fun collectSatellites(): Capture<List<VirtualSatellite>> {
        if (!fineLocationGranted) {
            return Capture(emptyList(), "未授予精准定位权限，卫星未采集")
        }
        val manager = appContext.getSystemService(LocationManager::class.java)
            ?: return Capture(emptyList(), "定位服务不可用")

        val latest = AtomicReference<List<VirtualSatellite>?>(null)
        runCatching {
            withTimeoutOrNull(GNSS_TIMEOUT_MS) {
                callbackFlow<Unit> {
                    val callback = object : GnssStatus.Callback() {
                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                            latest.set((0 until status.satelliteCount).map { index ->
                                VirtualSatellite(
                                    svid = status.getSvid(index),
                                    constellationType = status.getConstellationType(index),
                                    cn0DbHz = status.getCn0DbHz(index),
                                    elevationDegrees = status.getElevationDegrees(index),
                                    azimuthDegrees = status.getAzimuthDegrees(index),
                                    usedInFix = status.usedInFix(index)
                                )
                            })
                            trySend(Unit)
                        }
                    }
                    val registered = runCatching {
                        manager.registerGnssStatusCallback(appContext.mainExecutor, callback)
                    }.getOrDefault(false)
                    if (!registered) {
                        close()
                        return@callbackFlow
                    }
                    awaitClose {
                        runCatching { manager.unregisterGnssStatusCallback(callback) }
                    }
                }.collect { }
            }
        }

        val satellites = latest.get().orEmpty()
        return if (satellites.isEmpty()) {
            Capture(emptyList(), "未收到卫星状态（室内、GPS 关闭或设备不支持 GNSS 状态回调）")
        } else {
            Capture(satellites, null)
        }
    }

    // endregion

    private data class Capture<T>(val value: T, val warning: String?)
}
