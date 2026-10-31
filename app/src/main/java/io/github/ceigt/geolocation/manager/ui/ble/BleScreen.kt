package io.github.ceigt.geolocation.manager.ui.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import io.github.ceigt.geolocation.domain.ble.BleDeliveryMode
import io.github.ceigt.geolocation.domain.ble.BleDeviceProfile
import io.github.ceigt.geolocation.manager.ble.BlePeripheralService
import kotlinx.coroutines.launch

private fun bleScanPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}.toTypedArray()

private fun bleAdvertisePermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

enum class BleUiTab {
    MAIN,
    DISCOVERY
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("蓝牙 BLE 信标与外设模拟", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            BlePanelContent(navController)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BlePanelContent(navController: NavController) {
    var selectedTab by remember { mutableStateOf(BleUiTab.MAIN) }
    var deliveryMode by remember { mutableStateOf(BleDeliveryMode.HOOK_VIRTUAL) }
    var isBroadcasting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val discoveredDevices = remember { mutableStateListOf<ScanResult>() }
    var isScanning by remember { mutableStateOf(false) }
    var activeScanCallback by remember { mutableStateOf<ScanCallback?>(null) }

    fun stopScanning() {
        if (isScanning && activeScanCallback != null) {
            runCatching {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                adapter?.bluetoothLeScanner?.stopScan(activeScanCallback)
            }
            isScanning = false
            activeScanCallback = null
        }
    }

    val startScanning: () -> Unit = {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            Toast.makeText(context, "蓝牙未开启或不支持 BLE，请先开启系统蓝牙", Toast.LENGTH_SHORT).show()
        } else {
            discoveredDevices.clear()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let {
                        if (discoveredDevices.none { d -> d.device.address == it.device.address }) {
                            discoveredDevices.add(it)
                        }
                    }
                }
            }
            activeScanCallback = callback
            runCatching {
                scanner.startScan(callback)
                isScanning = true
                Toast.makeText(context, "开始扫描周边真实 BLE 信标...", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                isScanning = false
                activeScanCallback = null
                Toast.makeText(context, "启动扫描失败: ${err.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val startBroadcasting: () -> Unit = {
        BlePeripheralService.start(context)
        isBroadcasting = true
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (hasScan) {
            startScanning()
        } else {
            Toast.makeText(context, "未授予蓝牙扫描权限，无法扫描周边信标", Toast.LENGTH_SHORT).show()
        }
    }

    val advertisePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val hasAdv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else true
        if (hasAdv) {
            startBroadcasting()
        } else {
            Toast.makeText(context, "未授予蓝牙广播权限，无法启动物理广播", Toast.LENGTH_SHORT).show()
        }
    }

    // Ensure scanner is cleanly stopped when leaving screen or toggling
    DisposableEffect(Unit) {
        onDispose {
            stopScanning()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == BleUiTab.MAIN,
                onClick = { selectedTab = BleUiTab.MAIN },
                text = { Text("设备配置与广播") },
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == BleUiTab.DISCOVERY,
                onClick = { selectedTab = BleUiTab.DISCOVERY },
                text = { Text("周边真实扫描克隆") },
                icon = { Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null) }
            )
        }

        when (selectedTab) {
                BleUiTab.MAIN -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("模拟工作模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = deliveryMode == BleDeliveryMode.HOOK_VIRTUAL,
                                        onClick = { deliveryMode = BleDeliveryMode.HOOK_VIRTUAL }
                                    )
                                    Text("Xposed 虚拟注入 (HOOK_VIRTUAL)", modifier = Modifier.padding(start = 4.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = deliveryMode == BleDeliveryMode.PHYSICAL_PERIPHERAL,
                                        onClick = { deliveryMode = BleDeliveryMode.PHYSICAL_PERIPHERAL }
                                    )
                                    Text("真实硬件物理广播 (PHYSICAL_PERIPHERAL)", modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }

                        if (deliveryMode == BleDeliveryMode.PHYSICAL_PERIPHERAL) {
                            Button(
                                onClick = {
                                    if (isBroadcasting) {
                                        BlePeripheralService.stop(context)
                                        isBroadcasting = false
                                    } else {
                                        val missing = bleAdvertisePermissions().filter {
                                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                        }
                                        if (missing.isNotEmpty()) {
                                            advertisePermissionLauncher.launch(missing.toTypedArray())
                                        } else {
                                            startBroadcasting()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBroadcasting) MaterialTheme.colorScheme.error else Color(0xFF006C5F)
                                )
                            ) {
                                Text(if (isBroadcasting) "停止物理 BLE 广播" else "启动物理 BLE 广播")
                            }
                        }
                    }
                }
                BleUiTab.DISCOVERY -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Button(
                            onClick = {
                                if (isScanning) {
                                    stopScanning()
                                    Toast.makeText(context, "已停止扫描", Toast.LENGTH_SHORT).show()
                                } else {
                                    val missing = bleScanPermissions().filter {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }
                                    if (missing.isNotEmpty()) {
                                        scanPermissionLauncher.launch(missing.toTypedArray())
                                    } else {
                                        startScanning()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) MaterialTheme.colorScheme.error else Color(0xFF006C5F)
                            )
                        ) {
                            Text(if (isScanning) "停止扫描" else "扫描周边真实信标")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(discoveredDevices) { res ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(res.device.name ?: "Unknown Beacon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("MAC: ${res.device.address}  |  RSSI: ${res.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = {
                                            val repo = io.github.ceigt.geolocation.data.repository.PreferencesRepository(context)
                                            val currentProfiles = repo.getBleProfiles().toMutableList()
                                            val newDev = io.github.ceigt.geolocation.domain.ble.BleDeviceProfile(
                                                displayName = res.device.name ?: "Cloned Beacon",
                                                virtualAddress = res.device.address,
                                                signal = io.github.ceigt.geolocation.domain.ble.BleSignalProfile(baseRssi = res.rssi)
                                            )
                                            val profile = io.github.ceigt.geolocation.domain.ble.BleProfile(
                                                name = "克隆-${res.device.name ?: res.device.address}",
                                                devices = listOf(newDev)
                                            )
                                            currentProfiles.add(profile)
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                repo.saveBleProfiles(currentProfiles)
                                                repo.saveActiveBleProfileId(profile.id)
                                            }
                                            Toast.makeText(context, "已成功保存并激活 ${res.device.address} 模拟模板", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("保存为模拟模板")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
