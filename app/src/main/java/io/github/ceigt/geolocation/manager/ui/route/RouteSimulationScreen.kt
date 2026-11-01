package io.github.ceigt.geolocation.manager.ui.route

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode
import io.github.ceigt.geolocation.data.model.route.*
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.manager.route.RoutePlanner
import io.github.ceigt.geolocation.manager.route.RouteSimulationService
import io.github.ceigt.geolocation.manager.route.TrackGenerator
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteSimulationScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("路线管理", "真实采集", "离线回放", "对照评估")

    val favorites by repository.getRouteFavoritesFlow().collectAsState(initial = emptyList())
    val engineState by RouteSimulationService.engine.stateFlow.collectAsState()
    val isRunning = engineState.isActive

    var showCustomTrackDialog by remember { mutableStateOf(false) }
    var customTrackMetersText by remember { mutableStateOf("400") }

    var editingRoute by remember { mutableStateOf<RouteFavorite?>(null) }
    var editName by remember { mutableStateOf("") }
    var editSpeedMps by remember { mutableDoubleStateOf(1.4) }
    var editLoopMode by remember { mutableStateOf(RouteLoopMode.CYCLE) }
    var editCadenceMode by remember { mutableStateOf(TargetCadenceMode.AUTO) }
    var editCustomSpm by remember { mutableIntStateOf(165) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("路线与运动数据", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Color(0xFF006C5F)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            when (selectedTab) {
                0 -> {
                    RouteManagementTab(
                        favorites = favorites,
                        isRunning = isRunning,
                        engineState = engineState,
                        onToggleSimulation = {
                            if (isRunning) {
                                RouteSimulationService.stopService(context)
                                scope.launch { repository.saveIsPlaying(false) }
                            } else {
                                val curLoc = repository.getLastClickedLocation()
                                if (curLoc != null) {
                                    showCustomTrackDialog = true
                                } else {
                                    Toast.makeText(context, "请先在地图上选定起跑点", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onStartRoute = { rf ->
                            RouteSimulationService.engine.start(
                                waypoints = rf.waypoints,
                                travelMode = rf.travelMode,
                                loopMode = rf.loopMode,
                                speedMps = rf.speedMps,
                                runMode = RouteRunMode.AUTOMATIC,
                                cadenceMode = rf.cadenceMode,
                                customSpm = rf.customSpm
                            )
                            scope.launch { repository.saveIsPlaying(true) }
                            checkNotificationPermission()
                            RouteSimulationService.startService(context)
                            Toast.makeText(context, "已启动巡航: ${rf.name}", Toast.LENGTH_SHORT).show()
                        },
                        onEditRoute = { rf ->
                            editingRoute = rf
                            editName = rf.name
                            editSpeedMps = rf.speedMps
                            editLoopMode = rf.loopMode
                            editCadenceMode = rf.cadenceMode
                            editCustomSpm = rf.customSpm
                        },
                        onDeleteRoute = { rf ->
                            scope.launch {
                                val updated = favorites.filter { it.id != rf.id }
                                repository.saveRouteFavorites(updated)
                            }
                        },
                        onGoToMap = { navController.popBackStack() }
                    )
                }
                1 -> MotionRecordTab(onSessionSaved = { selectedTab = 2 })
                2 -> MotionReplayTab()
                3 -> MotionEvaluationTab()
            }
        }
    }

    // Custom Stadium Track Dialog
    if (showCustomTrackDialog) {
        val curLoc = repository.getLastClickedLocation()
        AlertDialog(
            onDismissRequest = { showCustomTrackDialog = false },
            title = { Text("自定义操场跑道巡航", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "💡 跑道将以您在地图上选定的坐标为【起跑点】直接向前绘制闭环路线，保证真实无偏差。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF006C5F)
                    )
                    Text("选择操场周长标准 (一圈长度):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("200", "250", "300", "400", "800").forEach { meters ->
                            FilterChip(
                                selected = customTrackMetersText == meters,
                                onClick = { customTrackMetersText = meters },
                                label = { Text("${meters}米") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customTrackMetersText,
                        onValueChange = { customTrackMetersText = it.filter { c -> c.isDigit() } },
                        label = { Text("自定义一圈周长 (米)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val m = customTrackMetersText.toDoubleOrNull()?.coerceIn(50.0, 5000.0) ?: 400.0
                        if (curLoc != null) {
                            val trackWps = TrackGenerator.generateStadiumTrack(
                                TrackEditorConfig(
                                    centerLat = curLoc.latitude,
                                    centerLon = curLoc.longitude,
                                    circumferenceMeters = m,
                                    startAtSelectedPoint = true
                                )
                            )
                            RouteSimulationService.engine.start(
                                waypoints = trackWps,
                                runMode = RouteRunMode.AUTOMATIC
                            )
                            scope.launch { repository.saveIsPlaying(true) }
                            checkNotificationPermission()
                            RouteSimulationService.startService(context)
                            Toast.makeText(context, "已启动 ${m.toInt()} 米跑道巡航 (自起跑点开始)", Toast.LENGTH_SHORT).show()
                        }
                        showCustomTrackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006C5F))
                ) {
                    Text("生成并启动巡航")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTrackDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Edit Route Dialog
    editingRoute?.let { target ->
        AlertDialog(
            onDismissRequest = { editingRoute = null },
            title = { Text("编辑路线参数", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("路线名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("巡航速度: ${"%.1f".format(editSpeedMps)} m/s (${(editSpeedMps * 3.6).roundToInt()} km/h)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Slider(
                        value = editSpeedMps.toFloat(),
                        onValueChange = { editSpeedMps = it.toDouble() },
                        valueRange = 0.5f..25.0f,
                        steps = 49
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("循环模式", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        FilterChip(
                            selected = editLoopMode == RouteLoopMode.CYCLE,
                            onClick = { editLoopMode = RouteLoopMode.CYCLE },
                            label = { Text("闭环循环") }
                        )
                        FilterChip(
                            selected = editLoopMode == RouteLoopMode.BOUNCE,
                            onClick = { editLoopMode = RouteLoopMode.BOUNCE },
                            label = { Text("往返折返") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("路线专属步频模式", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "💡 说明：此处步频仅在当前路线巡航时生效；如需全局拦截，可在「设置-步频与传感器」统一配置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF55717C),
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        FilterChip(
                            selected = editCadenceMode == TargetCadenceMode.AUTO,
                            onClick = { editCadenceMode = TargetCadenceMode.AUTO },
                            label = { Text("自适应速度") }
                        )
                        FilterChip(
                            selected = editCadenceMode == TargetCadenceMode.CUSTOM,
                            onClick = { editCadenceMode = TargetCadenceMode.CUSTOM },
                            label = { Text("固定SPM") }
                        )
                        FilterChip(
                            selected = editCadenceMode == TargetCadenceMode.OFF,
                            onClick = { editCadenceMode = TargetCadenceMode.OFF },
                            label = { Text("关闭步频") }
                        )
                    }

                    if (editCadenceMode == TargetCadenceMode.CUSTOM) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { editCustomSpm = (editCustomSpm - 5).coerceIn(40, 240) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "-5")
                            }
                            Text(
                                text = "目标步频: $editCustomSpm SPM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(
                                onClick = { editCustomSpm = (editCustomSpm + 5).coerceIn(40, 240) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "+5")
                            }
                        }
                        Slider(
                            value = editCustomSpm.toFloat(),
                            onValueChange = { editCustomSpm = it.toInt() },
                            valueRange = 40f..240f,
                            steps = 200
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val updated = favorites.map {
                            if (it.id == target.id) {
                                it.copy(
                                    name = editName.ifBlank { target.name },
                                    speedMps = editSpeedMps,
                                    loopMode = editLoopMode,
                                    cadenceMode = editCadenceMode,
                                    customSpm = editCustomSpm
                                )
                            } else it
                        }
                        repository.saveRouteFavorites(updated)
                    }
                    editingRoute = null
                    Toast.makeText(context, "路线已更新保存", Toast.LENGTH_SHORT).show()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRoute = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteManagementTab(
    favorites: List<RouteFavorite>,
    isRunning: Boolean,
    engineState: RouteSimulationState,
    onToggleSimulation: () -> Unit,
    onStartRoute: (RouteFavorite) -> Unit,
    onEditRoute: (RouteFavorite) -> Unit,
    onDeleteRoute: (RouteFavorite) -> Unit,
    onGoToMap: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (isRunning) Color(0xFFE8F5E9) else Color(0xFFF7FDFD))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isRunning) "路线模拟运行中 (巡航推进)" else "路线模拟已停止",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color(0xFF1B5E20) else Color(0xFF334155)
                    )
                    val speedStr = "%.1f".format(engineState.speedMetersPerSecond)
                    Text(
                        text = "速度: $speedStr m/s (${(engineState.speedMetersPerSecond * 3.6).roundToInt()} km/h)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onToggleSimulation,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error else Color(0xFF006C5F)
                        )
                    ) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRunning) "停止模拟" else "标准跑道巡航")
                    }
                    if (isRunning) {
                        HorizontalDivider(color = Color(0xFF81C784).copy(alpha = 0.5f))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("已巡航: ${"%.1f".format(engineState.traveledMeters)} 米", style = MaterialTheme.typography.labelMedium)
                            Text("当前步频: ${engineState.currentSpm} SPM", style = MaterialTheme.typography.labelMedium, color = Color(0xFF006C5F))
                            Text("已走步数: ${engineState.currentStepCount} 步", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("已收藏路线 (${favorites.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onGoToMap) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("前往地图规划路线")
                }
            }
        }
        if (favorites.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("暂无收藏路线。前往地图主界面可自由打点、手绘或自动生成路线！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(favorites, key = { it.id }) { rf ->
                val geoPoints = rf.waypoints.map { GeoPoint(it.latitude, it.longitude) }
                val distMeters = RoutePlanner.computeTotalDistanceMeters(geoPoints)
                val estSteps = RoutePlanner.estimateSteps(distMeters, rf.speedMps)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(rf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "航点: ${rf.waypoints.size} 个 | 里程: ${"%.2f".format(distMeters / 1000.0)} km | 预计: $estSteps 步",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF52615E)
                        )
                        Text(
                            "速度: ${"%.1f".format(rf.speedMps)} m/s (${(rf.speedMps * 3.6).roundToInt()} km/h) | 循环: ${rf.loopMode.name} | 步频: ${rf.cadenceMode.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF006C5F)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { onEditRoute(rf) }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color(0xFF006C5F))
                            }
                            IconButton(onClick = { onStartRoute(rf) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "运行", tint = Color(0xFF006C5F))
                            }
                            IconButton(onClick = { onDeleteRoute(rf) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
