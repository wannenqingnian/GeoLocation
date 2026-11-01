package io.github.ceigt.geolocation.manager.ui.route

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import io.github.ceigt.geolocation.data.model.motion.MotionRecordSession
import io.github.ceigt.geolocation.data.repository.MotionRepository
import io.github.ceigt.geolocation.domain.motion.MotionReplayEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MotionReplayTab(
    replayEngine: MotionReplayEngine = remember { MotionReplayEngine() }
) {
    val context = LocalContext.current
    val repository = remember { MotionRepository(context) }
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<MotionRecordSession>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<MotionRecordSession?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val replayState by replayEngine.state.collectAsState()

    fun loadSessionsList() {
        scope.launch {
            sessions = repository.listSessions()
        }
    }

    LaunchedEffect(Unit) {
        loadSessionsList()
    }

    DisposableEffect(Unit) {
        onDispose {
            replayEngine.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Prominent Offline Replay Mode Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF3E0),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFE65100))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "离线回放模式 (OFFLINE REPLAY)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "纯本地文件驱动，按真实硬件采样时间戳与采样间隔 100% 幂等回放，不产生物理传感器修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF795548)
                    )
                }
            }
        }

        // 2. Select Sample from Local Library Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择本地样本文件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { loadSessionsList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color(0xFF006C5F))
                    }
                }

                if (sessions.isEmpty()) {
                    Text("暂无录制的真实运动样本。请前往「真实采集」页录制第一组运动数据！", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSession?.let { "${it.title} (${it.activityType.label})" } ?: "点击下拉选择样本...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            sessions.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(s.title, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${s.activityType.label} | ${s.carryPosition.label} | 时长 ${s.durationSeconds.roundToInt()}s | 人工 ${s.manualStepCount} 步",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        expanded = false
                                        isLoading = true
                                        scope.launch {
                                            val full = repository.loadFullSession(s.id)
                                            selectedSession = full
                                            if (full != null) {
                                                replayEngine.loadSession(full)
                                                Toast.makeText(context, "已载入 ${full.samples.size} 个真实样本点", Toast.LENGTH_SHORT).show()
                                            }
                                            isLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Playback Dashboard & Live Waveform Curve
        selectedSession?.let { currentSession ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FDFD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "样本回放面板: ${currentSession.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A37)
                    )
                    Text(
                        text = "设备: ${currentSession.deviceModel} | 位置: ${currentSession.carryPosition.label} | 运动: ${currentSession.activityType.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF55717C)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metrics Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Column {
                            Text("回放累计步数", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                            Text("${replayState.accumulatedSteps} 步", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF006C5F))
                        }
                        Column {
                            Text("瞬时步频", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                            Text("${replayState.instantaneousSpm} SPM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                        Column {
                            Text("当前加速度模长", style = MaterialTheme.typography.labelSmall, color = Color(0xFF55717C))
                            Text("${"%.2f".format(replayState.currentAccMagnitude)} m/s²", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Waveform Canvas (Last 80 points)
                    Text("实时加速度模长曲线 (|a| vs 时间)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(vertical = 4.dp)
                            .background(Color(0xFF0F1E1D), RoundedCornerShape(8.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            val pts = replayState.recentMagnitudeWindow
                            if (pts.size > 1) {
                                val w = size.width
                                val h = size.height
                                val minVal = 5.0f
                                val maxVal = 22.0f
                                val stepX = w / (pts.size - 1)

                                val path = Path()
                                for (i in pts.indices) {
                                    val x = i * stepX
                                    val norm = ((pts[i] - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                                    val y = h - (norm * h)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color(0xFF4DD0E1), style = Stroke(width = 3.5f))

                                // Draw 9.81g baseline
                                val baseNorm = ((9.81f - minVal) / (maxVal - minVal))
                                val baseY = h - (baseNorm * h)
                                drawLine(
                                    color = Color(0xFF80CBC4).copy(alpha = 0.4f),
                                    start = Offset(0f, baseY),
                                    end = Offset(w, baseY),
                                    strokeWidth = 1.5f
                                )
                            }
                        }
                    }

                    // Progress bar
                    val durTotal = currentSession.durationSeconds.coerceAtLeast(0.1)
                    val progress = (replayState.elapsedPlaybackSeconds / durTotal).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 8.dp),
                        color = Color(0xFF006C5F)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "%02d:%02d".format(replayState.elapsedPlaybackSeconds.toInt() / 60, replayState.elapsedPlaybackSeconds.toInt() % 60),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "%02d:%02d".format(durTotal.toInt() / 60, durTotal.toInt() % 60),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Playback Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (replayState.isPlaying) replayEngine.pause()
                                else replayEngine.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006C5F)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(if (replayState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (replayState.isPlaying) "暂停" else "播放回放")
                        }

                        OutlinedButton(
                            onClick = { replayEngine.reset() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重置")
                        }

                        // Speed multiplier chips
                        listOf(1.0, 2.0).forEach { spd ->
                            FilterChip(
                                selected = replayState.playbackSpeed == spd,
                                onClick = { replayEngine.play(playbackSpeed = spd) },
                                label = { Text("${spd.toInt()}x") }
                            )
                        }
                    }
                }
            }
        }
    }
}
