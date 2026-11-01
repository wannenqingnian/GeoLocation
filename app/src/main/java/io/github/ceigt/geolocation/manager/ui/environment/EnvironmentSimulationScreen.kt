package io.github.ceigt.geolocation.manager.ui.environment

import android.Manifest
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import io.github.ceigt.geolocation.data.model.environment.*
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.domain.environment.EnvironmentCollector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val AccentGreen = Color(0xFF006C5F)
private val SecondaryText = Color(0xFF526870)
private val PageBackground = Color(0xFFF3F7F8)
private val CardBorder = Color(0xFFDCE7E9)

private fun collectionPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}.toTypedArray()

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
    var isCollecting by remember { mutableStateOf(false) }
    var lastWarnings by remember { mutableStateOf(emptyList<String>()) }

    val emptyProfile = remember { EnvironmentProfile(name = "未采集环境") }
    val currentProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull() ?: emptyProfile

    val runCollection: suspend () -> Unit = {
        isCollecting = true
        lastWarnings = emptyList()
        try {
            val collection = EnvironmentCollector(context.applicationContext).collect()
            val snapshot = collection.profile
            val existing = repository.getEnvironmentProfiles()
            repository.saveEnvironmentProfiles(existing + snapshot)
            repository.saveActiveEnvironmentProfileId(snapshot.id)
            if (snapshot.latitude != null && snapshot.longitude != null) {
                repository.saveLastClickedLocation(snapshot.latitude, snapshot.longitude)
            }
            lastWarnings = collection.warnings
            Toast.makeText(
                context,
                "已保存环境快照：${snapshot.wifiAps.size} AP | ${snapshot.bleBeacons.size} BLE | " +
                    "${snapshot.cellTowers.size} 基站 | ${snapshot.satellites.size} 卫星",
                Toast.LENGTH_LONG
            ).show()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Toast.makeText(context, "采集失败：${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        } finally {
            isCollecting = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            scope.launch { runCollection() }
        } else {
            Toast.makeText(context, "未授予精准定位权限，无法采集现场环境", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("环境采集与模拟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("现场数据 · 环境快照", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground),
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF164E48))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("当前环境快照", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB7DCD3))
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (activeId != null) Color(0xFF2E7D32) else Color(0xFF55717C).copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = if (activeId != null) "已生效中" else "未激活生效",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(currentProfile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                            val locStr = if (currentProfile.latitude != null && currentProfile.longitude != null) {
                                "坐标: ${"%.4f".format(currentProfile.latitude)}, ${"%.4f".format(currentProfile.longitude)}"
                            } else "坐标: 随地图单点"
                            Text(locStr, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD2E8E2))
                        }
                        if (activeId != null) {
                            Button(
                                onClick = {
                                    scope.launch { repository.saveActiveEnvironmentProfileId(null) }
                                    Toast.makeText(context, "已取消环境快照生效，恢复默认独立定位与环境", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).padding(end = 4.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("取消生效", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = {
                            editNameText = currentProfile.name
                            editingProfile = currentProfile
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "重命名", tint = Color.White)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnvironmentMetric("Wi-Fi", currentProfile.wifiAps.size, Modifier.weight(1f))
                        EnvironmentMetric("基站", currentProfile.cellTowers.size, Modifier.weight(1f))
                        EnvironmentMetric("卫星", currentProfile.satellites.size, Modifier.weight(1f))
                        EnvironmentMetric("蓝牙", currentProfile.bleBeacons.size, Modifier.weight(1f))
                    }
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)),
                containerColor = Color.White,
                contentColor = AccentGreen,
                divider = {}
            ) {
                Tab(
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = SecondaryText,
                    selected = selectedTab == EnvironmentSimulationTab.WIFI,
                    onClick = { selectedTab = EnvironmentSimulationTab.WIFI },
                    text = { Text("Wi-Fi") },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
                )
                Tab(
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = SecondaryText,
                    selected = selectedTab == EnvironmentSimulationTab.CELLULAR,
                    onClick = { selectedTab = EnvironmentSimulationTab.CELLULAR },
                    text = { Text("基站") },
                    icon = { Icon(Icons.Default.CellTower, contentDescription = null) }
                )
                Tab(
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = SecondaryText,
                    selected = selectedTab == EnvironmentSimulationTab.SATELLITES,
                    onClick = { selectedTab = EnvironmentSimulationTab.SATELLITES },
                    text = { Text("卫星") },
                    icon = { Icon(Icons.Default.Satellite, contentDescription = null) }
                )
                Tab(
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = SecondaryText,
                    selected = selectedTab == EnvironmentSimulationTab.BLE,
                    onClick = { selectedTab = EnvironmentSimulationTab.BLE },
                    text = { Text("蓝牙") },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                )
                Tab(
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = SecondaryText,
                    selected = selectedTab == EnvironmentSimulationTab.COLLECTION,
                    onClick = { selectedTab = EnvironmentSimulationTab.COLLECTION },
                    text = { Text("采集") },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) }
                )
            }

            when (selectedTab) {
                EnvironmentSimulationTab.WIFI -> {
                    if (currentProfile.wifiAps.isEmpty()) {
                        EmptyEnvironmentHint("暂无 Wi-Fi 热点，请在“采集”页执行现场采集")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(currentProfile.wifiAps) { ap ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(ap.ssid, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "BSSID: ${ap.bssid}  |  信号: ${ap.levelDbm} dBm  |  频段: ${ap.frequencyMhz} MHz",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                EnvironmentSimulationTab.CELLULAR -> {
                    if (currentProfile.cellTowers.isEmpty()) {
                        EmptyEnvironmentHint("暂无基站数据，请在“采集”页执行现场采集")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(currentProfile.cellTowers) { tower ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "${tower.radioType} 基站 (MCC:${tower.mcc} MNC:${tower.mnc})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "CI/CellID: ${tower.cidOrCi}  |  TAC/LAC: ${tower.lacOrTac}  |  PCI/PSC: ${tower.pci}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "ARFCN: ${tower.arfcn}  |  信号: ${tower.rssiDbm} dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AccentGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                EnvironmentSimulationTab.SATELLITES -> {
                    if (currentProfile.satellites.isEmpty()) {
                        EmptyEnvironmentHint("暂无卫星状态，请在室外或靠近窗户处执行现场采集")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(currentProfile.satellites) { satellite ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "${constellationLabel(satellite.constellationType)} · SV ${satellite.svid}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "CN0: ${"%.1f".format(satellite.cn0DbHz)} dB-Hz  |  仰角: ${satellite.elevationDegrees.toInt()}°" +
                                                "  |  方位: ${satellite.azimuthDegrees.toInt()}°",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            if (satellite.usedInFix) "参与定位解算" else "可见但未参与定位",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (satellite.usedInFix) AccentGreen else SecondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                EnvironmentSimulationTab.BLE -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (currentProfile.bleBeacons.isNotEmpty()) {
                            Text(
                                "采集到的 BLE 信标 (${currentProfile.bleBeacons.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .padding(horizontal = 16.dp)
                            ) {
                                items(currentProfile.bleBeacons, key = { it.id }) { beacon ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                beacon.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "MAC: ${beacon.virtualAddress}  |  RSSI: ${beacon.signal.baseRssi} dBm",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            BleEmbeddedPanel(navController = navController)
                        }
                    }
                }

                EnvironmentSimulationTab.COLLECTION -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "现场环境采集",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val missingPermissions = collectionPermissions().filter {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }
                                    if (missingPermissions.isNotEmpty()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFFF7EB)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFB25E00), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "检测到部分权限未授予，点击下方按钮将自动请求系统授权并直接采集",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF804300)
                                                )
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val missing = collectionPermissions().filter {
                                                ContextCompat.checkSelfPermission(context, it) !=
                                                    PackageManager.PERMISSION_GRANTED
                                            }
                                            if (missing.isNotEmpty()) {
                                                permissionLauncher.launch(missing.toTypedArray())
                                            } else {
                                                scope.launch { runCollection() }
                                            }
                                        },
                                        enabled = !isCollecting,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isCollecting) "正在采集…" else "开始现场全要素同步采集")
                                    }
                                    if (isCollecting) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "同时采集真实经纬度、周边 Wi-Fi 热点、GSM/WCDMA/CDMA/LTE/5G 基站、" +
                                            "BLE 信标以及 GNSS 卫星状态，生成可复查的环境快照。缺少权限、无线关闭或无信号时，" +
                                            "对应项目会留空并给出提示，不会填充伪造样本。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (lastWarnings.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "采集提示",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SecondaryText
                                        )
                                        lastWarnings.forEach { warning ->
                                            Text(
                                                "• $warning",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9A5B00)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "已保存的环境快照 (${profiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (profiles.isEmpty()) {
                            item {
                                Text(
                                    "尚无环境快照，点击上方按钮开始采集。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                            }
                        } else {
                            items(profiles, key = { it.id }) { prof ->
                                val isAct = prof.id == (activeId ?: currentProfile.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAct) Color(0xFFE4F3ED) else Color.White
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                prof.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val loc = if (prof.latitude != null && prof.longitude != null) {
                                                "坐标: ${"%.4f".format(prof.latitude)}, ${"%.4f".format(prof.longitude)}"
                                            } else "坐标: 独立选点"
                                            Text(
                                                "$loc | AP: ${prof.wifiAps.size} | BLE: ${prof.bleBeacons.size} | " +
                                                    "基站: ${prof.cellTowers.size} | 卫星: ${prof.satellites.size}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row {
                                            if (isAct) {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        repository.saveActiveEnvironmentProfileId(null)
                                                    }
                                                    Toast.makeText(context, "已取消生效: ${prof.name}", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Text("取消生效", color = Color(0xFFD32F2F))
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        repository.saveActiveEnvironmentProfileId(prof.id)
                                                        if (prof.latitude != null && prof.longitude != null) {
                                                            repository.saveLastClickedLocation(prof.latitude, prof.longitude)
                                                        }
                                                    }
                                                    Toast.makeText(context, "已激活环境: ${prof.name}", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Text("应用", color = AccentGreen)
                                                }
                                            }
                                            IconButton(onClick = {
                                                editNameText = prof.name
                                                editingProfile = prof
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = SecondaryText)
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    val updated = profiles.filter { it.id != prof.id }
                                                    repository.saveEnvironmentProfiles(updated)
                                                    if (activeId == prof.id) {
                                                        repository.saveActiveEnvironmentProfileId(updated.lastOrNull()?.id)
                                                    }
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
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
                            val updated = repository.getEnvironmentProfiles().map {
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
private fun EnvironmentMetric(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Color(0xFF2A5F59)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFD2E8E2))
        }
    }
}

@Composable
private fun EmptyEnvironmentHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.dp, CardBorder)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(32.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
        }
    }
}

private fun constellationLabel(type: Int): String = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
    GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
    GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
    else -> "未知($type)"
}

@Composable
fun BleEmbeddedPanel(navController: NavController) {
    io.github.ceigt.geolocation.manager.ui.ble.BlePanelContent(navController = navController)
}
