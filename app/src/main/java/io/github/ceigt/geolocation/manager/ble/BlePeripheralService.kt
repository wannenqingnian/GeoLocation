package io.github.ceigt.geolocation.manager.ble

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.domain.ble.BleDeviceProfile
import io.github.ceigt.geolocation.domain.ble.BleProfile
import java.util.UUID

class BlePeripheralService : Service() {

    companion object {
        private const val TAG = "BlePeripheralService"
        private const val CHANNEL_ID = "ble_peripheral_channel"
        private const val NOTIFICATION_ID = 4001

        fun start(context: Context) {
            val intent = Intent(context, BlePeripheralService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlePeripheralService::class.java))
        }
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private lateinit var repository: PreferencesRepository
    private var activeDevice: BleDeviceProfile? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE advertising started successfully for: ${activeDevice?.displayName}")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val uuidStr = characteristic?.uuid?.toString() ?: ""
            var responseData = byteArrayOf(0x00)

            // Look up configured characteristic response
            val configuredChar = activeDevice?.gattServices
                ?.flatMap { it.characteristics }
                ?.firstOrNull { it.uuid.equals(uuidStr, ignoreCase = true) }

            if (configuredChar != null && configuredChar.fixedReadValueHex.isNotBlank()) {
                val hex = configuredChar.fixedReadValueHex.replace(" ", "").replace("0x", "")
                responseData = runCatching {
                    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }.getOrDefault(byteArrayOf(0x00))
            }

            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = PreferencesRepository(this)
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("蓝牙 BLE 物理外设广播运行中")
            .setSmallIcon(R.drawable.ic_stat_location)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        loadActiveProfileAndAdvertise()
    }

    @SuppressLint("MissingPermission")
    private fun loadActiveProfileAndAdvertise() {
        val adv = advertiser ?: run {
            Log.e(TAG, "BluetoothLeAdvertiser not supported on this device")
            return
        }

        val profiles = repository.getBleProfiles()
        val activeId = repository.sharedPrefs()?.getString(io.github.ceigt.geolocation.data.KEY_ACTIVE_BLE_PROFILE_ID, null)
        val selectedProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

        activeDevice = selectedProfile?.devices?.firstOrNull()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(activeDevice?.advertising?.intervalMode?.mode ?: AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(activeDevice?.signal?.txPower?.level ?: AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(activeDevice?.advertising?.connectable ?: true)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(activeDevice?.advertising?.includeDeviceName ?: true)
            .setIncludeTxPowerLevel(activeDevice?.advertising?.includeTxPower ?: true)

        val serviceUuids = activeDevice?.advertising?.serviceUuids.orEmpty()
        if (serviceUuids.isNotEmpty()) {
            serviceUuids.forEach { uuid ->
                runCatching { dataBuilder.addServiceUuid(ParcelUuid(UUID.fromString(uuid))) }
            }
        } else {
            dataBuilder.addServiceUuid(ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")))
        }

        // Add manufacturer data
        activeDevice?.advertising?.manufacturerData?.forEach { mData ->
            val payload = runCatching {
                mData.payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }.getOrNull()
            if (payload != null) {
                dataBuilder.addManufacturerData(mData.manufacturerId, payload)
            }
        }

        val data = dataBuilder.build()
        adv.startAdvertising(settings, data, advertiseCallback)

        // Setup local GATT Server if configured
        if (activeDevice?.advertising?.connectable == true) {
            setupGattServer()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        runCatching {
            val server = bluetoothManager?.openGattServer(this, gattServerCallback) ?: return
            gattServer = server

            val services = activeDevice?.gattServices.orEmpty()
            if (services.isNotEmpty()) {
                for (sProfile in services) {
                    val sUuid = runCatching { UUID.fromString(sProfile.uuid) }.getOrNull() ?: continue
                    val gattService = BluetoothGattService(sUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                    for (cProfile in sProfile.characteristics) {
                        val cUuid = runCatching { UUID.fromString(cProfile.uuid) }.getOrNull() ?: continue
                        val gattChar = BluetoothGattCharacteristic(
                            cUuid,
                            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                            BluetoothGattCharacteristic.PERMISSION_READ
                        )
                        gattService.addCharacteristic(gattChar)
                    }
                    server.addService(gattService)
                }
            } else {
                // Default beacon service
                val serviceUuid = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
                val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                val characteristic = BluetoothGattCharacteristic(
                    UUID.fromString("0000FEA1-0000-1000-8000-00805F9B34FB"),
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                service.addCharacteristic(characteristic)
                server.addService(service)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE 外设服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
