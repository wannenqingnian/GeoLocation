package io.github.ceigt.geolocation.xposed.hooks

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.domain.ble.BleDeviceProfile
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BleScannerHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[BleScannerHooks]"
    private val installedMethods = mutableSetOf<Method>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()

    private val activeScanCallbacks = Collections.newSetFromMap(ConcurrentHashMap<ScanCallback, Boolean>())
    @Volatile private var isLooping = false

    private val periodicBleDispatchRunnable = object : Runnable {
        override fun run() {
            if (!PreferencesUtil.getIsPlaying() || activeScanCallbacks.isEmpty()) {
                isLooping = false
                return
            }

            dispatchSyntheticBleResults()
            mainHandler.postDelayed(this, 1000L) // 1 Hz BLE advertise interval
        }
    }

    fun initHooks(loader: ClassLoader = classLoader) {
        runCatching {
            val scannerClass = Class.forName("android.bluetooth.le.BluetoothLeScanner", false, loader)
            val adapterClass = BluetoothAdapter::class.java

            hookStartScan(scannerClass)
            hookStopScan(scannerClass)
            hookLegacyLeScan(adapterClass)
            module.log(Log.INFO, tag, "BleScannerHooks installed successfully")
        }.onFailure {
            module.log(Log.INFO, tag, "BleScannerHooks initialization failed: ${it.message}")
        }
    }

    private fun hookStartScan(scannerClass: Class<*>) {
        scannerClass.declaredMethods
            .filter { it.name == "startScan" && it.parameterTypes.any { p -> p == ScanCallback::class.java } }
            .forEach { method ->
                hook(method) { chain ->
                    val cb = chain.args.firstOrNull { ScanCallback::class.java.isInstance(it) } as? ScanCallback
                    if (cb != null) {
                        activeScanCallbacks.add(cb)
                        startLoopIfNeeded()
                    }
                    chain.proceed()
                }
            }

        // flushPendingScanResults
        scannerClass.declaredMethods
            .filter { it.name == "flushPendingScanResults" && it.parameterTypes.any { p -> p == ScanCallback::class.java } }
            .forEach { method ->
                hook(method) { chain ->
                    val cb = chain.args.firstOrNull { ScanCallback::class.java.isInstance(it) } as? ScanCallback
                    if (cb != null) {
                        dispatchToCallback(cb)
                    }
                    chain.proceed()
                }
            }
    }

    private fun hookStopScan(scannerClass: Class<*>) {
        scannerClass.declaredMethods
            .filter { it.name == "stopScan" && it.parameterTypes.any { p -> p == ScanCallback::class.java } }
            .forEach { method ->
                hook(method) { chain ->
                    val cb = chain.args.firstOrNull { ScanCallback::class.java.isInstance(it) } as? ScanCallback
                    if (cb != null) {
                        activeScanCallbacks.remove(cb)
                    }
                    chain.proceed()
                }
            }
    }

    private fun hookLegacyLeScan(adapterClass: Class<*>) {
        adapterClass.declaredMethods
            .filter { it.name == "startLeScan" && it.parameterTypes.any { p -> p == BluetoothAdapter.LeScanCallback::class.java } }
            .forEach { method ->
                hook(method) { chain ->
                    val cb = chain.args.firstOrNull { BluetoothAdapter.LeScanCallback::class.java.isInstance(it) } as? BluetoothAdapter.LeScanCallback
                    if (cb != null && PreferencesUtil.getIsPlaying()) {
                        val device = getRemoteDevice("FA:CE:88:99:00:11", "Virtual Beacon")
                        val rssi = -58 + random.nextInt(9) - 4
                        val recordBytes = byteArrayOf(0x02, 0x01, 0x06)
                        mainHandler.post {
                            runCatching { cb.onLeScan(device, rssi, recordBytes) }
                        }
                    }
                    chain.proceed()
                }
            }
    }

    private fun startLoopIfNeeded() {
        if (!isLooping && PreferencesUtil.getIsPlaying()) {
            isLooping = true
            mainHandler.post(periodicBleDispatchRunnable)
        }
    }

    private fun dispatchSyntheticBleResults() {
        if (activeScanCallbacks.isEmpty()) return
        for (cb in activeScanCallbacks) {
            dispatchToCallback(cb)
        }
    }

    private fun dispatchToCallback(cb: ScanCallback) {
        val result = createSyntheticScanResult(
            address = "FA:CE:88:99:00:11",
            displayName = "Virtual Punch Beacon",
            baseRssi = -58
        ) ?: return

        mainHandler.post {
            runCatching {
                cb.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }
    }

    private fun createSyntheticScanResult(address: String, displayName: String, baseRssi: Int): ScanResult? {
        return runCatching {
            val device = getRemoteDevice(address, displayName) ?: return null
            val jitteredRssi = baseRssi + random.nextInt(9) - 4
            val nowNanos = SystemClock.elapsedRealtimeNanos()

            // Construct ScanRecord: parseFromBytes or via reflection
            val scanRecord = runCatching {
                val rawBytes = byteArrayOf(
                    0x02, 0x01, 0x06, // Flags: LE General Discoverable Mode, BR/EDR Not Supported
                    0x07, 0x09, 'B'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'c'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte()
                )
                val parseMethod = ScanRecord::class.java.getMethod("parseFromBytes", ByteArray::class.java)
                parseMethod.invoke(null, rawBytes) as? ScanRecord
            }.getOrNull()

            // ScanResult(BluetoothDevice device, ScanRecord scanRecord, int rssi, long timestampNanos)
            val constructor: Constructor<ScanResult> = ScanResult::class.java.getDeclaredConstructor(
                BluetoothDevice::class.java,
                ScanRecord::class.java,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            ).apply { isAccessible = true }

            constructor.newInstance(device, scanRecord, jitteredRssi, nowNanos)
        }.getOrNull()
    }

    private fun getRemoteDevice(address: String, displayName: String): BluetoothDevice? {
        return runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            val device = adapter.getRemoteDevice(address)

            // Hook device.getName() to return displayName
            hookDeviceName(device, displayName)
            device
        }.getOrNull()
    }

    private fun hookDeviceName(device: BluetoothDevice, name: String) {
        runCatching {
            val getNameMethod = device.javaClass.getMethod("getName")
            hook(getNameMethod) { chain ->
                if (chain.thisObject === device || (chain.thisObject as? BluetoothDevice)?.address == device.address) {
                    name
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private fun hook(method: Method, interceptor: Hooker) {
        synchronized(installedMethods) {
            if (method in installedMethods) return
            runCatching {
                module.hook(method).intercept(interceptor)
                installedMethods.add(method)
            }
        }
    }
}
