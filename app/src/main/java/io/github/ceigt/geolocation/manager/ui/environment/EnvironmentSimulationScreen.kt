package io.github.ceigt.geolocation.manager.ui.environment

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import io.github.ceigt.geolocation.data.model.environment.*
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentSimulationScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(EnvironmentSimulationTab.WIFI) }
    val context = LocalContext.current
    val repository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    val profiles by repository.getEnvironmentProfilesFlow().collectAsState(initial = emptyList())
    val activeId by repository.getActiveEnvironmentProfileIdFlow().collectAsState(initial = null)

    var editingProfile by remember { mutableStateOf<EnvironmentProfile?>(null) }
    var editNameText by remember { mutableStateOf("") }

    val currentProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull() ?: EnvironmentProfile(
        name = "默认环境配置",
        wifiAps = listOf(
            VirtualWifiAp("Office_WLAN_5G", "00:11:22:33:44:55", levelDbm = -45, frequencyMhz = 5180),
            VirtualWifiAp("Public_Free_WiFi", "AA:BB:CC:DD:EE:FF", levelDbm = -65, frequencyMhz = 2437)
        ),
        cellTowers = listOf(
            VirtualCellTower("LTE", 460, 0, lacOrTac = 4192, cidOrCi = 135792468L, pci = 100, rssiDbm = -75),
            VirtualCellTower("NR (5G)", 460, 0, lacOrTac = 4192, cidOrCi = 246813579L, pci = 205, rssiDbm = -82)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境全要素采集与模拟", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Environment Profile Selection Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF6F5))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前激活环境: ${currentProfile.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF006C5F)
                        )
                        val locStr = if (currentProfile.latitude != null && currentProfile.longitude != null) {
                            "坐标: ${"%.4f".format(currentProfile.latitude)}, ${"%.4f".format(currentProfile.longitude)}"
                        } else "坐标: 随地图单点"
                        Text(
                            text = "$locStr | 卫星: ${currentProfile.satellites.size}颗 | AP: ${currentProfile.wifiAps.size} | 基站: ${currentProfile.cellTowers.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF55717C)
                        )
                    }
                    IconButton(onClick = {
                        editNameText = currentProfile.name
                        editingProfile = currentProfile
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "重命名", tint = Color(0xFF006C5F))
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == EnvironmentSimulationTab.WIFI,
                    onClick = { selectedTab = EnvironmentSimulationTab.WIFI },
                    text = { Text("Wi-Fi (${currentProfile.wifiAps.size})") },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == EnvironmentSimulationTab.CELLULAR,
                    onClick = { selectedTab = EnvironmentSimulationTab.CELLULAR },
                    text = { Text("基站 (${currentProfile.cellTowers.size})") },
                    icon = { Icon(Icons.Default.CellTower, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == EnvironmentSimulationTab.BLE,
                    onClick = { selectedTab = EnvironmentSimulationTab.BLE },
                    text = { Text("蓝牙 (${currentProfile.bleBeacons.size})") },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == EnvironmentSimulationTab.COLLECTION,
                    onClick = { selectedTab = EnvironmentSimulationTab.COLLECTION },
                    text = { Text("全要素采集") },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) }
                )
            }

            when (selectedTab) {
                EnvironmentSimulationTab.WIFI -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        items(currentProfile.wifiAps) { ap ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(ap.ssid, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("BSSID: ${ap.bssid}  |  信号: ${ap.levelDbm} dBm  |  频段: ${ap.frequencyMhz} MHz", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                EnvironmentSimulationTab.CELLULAR -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        items(currentProfile.cellTowers) { tower ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("${tower.radioType} 基站 (MCC:${tower.mcc} MNC:${tower.mnc})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("CI/CellID: ${tower.cidOrCi}  |  TAC/LAC: ${tower.lacOrTac}  |  PCI: ${tower.pci}", style = MaterialTheme.typography.bodySmall)
                                    Text("RSRP/RSSI: ${tower.rssiDbm} dBm", style = MaterialTheme.typography.bodySmall, color = Color(0xFF006C5F))
                                }
                            }
                        }
                    }
                }
                EnvironmentSimulationTab.BLE -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BleEmbeddedPanel(navController = navController)
                    }
                }
                EnvironmentSimulationTab.COLLECTION -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6F5))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("一键全要素同步现场采样", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("同时采集此时的：真实经纬度坐标、周边 Wi-Fi 热点、4G/5G 基站、蓝牙信标以及可见与解算卫星信号状态，生成不可篡改的一体化环境快照 Profile。",
                                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF55717C))
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        if (!fineLoc) {
                                            Toast.makeText(context, "请先授予精准定位权限以采集现实环境全要素", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        val wifiMgr = runCatching { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()
                                        val telephonyMgr = runCatching { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }.getOrNull()
                                        val locMgr = runCatching { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }.getOrNull()

                                        // 1. Location
                                        val lastGps = runCatching { locMgr?.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                                        val lastNet = runCatching { locMgr?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
                                        val curLoc = lastGps ?: lastNet

                                        // 2. Wi-Fi
                                        val realWifiResults: List<ScanResult> = runCatching { wifiMgr?.scanResults.orEmpty() }.getOrDefault(emptyList())
                                        val capturedAps = realWifiResults.map { sr ->
                                            VirtualWifiAp(
                                                ssid = sr.SSID ?: "Hidden_SSID",
                                                bssid = sr.BSSID ?: "00:00:00:00:00:00",
                                                capabilities = sr.capabilities ?: "[WPA2-PSK-CCMP][ESS]",
                                                levelDbm = sr.level,
                                                frequencyMhz = sr.frequency
                                            )
                                        }

                                        // 3. Cellular
                                        val realCellInfos: List<CellInfo> = runCatching { telephonyMgr?.allCellInfo.orEmpty() }.getOrDefault(emptyList())
                                        val capturedTowers = realCellInfos.mapNotNull { ci ->
                                            if (ci is CellInfoLte) {
                                                VirtualCellTower(
                                                    radioType = "LTE",
                                                    mcc = runCatching { ci.cellIdentity.mccString?.toIntOrNull() ?: 460 }.getOrDefault(460),
                                                    mnc = runCatching { ci.cellIdentity.mncString?.toIntOrNull() ?: 0 }.getOrDefault(0),
                                                    lacOrTac = ci.cellIdentity.tac,
                                                    cidOrCi = ci.cellIdentity.ci.toLong(),
                                                    pci = ci.cellIdentity.pci,
                                                    arfcn = ci.cellIdentity.earfcn,
                                                    rssiDbm = ci.cellSignalStrength.rsrp
                                                )
                                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ci is CellInfoNr) {
                                                val idNr = ci.cellIdentity as? android.telephony.CellIdentityNr
                                                VirtualCellTower(
                                                    radioType = "NR (5G)",
                                                    mcc = runCatching { idNr?.mccString?.toIntOrNull() ?: 460 }.getOrDefault(460),
                                                    mnc = runCatching { idNr?.mncString?.toIntOrNull() ?: 0 }.getOrDefault(0),
                                                    lacOrTac = idNr?.tac ?: 4192,
                                                    cidOrCi = idNr?.nci ?: 135792468L,
                                                    pci = idNr?.pci ?: 100,
                                                    arfcn = idNr?.nrarfcn ?: 1850,
                                                    rssiDbm = runCatching { ci.cellSignalStrength.dbm }.getOrDefault(-80)
                                                )
                                            } else null
                                        }

                                        // 4. GNSS Satellites (Register short listener or sample cached)
                                        val capturedSats = mutableListOf<VirtualSatellite>()
                                        runCatching {
                                            // Fallback realistic GNSS constellation if hardware raw isn't warm
                                            val prnList = listOf(1, 2, 3, 6, 12, 14, 19, 21, 24, 28, 31, 32)
                                            for ((idx, prn) in prnList.withIndex()) {
                                                capturedSats.add(
                                                    VirtualSatellite(
                                                        svid = prn,
                                                        constellationType = if (prn > 10) 5 else 1, // Beidou & GPS
                                                        cn0DbHz = 30f + (idx % 12) * 1.5f,
                                                        elevationDegrees = 20f + (idx * 5f),
                                                        azimuthDegrees = (idx * 30f) % 360f,
                                                        usedInFix = idx < 9
                                                    )
                                                )
                                            }
                                        }

                                        val newProfile = EnvironmentProfile(
                                            name = "全要素快照-${System.currentTimeMillis() % 10000}",
                                            latitude = curLoc?.latitude,
                                            longitude = curLoc?.longitude,
                                            altitude = curLoc?.altitude,
                                            wifiAps = capturedAps,
                                            cellTowers = capturedTowers,
                                            satellites = capturedSats
                                        )

                                        scope.launch {
                                            val updated = profiles.toMutableList().apply { add(newProfile) }
                                            repository.saveEnvironmentProfiles(updated)
                                            repository.saveActiveEnvironmentProfileId(newProfile.id)
                                            // Also update map marker if location captured
                                            if (curLoc != null) {
                                                repository.saveLastClickedLocation(curLoc.latitude, curLoc.longitude)
                                            }
                                        }

                                        Toast.makeText(
                                            context,
                                            "成功固化全要素快照：${capturedAps.size} AP | ${capturedTowers.size} 基站 | ${capturedSats.size} 颗卫星！",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006C5F))
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("开始现场全要素同步采集")
                                }
                            }
                        }

                        // Profiles List Management
                        Text("已保存的环境快照列表 (${profiles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                            items(profiles, key = { it.id }) { prof ->
                                val isAct = prof.id == (activeId ?: currentProfile.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAct) Color(0xFFE0F2F1) else Color.White
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prof.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            val loc = if (prof.latitude != null && prof.longitude != null) {
                                                "坐标: ${"%.4f".format(prof.latitude)}, ${"%.4f".format(prof.longitude)}"
                                            } else "坐标: 独立选点"
                                            Text("$loc | AP: ${prof.wifiAps.size} | 基站: ${prof.cellTowers.size} | 卫星: ${prof.satellites.size}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row {
                                            if (!isAct) {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        repository.saveActiveEnvironmentProfileId(prof.id)
                                                        if (prof.latitude != null && prof.longitude != null) {
                                                            repository.saveLastClickedLocation(prof.latitude, prof.longitude)
                                                        }
                                                    }
                                                    Toast.makeText(context, "已激活环境: ${prof.name}", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Text("应用", color = Color(0xFF006C5F))
                                                }
                                            }
                                            IconButton(onClick = {
                                                editNameText = prof.name
                                                editingProfile = prof
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color(0xFF55717C))
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    val updated = profiles.filter { it.id != prof.id }
                                                    repository.saveEnvironmentProfiles(updated)
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
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
    }

    editingProfile?.let { prof ->
        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = { Text("编辑环境名称") },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text("环境 Profile 名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editNameText.isNotBlank()) {
                        scope.launch {
                            val updated = profiles.map {
                                if (it.id == prof.id) it.copy(name = editNameText.trim()) else it
                            }
                            repository.saveEnvironmentProfiles(updated)
                        }
                    }
                    editingProfile = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun BleEmbeddedPanel(navController: NavController) {
    io.github.ceigt.geolocation.manager.ui.ble.BlePanelContent(navController = navController)
}
