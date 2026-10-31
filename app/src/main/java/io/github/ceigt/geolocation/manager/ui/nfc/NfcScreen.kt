package io.github.ceigt.geolocation.manager.ui.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.domain.nfc.NfcHardwareState
import io.github.ceigt.geolocation.domain.nfc.NfcProfile
import io.github.ceigt.geolocation.manager.nfc.NfcDispatcher
import io.github.ceigt.geolocation.manager.nfc.NfcReaderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val hardwareState = when {
        nfcAdapter == null -> NfcHardwareState.UNSUPPORTED
        !nfcAdapter.isEnabled -> NfcHardwareState.DISABLED
        else -> NfcHardwareState.AVAILABLE
    }

    val persistedProfiles by repository.getNfcProfilesFlow().collectAsState(initial = emptyList())
    val activeProfileId by repository.getActiveNfcProfileIdFlow().collectAsState(initial = null)

    var isReadingMode by remember { mutableStateOf(false) }

    DisposableEffect(isReadingMode) {
        if (isReadingMode && activity != null) {
            val reader = NfcReaderManager(activity)
            reader.enableReaderMode { result ->
                val newProfile = NfcProfile(
                    name = "采集卡片 (${result.tagIdHex.take(6)})",
                    targetPackage = result.targetPackage ?: "",
                    targetLabel = result.targetPackage ?: "已捕获应用",
                    primaryUri = result.primaryUri
                )
                scope.launch {
                    val updated = persistedProfiles.toMutableList().apply { add(newProfile) }
                    repository.saveNfcProfiles(updated)
                    repository.saveActiveNfcProfileId(newProfile.id)
                }
                Toast.makeText(context, "成功读取 NFC 标签并持久化至模板库", Toast.LENGTH_SHORT).show()
                isReadingMode = false
            }
            onDispose { reader.disableReaderMode() }
        } else {
            onDispose { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC 模拟与标签派发", fontWeight = FontWeight.SemiBold) },
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
            // Hardware Status Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (hardwareState) {
                        NfcHardwareState.AVAILABLE -> Color(0xFFE8F5E9)
                        NfcHardwareState.DISABLED -> Color(0xFFFFF3E0)
                        NfcHardwareState.UNSUPPORTED -> Color(0xFFFFEBEE)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = when (hardwareState) {
                            NfcHardwareState.AVAILABLE -> Color(0xFF2E7D32)
                            NfcHardwareState.DISABLED -> Color(0xFFEF6C00)
                            NfcHardwareState.UNSUPPORTED -> Color(0xFFC62828)
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (hardwareState) {
                                NfcHardwareState.AVAILABLE -> "NFC 硬件已就绪"
                                NfcHardwareState.DISABLED -> "NFC 硬件已关闭"
                                NfcHardwareState.UNSUPPORTED -> "本机不支持 NFC (仍支持虚拟直接派发)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "虚拟派发无需贴卡，可直接向应用派发合法 NDEF Intent 事件",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Reader trigger
            if (hardwareState == NfcHardwareState.AVAILABLE) {
                Button(
                    onClick = { isReadingMode = !isReadingMode },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReadingMode) MaterialTheme.colorScheme.error else Color(0xFF006C5F)
                    )
                ) {
                    Text(if (isReadingMode) "请将实体 NFC 卡片贴在手机背面 (点击取消)" else "读取实体 NFC 标签并持久化克隆")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("持久化 NFC 模板库 (${persistedProfiles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (persistedProfiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无已保存的 NFC 模板，可贴卡读取或添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(persistedProfiles, key = { it.id }) { profile ->
                        val (valError, valWarnings) = NfcDispatcher.validateUri(profile.primaryUri)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val res = NfcDispatcher.dispatchVirtualTag(context, profile)
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "已成功派发 NFC 标签至 ${profile.targetLabel}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "派发失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "立即派发", tint = Color(0xFF006C5F))
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val updated = persistedProfiles.filter { it.id != profile.id }
                                                    repository.saveNfcProfiles(updated)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Text("目标包名: ${profile.targetPackage.ifBlank { "全局匹配" }}", style = MaterialTheme.typography.bodySmall)
                                Text("NDEF URI: ${profile.primaryUri}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF006C5F))

                                if (valError != null) {
                                    Text("URI错误: ${valError.name}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                                if (valWarnings.isNotEmpty()) {
                                    Text("安全提示: ${valWarnings.joinToString { it.name }}", color = Color(0xFFEF6C00), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
