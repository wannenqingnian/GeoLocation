package io.github.ceigt.geolocation.manager.ui.route

import android.Manifest
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import io.github.ceigt.geolocation.data.model.route.RouteFavorite
import io.github.ceigt.geolocation.data.model.route.RouteRunMode
import io.github.ceigt.geolocation.data.model.route.TrackEditorConfig
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.manager.route.RouteSimulationService
import io.github.ceigt.geolocation.manager.route.TrackGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSimulationScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    val favorites by repository.getRouteFavoritesFlow().collectAsState(initial = emptyList())
    var isRunning by remember { mutableStateOf(RouteSimulationService.engine.state.isActive) }

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
            TopAppBar(
                title = { Text("路线与跑道模拟管理", fontWeight = FontWeight.SemiBold) },
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
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isRunning) Color(0xFFE8F5E9) else Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "路线模拟运行中" else "路线模拟已停止",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "当前速度: ${"%.1f".format(RouteSimulationService.engine.state.speedMetersPerSecond)} m/s",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            if (isRunning) {
                                RouteSimulationService.stopService(context)
                                isRunning = false
                            } else {
                                // Default run track if waypoints exist
                                val curLoc = repository.getLastClickedLocation()
                                if (curLoc != null) {
                                    val trackWps = TrackGenerator.generateStadiumTrack(
                                        TrackEditorConfig(curLoc.latitude, curLoc.longitude)
                                    )
                                    RouteSimulationService.engine.start(
                                        waypoints = trackWps,
                                        runMode = RouteRunMode.AUTOMATIC
                                    )
                                    checkNotificationPermission()
                                    RouteSimulationService.startService(context)
                                    isRunning = true
                                    Toast.makeText(context, "已启动 400 米标准环形跑道模拟巡航", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "请先在地图上选定跑道中心点", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error else Color(0xFF006C5F)
                        )
                    ) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRunning) "停止模拟" else "一键生成400m跑道巡航")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("已收藏的路线规划 (${favorites.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (favorites.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无收藏路线，可点击上方按钮一键生成标准跑道", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(favorites, key = { it.id }) { rf ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("航点数量: ${rf.waypoints.size} 个  |  模式: ${rf.travelMode.name}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            RouteSimulationService.engine.start(
                                                waypoints = rf.waypoints,
                                                travelMode = rf.travelMode,
                                                loopMode = rf.loopMode,
                                                runMode = RouteRunMode.AUTOMATIC
                                            )
                                            checkNotificationPermission()
                                            RouteSimulationService.startService(context)
                                            isRunning = true
                                            Toast.makeText(context, "已启动路线: ${rf.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "运行", tint = Color(0xFF006C5F))
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val updated = favorites.filter { it.id != rf.id }
                                                repository.saveRouteFavorites(updated)
                                            }
                                        }
                                    ) {
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
