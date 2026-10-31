package io.github.ceigt.geolocation.manager.ui.environment

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wifi
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
import io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile
import io.github.ceigt.geolocation.data.model.environment.EnvironmentSimulationTab
import io.github.ceigt.geolocation.data.model.environment.VirtualCellTower
import io.github.ceigt.geolocation.data.model.environment.VirtualWifiAp
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

    val currentProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull() ?: EnvironmentProfile(
        name = "默认真实环境模板",
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
                title = { Text("Wi-Fi 与基站环境模拟", fontWeight = FontWeight.SemiBold) },
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
                    text = { Text("蓝牙 BLE") },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == EnvironmentSimulationTab.COLLECTION,
                    onClick = { selectedTab = EnvironmentSimulationTab.COLLECTION },
                    text = { Text("现场采样克隆") },
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
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("一键全要素采样 (Wi-Fi + 蜂窝基站 + 蓝牙)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("将当前身处的物理无线电与基站特征固化为仿真 Profile，安全用于离线回放", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (!fineLoc) {
                                    Toast.makeText(context, "请先授予精准定位权限以采集现实无线电环境", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val wifiMgr = runCatching { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()
                                val telephonyMgr = runCatching { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }.getOrNull()

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

                                val newProfile = EnvironmentProfile(
                                    name = "现场采集-${System.currentTimeMillis() % 10000}",
                                    wifiAps = capturedAps,
                                    cellTowers = capturedTowers
                                )

                                scope.launch {
                                    val updated = profiles.toMutableList().apply { add(newProfile) }
                                    repository.saveEnvironmentProfiles(updated)
                                    repository.saveActiveEnvironmentProfileId(newProfile.id)
                                }

                                Toast.makeText(
                                    context,
                                    "已成功采集并持久化 ${capturedAps.size} 个热点与 ${capturedTowers.size} 座基站！",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        ) {
                            Text("开始全要素采集并保存为 Profile")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BleEmbeddedPanel(navController: NavController) {
    io.github.ceigt.geolocation.manager.ui.ble.BlePanelContent(navController = navController)
}
