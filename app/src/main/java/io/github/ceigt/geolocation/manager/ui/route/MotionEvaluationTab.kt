package io.github.ceigt.geolocation.manager.ui.route

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ceigt.geolocation.data.model.motion.MotionEvaluationReport
import io.github.ceigt.geolocation.data.model.motion.MotionRecordSession
import io.github.ceigt.geolocation.data.repository.MotionRepository
import io.github.ceigt.geolocation.domain.motion.MotionEvaluator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MotionEvaluationTab() {
    val context = LocalContext.current
    val repository = remember { MotionRepository(context) }
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<MotionRecordSession>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<MotionRecordSession?>(null) }
    var report by remember { mutableStateOf<MotionEvaluationReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshSessions() {
        scope.launch {
            sessions = repository.listSessions()
        }
    }

    LaunchedEffect(Unit) {
        refreshSessions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Session Picker Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("选择待评估的运动样本", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (sessions.isEmpty()) {
                    Text("暂无录制样本。请先在「真实采集」页录制一组运动数据！", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSession?.let { "${it.title} (${it.activityType.label})" } ?: "点击选择样本并运行评估...",
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
                                                "${s.activityType.label} | 人工: ${s.manualStepCount} 步 | 时长: ${s.durationSeconds.roundToInt()}s",
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
                                                report = MotionEvaluator.evaluate(full)
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

        // 2. Evaluation Report Card
        report?.let { rep ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FDFD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "对照评估报告: ${rep.sessionTitle}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A37)
                        )
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("MotionReport", generateMarkdownReport(rep))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "评估报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制报告", tint = Color(0xFF006C5F))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE0F2F1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = rep.summaryEvaluation,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF004D40),
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Check 1: Total Step Error
                    EvaluationMetricRow(
                        title = "1. 总步数误差 (Total Step Count)",
                        value = "人工: ${rep.manualStepCount} 步  vs  计算: ${rep.computedStepCount} 步",
                        detail = "绝对相差: ${rep.stepCountDiff} 步 | 相对误差: ${"%.1f".format(rep.stepCountErrorPercent)}%",
                        pass = rep.stepCountErrorPercent <= 10.0
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFCCE8E4))

                    // Check 2: Segmented Cadence Error
                    EvaluationMetricRow(
                        title = "2. 分段步频误差 (Segmented Cadence)",
                        value = "人工均频: ${"%.1f".format(rep.manualAvgSpm)} SPM  vs  计算均频: ${"%.1f".format(rep.computedAvgSpm)} SPM",
                        detail = "步频偏离: ${"%.1f".format(rep.cadenceErrorPercent)}% | 划分 10 秒时间片数: ${rep.segmentedSpmList.size} 段",
                        pass = rep.cadenceErrorPercent <= 15.0
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFCCE8E4))

                    // Check 3: State Response
                    EvaluationMetricRow(
                        title = "3. 状态响应 (State Transitions)",
                        value = "识别静止段: ${rep.detectedStationarySlices} 段 | 识别运动段: ${rep.detectedMotionSlices} 段",
                        detail = "起停响应判定: ${if (rep.stateResponseOk) "状态准确切分" else "存在过度模糊"}",
                        pass = rep.stateResponseOk
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFCCE8E4))

                    // Check 4: Repeatability
                    EvaluationMetricRow(
                        title = "4. 重复性校验 (Repeatability / Idempotence)",
                        value = if (rep.repeatabilityPass) "100% 确定性通过" else "出现非确定性偏差",
                        detail = "同一数据文件多轮独立回放，计算结果完全一致无漂移",
                        pass = rep.repeatabilityPass
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Segmented Table List
                    Text(
                        text = "10秒切片分段步频明细 (${rep.segmentedSpmList.size} 段)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A37)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    rep.segmentedSpmList.forEach { slice ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (slice.isStationary) Color(0xFFEEEEEE) else Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "第 ${slice.sliceIndex} 片 (${slice.startSecond.toInt()}s - ${slice.endSecond.toInt()}s)",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    "步数: ${slice.stepsInSlice} 步",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    "${slice.instantaneousSpm} SPM",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (slice.isStationary) Color.Gray else Color(0xFF006C5F)
                                )
                                Text(
                                    if (slice.isStationary) "静止" else "运动",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (slice.isStationary) Color(0xFF757575) else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationMetricRow(
    title: String,
    value: String,
    detail: String,
    pass: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (pass) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ) {
                Text(
                    text = if (pass) "合格" else "需关注",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pass) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
    }
}

private fun generateMarkdownReport(r: MotionEvaluationReport): String = buildString {
    appendLine("# 运动传感器数据对照评估报告")
    appendLine("- 样本名称: ${r.sessionTitle}")
    appendLine("- 采集编号: ${r.sessionId}")
    appendLine("- 总时长: ${"%.1f".format(r.durationSeconds)} 秒")
    appendLine()
    appendLine("## 核心误差指标")
    appendLine("1. **总步数误差**: 人工 ${r.manualStepCount} 步，计算 ${r.computedStepCount} 步，误差 ${"%.1f".format(r.stepCountErrorPercent)}%")
    appendLine("2. **分段步频误差**: 人工均频 ${"%.1f".format(r.manualAvgSpm)} SPM，计算均频 ${"%.1f".format(r.computedAvgSpm)} SPM，偏离 ${"%.1f".format(r.cadenceErrorPercent)}%")
    appendLine("3. **状态响应**: 静止 ${r.detectedStationarySlices} 段，运动 ${r.detectedMotionSlices} 段 (${if (r.stateResponseOk) "状态准确切分" else "存在过度模糊"})")
    appendLine("4. **重复性校验**: ${if (r.repeatabilityPass) "100% 确定性幂等通过" else "非确定性"}")
    appendLine()
    appendLine("## 评估结论")
    appendLine(r.summaryEvaluation)
}
