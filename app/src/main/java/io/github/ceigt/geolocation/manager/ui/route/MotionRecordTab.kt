package io.github.ceigt.geolocation.manager.ui.route

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ceigt.geolocation.data.model.motion.*
import io.github.ceigt.geolocation.data.repository.MotionRepository
import io.github.ceigt.geolocation.domain.motion.MotionDataCollector
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionRecordTab(
    onSessionSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { MotionRepository(context) }
    val collector = remember { MotionDataCollector(context) }
    val scope = rememberCoroutineScope()

    val state by collector.state.collectAsState()
    val availability = collector.availability

    var title by remember { mutableStateOf("") }
    var selectedActivity by remember { mutableStateOf(MotionActivityType.BRISK_WALK) }
    var selectedPosition by remember { mutableStateOf(PhoneCarryPosition.PANT_POCKET) }
    var manualStepsText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "已获得身体活动识别权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensurePermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                return
            }
        }
        collector.startRecording()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (collector.state.value.isRecording) {
                collector.cancelRecording()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hardware Sensor Availability Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FDFD))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "硬件传感器就绪状态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A37)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SensorStatusBadge("加速度计", availability.hasAccelerometer)
                    SensorStatusBadge("陀螺仪", availability.hasGyroscope)
                    SensorStatusBadge("计步器", availability.hasStepCounter)
                    SensorStatusBadge("步伐检测", availability.hasStepDetector)
                }

                if (availability.missingSensors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ 缺失: ${availability.missingSensors.joinToString("、")}。系统将如实标记，绝不虚构数据。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // 2. Real-time Recording Feedback / Waveform Monitor
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isRecording) Color(0xFFE8F5E9) else Color.White
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (state.isRecording) Color(0xFFE53935) else Color(0xFF9E9E9E),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isRecording) "正在实时采集传感器样本..." else "传感器采集就绪 (静止)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isRecording) Color(0xFF1B5E20) else Color(0xFF334155)
                        )
                    }

                    if (state.isRecording) {
                        val mins = state.durationSeconds / 60
                        val secs = state.durationSeconds % 60
                        Text(
                            text = "%02d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF006C5F)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("样本总点数", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                        Text("${state.sampleCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("当前模长 |a|", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                        Text("${"%.2f".format(state.latestAccMagnitude)} m/s²", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("硬件计步", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                        Text("${state.hardwareSensorSteps} 步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("检测器脉冲", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                        Text("${state.stepDetectorHits} 次", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                // Acceleration values visualization
                if (state.isRecording) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "三轴: X=${"%.2f".format(state.latestValues[0])}  Y=${"%.2f".format(state.latestValues[1])}  Z=${"%.2f".format(state.latestValues[2])}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF006C5F)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!state.isRecording) {
                        Button(
                            onClick = { ensurePermissionThenStart() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006C5F)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("开始录制真实样本")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { collector.cancelRecording() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消放弃")
                        }

                        Button(
                            onClick = {
                                val manualSteps = manualStepsText.toIntOrNull() ?: 0
                                val session = collector.stopRecording(
                                    title = title,
                                    activityType = selectedActivity,
                                    carryPosition = selectedPosition,
                                    manualStepCount = manualSteps,
                                    note = note
                                )
                                scope.launch {
                                    repository.saveSession(session)
                                    Toast.makeText(context, "已成功保存 ${session.samples.size} 个传感器样本到本地库！", Toast.LENGTH_SHORT).show()
                                    title = ""
                                    manualStepsText = ""
                                    note = ""
                                    onSessionSaved()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("停止并保存样本")
                        }
                    }
                }
            }
        }

        // 3. Metadata & Label Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "样本标签与人工真实比对基准",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A37)
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("采集名称 / 场景描述") },
                    placeholder = { Text("例: 操场快走400米 / 上班快步走") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("真实运动类型", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        MotionActivityType.SLOW_WALK,
                        MotionActivityType.BRISK_WALK,
                        MotionActivityType.JOGGING
                    ).forEach { act ->
                        FilterChip(
                            selected = selectedActivity == act,
                            onClick = { selectedActivity = act },
                            label = { Text(act.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        MotionActivityType.RUNNING,
                        MotionActivityType.STATIONARY,
                        MotionActivityType.STAIRS
                    ).forEach { act ->
                        FilterChip(
                            selected = selectedActivity == act,
                            onClick = { selectedActivity = act },
                            label = { Text(act.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("手机携带/摆放位置", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PhoneCarryPosition.PANT_POCKET,
                        PhoneCarryPosition.HANDHELD,
                        PhoneCarryPosition.ARMBAND
                    ).forEach { pos ->
                        FilterChip(
                            selected = selectedPosition == pos,
                            onClick = { selectedPosition = pos },
                            label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PhoneCarryPosition.JACKET_POCKET,
                        PhoneCarryPosition.BACKPACK
                    ).forEach { pos ->
                        FilterChip(
                            selected = selectedPosition == pos,
                            onClick = { selectedPosition = pos },
                            label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = manualStepsText,
                    onValueChange = { str -> manualStepsText = str.filter { it.isDigit() } },
                    label = { Text("人工实测步数 (对照黄金基准)") },
                    placeholder = { Text("例: 人工心数走了 350 步") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注信息 (可选)") },
                    placeholder = { Text("例: 穿硬底运动鞋，塑胶跑道") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SensorStatusBadge(name: String, available: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (available) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (available) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (available) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = if (available) Color(0xFF1B5E20) else Color(0xFFB71C1C)
            )
        }
    }
}
