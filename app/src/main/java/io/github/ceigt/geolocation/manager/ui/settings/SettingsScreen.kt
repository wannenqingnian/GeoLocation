package io.github.ceigt.geolocation.manager.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.MapProvider
import io.github.ceigt.geolocation.data.OsmTileServer
import io.github.ceigt.geolocation.data.model.mode.HookInstallPolicy
import io.github.ceigt.geolocation.data.model.mode.LocationMode
import io.github.ceigt.geolocation.data.model.mode.MockFlagPolicy
import io.github.ceigt.geolocation.manager.control.ControlReceiver
import io.github.ceigt.geolocation.manager.localization.LanguageOption
import io.github.ceigt.geolocation.manager.localization.LocaleController
import io.github.ceigt.geolocation.manager.map.WebMapCache
import io.github.ceigt.geolocation.manager.ui.navigation.Screen
import kotlinx.coroutines.launch

private object Dimensions {
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 16.dp
    val SPACING_LARGE = 24.dp
    val CARD_CORNER_RADIUS = 20.dp
    val CARD_ELEVATION = 0.dp
}

private val MiuixPageBackground = Color(0xFFF3F7F8)
private val MiuixCardSurface = Color(0xFFFFFFFF)
private val SettingsAccent = Color(0xFF006C5F)
private val SettingsOutline = Color(0xFFDCE7E9)
private const val BAIDU_AK_CONSOLE_URL = "https://lbsyun.baidu.com/apiconsole/key#/home"
private const val BAIDU_MAP_REFERER_HOST = "appassets.androidplatform.net"
private const val AMAP_CONSOLE_URL = "https://console.amap.com/dev/key/app"
private const val GOOGLE_MAPS_CONSOLE_URL = "https://console.cloud.google.com/google/maps-apis/credentials"

private object SettingDefinitions {
    @Composable
    fun getCategories(): Map<String, List<String>> {
        val randomizeTitle = stringResource(R.string.setting_randomize_title)
        val horizontalAccuracyTitle = stringResource(R.string.setting_horizontal_accuracy_title)
        val verticalAccuracyTitle = stringResource(R.string.setting_vertical_accuracy_title)
        val altitudeTitle = stringResource(R.string.setting_altitude_title)
        val mslTitle = stringResource(R.string.setting_msl_title)
        val mslAccuracyTitle = stringResource(R.string.setting_msl_accuracy_title)
        val speedTitle = stringResource(R.string.setting_speed_title)
        val speedAccuracyTitle = stringResource(R.string.setting_speed_accuracy_title)

        return mapOf(
            stringResource(R.string.category_location) to listOf(
                randomizeTitle,
                horizontalAccuracyTitle,
                verticalAccuracyTitle
            ),
            stringResource(R.string.category_altitude) to listOf(
                altitudeTitle,
                mslTitle,
                mslAccuracyTitle
            ),
            stringResource(R.string.category_movement) to listOf(
                speedTitle,
                speedAccuracyTitle
            )
        )
    }

    @Composable
    fun getSettings(viewModel: SettingsViewModel): List<SettingData> = listOf(
        DoubleSettingData(
            title = stringResource(R.string.setting_randomize_title),
            description = stringResource(R.string.setting_randomize_description),
            useValueState = viewModel.useRandomize.collectAsState(),
            valueState = viewModel.randomizeRadius.collectAsState(),
            setUseValue = viewModel::setUseRandomize,
            setValue = viewModel::setRandomizeRadius,
            label = stringResource(R.string.setting_randomize_radius_label),
            unit = "m",
            minValue = 0f,
            maxValue = 2000f,
            step = 0.1f
        ),
        DoubleSettingData(
            title = stringResource(R.string.setting_horizontal_accuracy_title),
            description = stringResource(R.string.setting_horizontal_accuracy_description),
            useValueState = viewModel.useAccuracy.collectAsState(),
            valueState = viewModel.accuracy.collectAsState(),
            setUseValue = viewModel::setUseAccuracy,
            setValue = viewModel::setAccuracy,
            label = stringResource(R.string.setting_horizontal_accuracy_label),
            unit = "m",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_vertical_accuracy_title),
            description = stringResource(R.string.setting_vertical_accuracy_description),
            useValueState = viewModel.useVerticalAccuracy.collectAsState(),
            valueState = viewModel.verticalAccuracy.collectAsState(),
            setUseValue = viewModel::setUseVerticalAccuracy,
            setValue = viewModel::setVerticalAccuracy,
            label = stringResource(R.string.setting_vertical_accuracy_label),
            unit = "m",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        ),
        DoubleSettingData(
            title = stringResource(R.string.setting_altitude_title),
            description = stringResource(R.string.setting_altitude_description),
            useValueState = viewModel.useAltitude.collectAsState(),
            valueState = viewModel.altitude.collectAsState(),
            setUseValue = viewModel::setUseAltitude,
            setValue = viewModel::setAltitude,
            label = stringResource(R.string.setting_altitude_label),
            unit = "m",
            minValue = 0f,
            maxValue = 2000f,
            step = 0.5f
        ),
        DoubleSettingData(
            title = stringResource(R.string.setting_msl_title),
            description = stringResource(R.string.setting_msl_description),
            useValueState = viewModel.useMeanSeaLevel.collectAsState(),
            valueState = viewModel.meanSeaLevel.collectAsState(),
            setUseValue = viewModel::setUseMeanSeaLevel,
            setValue = viewModel::setMeanSeaLevel,
            label = stringResource(R.string.setting_msl_label),
            unit = "m",
            minValue = -400f,
            maxValue = 2000f,
            step = 0.5f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_msl_accuracy_title),
            description = stringResource(R.string.setting_msl_accuracy_description),
            useValueState = viewModel.useMeanSeaLevelAccuracy.collectAsState(),
            valueState = viewModel.meanSeaLevelAccuracy.collectAsState(),
            setUseValue = viewModel::setUseMeanSeaLevelAccuracy,
            setValue = viewModel::setMeanSeaLevelAccuracy,
            label = stringResource(R.string.setting_msl_accuracy_label),
            unit = "m",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_speed_title),
            description = stringResource(R.string.setting_speed_description),
            useValueState = viewModel.useSpeed.collectAsState(),
            valueState = viewModel.speed.collectAsState(),
            setUseValue = viewModel::setUseSpeed,
            setValue = viewModel::setSpeed,
            label = stringResource(R.string.setting_speed_label),
            unit = "m/s",
            minValue = 0f,
            maxValue = 30f,
            step = 0.1f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_speed_accuracy_title),
            description = stringResource(R.string.setting_speed_accuracy_description),
            useValueState = viewModel.useSpeedAccuracy.collectAsState(),
            valueState = viewModel.speedAccuracy.collectAsState(),
            setUseValue = viewModel::setUseSpeedAccuracy,
            setValue = viewModel::setSpeedAccuracy,
            label = stringResource(R.string.setting_speed_accuracy_label),
            unit = "m/s",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val allSettings = SettingDefinitions.getSettings(settingsViewModel)
    val failedSaveRevision by settingsViewModel.failedSaveRevision.collectAsState()
    val categories = SettingDefinitions.getCategories()
    val selectedLanguage = LanguageOption.fromTag(settingsViewModel.languageTag.collectAsState().value)

    val snackbarHostState = remember { SnackbarHostState() }
    val cacheScope = rememberCoroutineScope()
    var missingSystemScopePackages by remember { mutableStateOf<List<String>?>(null) }
    var targetAppScopeRequired by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(0) }

    LaunchedEffect(settingsTab) { scrollState.scrollTo(0) }

    LaunchedEffect(Unit) {
        settingsViewModel.saveError.collect { failed ->
            if (failed) {
                snackbarHostState.showSnackbar(context.getString(R.string.setting_save_failed))
                settingsViewModel.clearSaveError()
            }
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.systemHooksEvents.collect { event ->
            when (event) {
                is SystemHooksEvent.UnsupportedSystemVersion ->
                    snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_unsupported_version))
                is SystemHooksEvent.ModuleNotActive ->
                    snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                is SystemHooksEvent.TargetAppScopeRequired ->
                    targetAppScopeRequired = true
                is SystemHooksEvent.ScopeSetupRequired ->
                    missingSystemScopePackages = event.missingPackages
                is SystemHooksEvent.RootPermissionRequired ->
                    snackbarHostState.showSnackbar(context.getString(R.string.target_apps_root_required))
            }
        }
    }

    Scaffold(
        containerColor = MiuixPageBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MiuixPageBackground,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                SettingsCategoryTabs(selectedIndex = settingsTab, onSelected = { settingsTab = it })

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimensions.SPACING_MEDIUM)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                    SettingsCategorySummary(selectedIndex = settingsTab)

                    when (settingsTab) {
                        0 -> {
                            CategoryHeader(stringResource(R.string.category_location_mode))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                LocationModeSelector(
                                    selectedMode = settingsViewModel.locationMode.collectAsState().value,
                                    onModeSelected = settingsViewModel::selectLocationMode
                                )
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            CategoryHeader("加固与模拟标志策略")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    Text(
                                        text = "加固应用挂载策略",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = Dimensions.SPACING_EXTRA_SMALL)
                                    )
                                    val installPolicy by settingsViewModel.hookInstallPolicy.collectAsState()
                                    LocationModeOption(
                                        title = "立即挂载 (IMMEDIATE)",
                                        description = "PackageReady 阶段立即注入主 ClassLoader",
                                        selected = installPolicy == HookInstallPolicy.IMMEDIATE,
                                        onClick = { settingsViewModel.setHookInstallPolicy(HookInstallPolicy.IMMEDIATE) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "动态类加载挂载 (DYNAMIC_CLASSLOAD)",
                                        description = "Hook loadClass，针对加壳应用解密后真实 ClassLoader 挂载",
                                        selected = installPolicy == HookInstallPolicy.DYNAMIC_CLASSLOAD,
                                        onClick = { settingsViewModel.setHookInstallPolicy(HookInstallPolicy.DYNAMIC_CLASSLOAD) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "Application 启动时补装 (APPLICATION_CREATE)",
                                        description = "在 callApplicationOnCreate 时根据获取的真实 ClassLoader 补装",
                                        selected = installPolicy == HookInstallPolicy.APPLICATION_CREATE,
                                        onClick = { settingsViewModel.setHookInstallPolicy(HookInstallPolicy.APPLICATION_CREATE) }
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL), color = MaterialTheme.colorScheme.outlineVariant)

                                    Text(
                                        text = "Mock 标志剥离策略",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = Dimensions.SPACING_EXTRA_SMALL)
                                    )
                                    val flagPolicy by settingsViewModel.mockFlagPolicy.collectAsState()
                                    LocationModeOption(
                                        title = "完全剥离 (HIDE_ALL)",
                                        description = "清除 isFromMockProvider、setMock 与 mFieldsMask mock bit",
                                        selected = flagPolicy == MockFlagPolicy.HIDE_ALL,
                                        onClick = { settingsViewModel.setMockFlagPolicy(MockFlagPolicy.HIDE_ALL) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "保留原始标志 (PRESERVE)",
                                        description = "不主动清除系统 Mock 标志（调试场景）",
                                        selected = flagPolicy == MockFlagPolicy.PRESERVE,
                                        onClick = { settingsViewModel.setMockFlagPolicy(MockFlagPolicy.PRESERVE) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            CategoryHeader("GNSS 卫星模拟与星座状态")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    val constellationPreset by settingsViewModel.gnssConstellationPreset.collectAsState()
                                    LocationModeOption(
                                        title = "混合多模 (GPS + 北斗 + Galileo + GLONASS)",
                                        description = "模拟真实户外多星座联合解算状态",
                                        selected = constellationPreset == io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.AUTO_MIXED,
                                        onClick = { settingsViewModel.setGnssConstellationPreset(io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.AUTO_MIXED) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "仅北斗 (BDS Only)",
                                        description = "纯北斗卫星星座导航频段模拟",
                                        selected = constellationPreset == io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.BEIDOU_ONLY,
                                        onClick = { settingsViewModel.setGnssConstellationPreset(io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.BEIDOU_ONLY) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "仅 GPS (GPS Only)",
                                        description = "纯美标 GPS 卫星星座频段模拟",
                                        selected = constellationPreset == io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.GPS_ONLY,
                                        onClick = { settingsViewModel.setGnssConstellationPreset(io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.GPS_ONLY) }
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL), color = MaterialTheme.colorScheme.outlineVariant)

                                    val visibleSatellites by settingsViewModel.gnssVisibleSatellites.collectAsState()
                                    Text(
                                        text = "可见卫星数量: $visibleSatellites 颗",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = Dimensions.SPACING_EXTRA_SMALL)
                                    )
                                    Slider(
                                        value = visibleSatellites.toFloat(),
                                        onValueChange = { settingsViewModel.setGnssVisibleSatellites(it.toInt()) },
                                        valueRange = 8f..36f,
                                        steps = 28
                                    )

                                    val usedSatellites by settingsViewModel.gnssUsedSatellites.collectAsState()
                                    Text(
                                        text = "参与解算卫星数量: $usedSatellites 颗",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = Dimensions.SPACING_EXTRA_SMALL)
                                    )
                                    Slider(
                                        value = usedSatellites.coerceAtMost(visibleSatellites).toFloat(),
                                        onValueChange = { settingsViewModel.setGnssUsedSatellites(it.toInt().coerceAtMost(visibleSatellites)) },
                                        valueRange = 4f..36f,
                                        steps = 32
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL), color = MaterialTheme.colorScheme.outlineVariant)

                                    val signalPreset by settingsViewModel.gnssSignalStrengthPreset.collectAsState()
                                    LocationModeOption(
                                        title = "强信号 (GOOD: 38~48 dB-Hz)",
                                        description = "开阔室外、晴朗天候，优质卫星几何分布",
                                        selected = signalPreset == io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.GOOD,
                                        onClick = { settingsViewModel.setGnssSignalStrengthPreset(io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.GOOD) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "标准信号 (NORMAL: 28~38 dB-Hz)",
                                        description = "典型城市道路与轻度遮挡环境",
                                        selected = signalPreset == io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.NORMAL,
                                        onClick = { settingsViewModel.setGnssSignalStrengthPreset(io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.NORMAL) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "微弱信号 (WEAK: 18~26 dB-Hz)",
                                        description = "建筑物窗边、高架下或树荫密集处",
                                        selected = signalPreset == io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.WEAK,
                                        onClick = { settingsViewModel.setGnssSignalStrengthPreset(io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.WEAK) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            CategoryHeader("高阶定位数学抖动模型 (GnssJitter)")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    val jitterEnabled by settingsViewModel.gnssJitterEnabled.collectAsState()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(Dimensions.SPACING_SMALL),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("启用高阶定位抖动模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                            Text("模拟真实卫星信号大气延迟与载波多径波动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = jitterEnabled, onCheckedChange = { settingsViewModel.setGnssJitterEnabled(it) })
                                    }
                                    if (jitterEnabled) {
                                        val jitterAlgo by settingsViewModel.gnssJitterAlgorithm.collectAsState()
                                        LocationModeOption(
                                            title = "一阶高斯-马尔可夫随机游走 (FIRST_ORDER_MARKOV)",
                                            description = "平滑连续相关随机漂移模型",
                                            selected = jitterAlgo == io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FIRST_ORDER_MARKOV,
                                            onClick = { settingsViewModel.setGnssJitterAlgorithm(io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FIRST_ORDER_MARKOV) }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        LocationModeOption(
                                            title = "二阶马尔可夫惯性漂移 (SECOND_ORDER_MARKOV)",
                                            description = "具备方向惯性记忆与阻尼回归",
                                            selected = jitterAlgo == io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.SECOND_ORDER_MARKOV,
                                            onClick = { settingsViewModel.setGnssJitterAlgorithm(io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.SECOND_ORDER_MARKOV) }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        LocationModeOption(
                                            title = "低通滤波高斯白噪声 (FILTERED_GAUSSIAN)",
                                            description = "轻度滤波白噪声",
                                            selected = jitterAlgo == io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FILTERED_GAUSSIAN,
                                            onClick = { settingsViewModel.setGnssJitterAlgorithm(io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FILTERED_GAUSSIAN) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL), color = MaterialTheme.colorScheme.outlineVariant)

                                        val jitterRadius by settingsViewModel.gnssJitterRadius.collectAsState()
                                        Text("漂移半径: ${"%.1f".format(jitterRadius)} 米", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Slider(value = jitterRadius.toFloat(), onValueChange = { settingsViewModel.setGnssJitterRadius(it.toDouble()) }, valueRange = 0.5f..10.0f)

                                        val jitterSmoothing by settingsViewModel.gnssJitterSmoothing.collectAsState()
                                        Text("平滑滤波比例: ${"%.2f".format(jitterSmoothing)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Slider(value = jitterSmoothing.toFloat(), onValueChange = { settingsViewModel.setGnssJitterSmoothing(it.toDouble()) }, valueRange = 0.1f..0.98f)

                                        val jitterDriftSpeed by settingsViewModel.gnssJitterDriftSpeed.collectAsState()
                                        Text("最大惯性漂移速度: ${"%.2f".format(jitterDriftSpeed)} m/s", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Slider(value = jitterDriftSpeed.toFloat(), onValueChange = { settingsViewModel.setGnssJitterDriftSpeed(it.toDouble()) }, valueRange = 0.05f..2.0f)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            categories.forEach { (category, settingsInCategory) ->
                                CategoryHeader(category)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimensions.SPACING_SMALL),
                                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                    colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                                ) {
                                    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                        settingsInCategory.forEach { settingTitle ->
                                            val setting = allSettings.find { it.title == settingTitle }
                                            setting?.let {
                                                when (setting) {
                                                    is DoubleSettingData -> DoubleSettingComposable(setting, failedSaveRevision)
                                                    is FloatSettingData -> FloatSettingComposable(setting, failedSaveRevision)
                                                }
                                            }
                                            if (settingTitle != settingsInCategory.last()) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                            }
                        }
                        1 -> {
                            CategoryHeader("高级模拟子系统")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    LocationModeOption(
                                        title = "路线规划与跑道巡航 (Route Simulation)",
                                        description = "管理已收藏的巡航路线、一键生成标准 400 米环形跑道并启动模拟",
                                        selected = false,
                                        onClick = { navController.navigate(Screen.Route.route) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "Wi-Fi、基站与蓝牙环境模拟 (Virtual Environment)",
                                        description = "统一配置虚拟 AP 热点、4G/5G 蜂窝基站与蓝牙信标，支持现场一键真实无线电全要素采样",
                                        selected = false,
                                        onClick = { navController.navigate(Screen.Environment.route) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "NFC 标签读写与虚拟派发 (NFC Simulation)",
                                        description = "读取实体 NFC 卡片 NDEF 记录，支持免贴卡向目标应用直接派发虚拟标签",
                                        selected = false,
                                        onClick = { navController.navigate(Screen.Nfc.route) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            CategoryHeader("步频与传感器步频模拟 (Cadence SPM)")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    Text(
                                        text = "全局步频控制模式",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = Dimensions.SPACING_SMALL, vertical = Dimensions.SPACING_EXTRA_SMALL)
                                    )
                                    val cadenceMode by settingsViewModel.globalCadenceMode.collectAsState()
                                    LocationModeOption(
                                        title = "速度动态自适应 (AUTO)",
                                        description = "慢速行走 90~150 SPM，跑步阶段 150~200 SPM 自动线性拟合",
                                        selected = cadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO,
                                        onClick = { settingsViewModel.setGlobalCadenceMode(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "固定自定义步频 (CUSTOM)",
                                        description = "强制以设定的固定步频每分钟产生步数与加速度震颤",
                                        selected = cadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM,
                                        onClick = { settingsViewModel.setGlobalCadenceMode(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "关闭步频与传感器模拟 (OFF)",
                                        description = "不干预系统的加速度计与计步器原生硬件数据",
                                        selected = cadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.OFF,
                                        onClick = { settingsViewModel.setGlobalCadenceMode(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.OFF) }
                                    )

                                    if (cadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        val customSpm by settingsViewModel.globalCustomSpm.collectAsState()
                                        Text(
                                            text = "目标自定义步频: $customSpm SPM",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = Dimensions.SPACING_SMALL, vertical = Dimensions.SPACING_EXTRA_SMALL)
                                        )
                                        Slider(
                                            value = customSpm.toFloat(),
                                            onValueChange = { settingsViewModel.setGlobalCustomSpm(it.toInt()) },
                                            valueRange = 60f..220f,
                                            steps = 160,
                                            modifier = Modifier.padding(horizontal = Dimensions.SPACING_SMALL)
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    Text(
                                        text = "传感器模拟通道",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = Dimensions.SPACING_SMALL, vertical = Dimensions.SPACING_EXTRA_SMALL)
                                    )
                                    val cadenceChannel by settingsViewModel.globalCadenceChannel.collectAsState()
                                    LocationModeOption(
                                        title = "计步器 + 3D 加速度计 (ACCELEROMETER)",
                                        description = "合成三轴正弦运动重力摆动波形与计步事件，适配严格运动风控",
                                        selected = cadenceChannel == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.ACCELEROMETER,
                                        onClick = { settingsViewModel.setGlobalCadenceChannel(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.ACCELEROMETER) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "仅标准计步传感器 (STANDARD_STEP_SENSORS)",
                                        description = "仅更新 StepCounter 和 StepDetector，不合成震动波形",
                                        selected = cadenceChannel == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.STANDARD_STEP_SENSORS,
                                        onClick = { settingsViewModel.setGlobalCadenceChannel(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.STANDARD_STEP_SENSORS) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                        }
                        2 -> {
                            CategoryHeader(stringResource(R.string.category_map_service))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                MapServiceSettings(
                                    provider = settingsViewModel.mapProvider.collectAsState().value,
                                    onProviderSelected = settingsViewModel::setMapProvider,
                                    osmTileServer = settingsViewModel.osmTileServer.collectAsState().value,
                                    onOsmTileServerSelected = settingsViewModel::setOsmTileServer,
                                    baiduMapAk = settingsViewModel.baiduMapAk.collectAsState().value,
                                    amapWebKey = settingsViewModel.amapWebKey.collectAsState().value,
                                    amapSecurityCode = settingsViewModel.amapSecurityCode.collectAsState().value,
                                    googleMapsApiKey = settingsViewModel.googleMapsApiKey.collectAsState().value,
                                    onSaveBaidu = settingsViewModel::setBaiduMapAk,
                                    onSaveAmap = { key, code ->
                                        settingsViewModel.setAmapWebKey(key)
                                        settingsViewModel.setAmapSecurityCode(code)
                                    },
                                    onSaveGoogle = settingsViewModel::setGoogleMapsApiKey,
                                    onSaved = {
                                        cacheScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.setting_save_requested))
                                        }
                                    },
                                    onClearMapCache = {
                                        cacheScope.launch {
                                            val cleared = WebMapCache.clear(context)
                                            snackbarHostState.showSnackbar(
                                                context.getString(if (cleared) R.string.setting_map_cache_cleared
                                                    else R.string.setting_map_cache_failed)
                                            )
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                        }
                        3 -> {
                            CategoryHeader(stringResource(R.string.category_language))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                LanguageSettingItem(
                                    selectedLanguage = selectedLanguage,
                                    onLanguageSelected = { option ->
                                        settingsViewModel.setLanguageTag(option.tag)
                                        LocaleController.persistLanguageTag(context, option.tag)
                                        (context as? Activity)?.recreate()
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            CategoryHeader(stringResource(R.string.category_notifications))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    BooleanSettingItem(
                                        title = stringResource(R.string.setting_hide_toast_title),
                                        description = stringResource(R.string.setting_hide_toast_description),
                                        checked = settingsViewModel.hideFakeLocationToast.collectAsState().value,
                                        onCheckedChange = settingsViewModel::setHideFakeLocationToast
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                            CategoryHeader(stringResource(R.string.category_external_control))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.SPACING_SMALL),
                                shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                                border = BorderStroke(1.dp, SettingsOutline),
                                colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                            ) {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    BooleanSettingItem(
                                        title = stringResource(R.string.setting_external_broadcast_title),
                                        description = stringResource(R.string.setting_external_broadcast_description),
                                        checked = settingsViewModel.enableBroadcastControl.collectAsState().value,
                                        onCheckedChange = { newValue ->
                                            settingsViewModel.setEnableBroadcastControl(newValue)
                                            setControlReceiverEnabled(context, newValue)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                        }
                    }

                    // Keep the final setting reachable above the floating bottom navigation while
                    // allowing this page's background to continue underneath the bar.
                    Spacer(modifier = Modifier.height(112.dp))
                }
            }

            missingSystemScopePackages?.let { missingPackages ->
                AlertDialog(
                    onDismissRequest = { missingSystemScopePackages = null },
                    title = { Text(stringResource(R.string.system_hooks_scope_required_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.system_hooks_scope_required_message,
                                missingPackages.joinToString(", ")
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { missingSystemScopePackages = null }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
            if (targetAppScopeRequired) {
                AlertDialog(
                    onDismissRequest = { targetAppScopeRequired = false },
                    title = { Text(stringResource(R.string.system_hooks_target_app_required_title)) },
                    text = { Text(stringResource(R.string.system_hooks_target_app_required_message)) },
                    confirmButton = {
                        TextButton(onClick = { targetAppScopeRequired = false }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Home-screen variant of settings.  It keeps the real ViewModel-backed controls in the
 * prototype's bottom-sheet interaction instead of navigating away from the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismissRequest: () -> Unit,
    navController: NavController? = null,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = (screenHeight - 116.dp).coerceAtLeast(320.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val allSettings = SettingDefinitions.getSettings(settingsViewModel)
    val failedSaveRevision by settingsViewModel.failedSaveRevision.collectAsState()
    val categories = SettingDefinitions.getCategories()
    val selectedLanguage = LanguageOption.fromTag(settingsViewModel.languageTag.collectAsState().value)
    val snackbarHostState = remember { SnackbarHostState() }
    val cacheScope = rememberCoroutineScope()
    var missingSystemScopePackages by remember { mutableStateOf<List<String>?>(null) }
    var targetAppScopeRequired by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(0) }

    LaunchedEffect(settingsTab) { scrollState.scrollTo(0) }

    LaunchedEffect(Unit) {
        settingsViewModel.saveError.collect { failed ->
            if (failed) {
                snackbarHostState.showSnackbar(context.getString(R.string.setting_save_failed))
                settingsViewModel.clearSaveError()
            }
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.systemHooksEvents.collect { event ->
            when (event) {
                is SystemHooksEvent.UnsupportedSystemVersion ->
                    snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_unsupported_version))
                is SystemHooksEvent.ModuleNotActive ->
                    snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                is SystemHooksEvent.TargetAppScopeRequired ->
                    targetAppScopeRequired = true
                is SystemHooksEvent.ScopeSetupRequired ->
                    missingSystemScopePackages = event.missingPackages
                is SystemHooksEvent.RootPermissionRequired ->
                    snackbarHostState.showSnackbar(context.getString(R.string.target_apps_root_required))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MiuixPageBackground,
        scrimColor = Color.Transparent,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = Dimensions.SPACING_MEDIUM)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
                    .padding(bottom = Dimensions.SPACING_LARGE)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.screen_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF203F4C)
                    )
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFE4F0F3),
                        contentColor = Color(0xFF55717C)
                    ) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                }

                SettingsCategoryTabs(selectedIndex = settingsTab, onSelected = { settingsTab = it })
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    SettingsCategorySummary(selectedIndex = settingsTab)
                    when (settingsTab) {
                        0 -> {
                            SettingsSheetSection(stringResource(R.string.category_location_mode)) {
                                LocationModeSelector(
                                    selectedMode = settingsViewModel.locationMode.collectAsState().value,
                                    onModeSelected = settingsViewModel::selectLocationMode
                                )
                            }

                            categories.forEach { (category, settingsInCategory) ->
                                SettingsSheetSection(category) {
                                    settingsInCategory.forEachIndexed { index, settingTitle ->
                                        when (val setting = allSettings.find { it.title == settingTitle }) {
                                            is DoubleSettingData -> DoubleSettingComposable(setting, failedSaveRevision)
                                            is FloatSettingData -> FloatSettingComposable(setting, failedSaveRevision)
                                            null -> Unit
                                        }
                                        if (index != settingsInCategory.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            SettingsSheetSection("高级模拟子系统") {
                                Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                    LocationModeOption(
                                        title = "路线规划与跑道巡航 (Route Simulation)",
                                        description = "管理已收藏的巡航路线、一键生成标准 400 米环形跑道并启动模拟",
                                        selected = false,
                                        onClick = {
                                            onDismissRequest()
                                            navController?.navigate(Screen.Route.route)
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "环境全要素采集与模拟 (Virtual Environment)",
                                        description = "配置虚拟 AP 热点、4G/5G 蜂窝基站与蓝牙信标，支持现场一键真实无线电全要素采样",
                                        selected = false,
                                        onClick = {
                                            onDismissRequest()
                                            navController?.navigate(Screen.Environment.route)
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    LocationModeOption(
                                        title = "NFC 标签读写与虚拟派发 (NFC Simulation)",
                                        description = "读取实体 NFC 卡片 NDEF 记录，支持免贴卡向目标应用直接派发虚拟标签",
                                        selected = false,
                                        onClick = {
                                            onDismissRequest()
                                            navController?.navigate(Screen.Nfc.route)
                                        }
                                    )
                                }
                            }

                        }
                        2 -> {
                            SettingsSheetSection(stringResource(R.string.category_map_service)) {
                                MapServiceSettings(
                                    provider = settingsViewModel.mapProvider.collectAsState().value,
                                    onProviderSelected = settingsViewModel::setMapProvider,
                                    osmTileServer = settingsViewModel.osmTileServer.collectAsState().value,
                                    onOsmTileServerSelected = settingsViewModel::setOsmTileServer,
                                    baiduMapAk = settingsViewModel.baiduMapAk.collectAsState().value,
                                    amapWebKey = settingsViewModel.amapWebKey.collectAsState().value,
                                    amapSecurityCode = settingsViewModel.amapSecurityCode.collectAsState().value,
                                    googleMapsApiKey = settingsViewModel.googleMapsApiKey.collectAsState().value,
                                    onSaveBaidu = settingsViewModel::setBaiduMapAk,
                                    onSaveAmap = { key, code ->
                                        settingsViewModel.setAmapWebKey(key)
                                        settingsViewModel.setAmapSecurityCode(code)
                                    },
                                    onSaveGoogle = settingsViewModel::setGoogleMapsApiKey,
                                    onSaved = {
                                        cacheScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.setting_save_requested))
                                        }
                                    },
                                    onClearMapCache = {
                                        cacheScope.launch {
                                            val cleared = WebMapCache.clear(context)
                                            snackbarHostState.showSnackbar(context.getString(
                                                if (cleared) R.string.setting_map_cache_cleared
                                                else R.string.setting_map_cache_failed))
                                        }
                                    }
                                )
                            }
                        }
                        3 -> {
                            SettingsSheetSection(stringResource(R.string.category_language)) {
                                LanguageSettingItem(
                                    selectedLanguage = selectedLanguage,
                                    onLanguageSelected = { option ->
                                        settingsViewModel.setLanguageTag(option.tag)
                                        LocaleController.persistLanguageTag(context, option.tag)
                                        (context as? Activity)?.recreate()
                                    }
                                )
                            }
                            SettingsSheetSection(stringResource(R.string.category_notifications)) {
                                BooleanSettingItem(
                                    title = stringResource(R.string.setting_hide_toast_title),
                                    description = stringResource(R.string.setting_hide_toast_description),
                                    checked = settingsViewModel.hideFakeLocationToast.collectAsState().value,
                                    onCheckedChange = settingsViewModel::setHideFakeLocationToast
                                )
                            }
                            SettingsSheetSection(stringResource(R.string.category_external_control)) {
                                BooleanSettingItem(
                                    title = stringResource(R.string.setting_external_broadcast_title),
                                    description = stringResource(R.string.setting_external_broadcast_description),
                                    checked = settingsViewModel.enableBroadcastControl.collectAsState().value,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.setEnableBroadcastControl(enabled)
                                        setControlReceiverEnabled(context, enabled)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        missingSystemScopePackages?.let { missingPackages ->
            AlertDialog(
                onDismissRequest = { missingSystemScopePackages = null },
                title = { Text(stringResource(R.string.system_hooks_scope_required_title)) },
                text = { Text(stringResource(R.string.system_hooks_scope_required_message, missingPackages.joinToString(", "))) },
                confirmButton = {
                    TextButton(onClick = { missingSystemScopePackages = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            )
        }
        if (targetAppScopeRequired) {
            AlertDialog(
                onDismissRequest = { targetAppScopeRequired = false },
                title = { Text(stringResource(R.string.system_hooks_target_app_required_title)) },
                text = { Text(stringResource(R.string.system_hooks_target_app_required_message)) },
                confirmButton = {
                    TextButton(onClick = { targetAppScopeRequired = false }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCategoryTabs(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val labels = listOf(
        R.string.settings_group_location,
        R.string.settings_group_motion,
        R.string.settings_group_map,
        R.string.settings_group_general
    )
    val icons = listOf(Icons.Default.MyLocation, Icons.Default.DirectionsWalk, Icons.Default.Map, Icons.Default.Settings)
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)),
        containerColor = MiuixCardSurface,
        contentColor = SettingsAccent,
        divider = {}
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                selectedContentColor = SettingsAccent,
                unselectedContentColor = Color(0xFF526870),
                text = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(icons[index], contentDescription = null) }
            )
        }
    }
}

@Composable
private fun SettingsCategorySummary(selectedIndex: Int) {
    val summaries = listOf(
        R.string.settings_group_location_summary,
        R.string.settings_group_motion_summary,
        R.string.settings_group_map_summary,
        R.string.settings_group_general_summary
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE4F3ED)
    ) {
        Text(
            text = stringResource(summaries[selectedIndex]),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF24554C),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SettingsSheetSection(
    title: String,
    content: @Composable () -> Unit
) {
    CategoryHeader(title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimensions.SPACING_MEDIUM),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SettingsOutline),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { content() }
}

@Composable
private fun MapServiceSettings(
    provider: MapProvider,
    onProviderSelected: (MapProvider) -> Unit,
    osmTileServer: OsmTileServer,
    onOsmTileServerSelected: (OsmTileServer) -> Unit,
    baiduMapAk: String,
    amapWebKey: String,
    amapSecurityCode: String,
    googleMapsApiKey: String,
    onSaveBaidu: (String) -> Unit,
    onSaveAmap: (String, String) -> Unit,
    onSaveGoogle: (String) -> Unit,
    onSaved: () -> Unit,
    onClearMapCache: () -> Unit
) {
    Column {
        MapProviderSelector(provider, onProviderSelected)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Dimensions.SPACING_MEDIUM),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        when (provider) {
            MapProvider.OSMDROID -> Column(Modifier.padding(Dimensions.SPACING_MEDIUM)) {
                Text(stringResource(R.string.map_osmdroid_description), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.setting_osm_tile_server_title),
                    modifier = Modifier.padding(top = Dimensions.SPACING_MEDIUM),
                    style = MaterialTheme.typography.titleMedium)
                LocationModeOption(
                    title = stringResource(R.string.osm_tile_server_international),
                    description = stringResource(R.string.osm_tile_server_international_description),
                    selected = osmTileServer == OsmTileServer.OPENSTREETMAP,
                    onClick = { onOsmTileServerSelected(OsmTileServer.OPENSTREETMAP) }
                )
                LocationModeOption(
                    title = stringResource(R.string.osm_tile_server_domestic),
                    description = stringResource(R.string.osm_tile_server_domestic_description),
                    selected = osmTileServer == OsmTileServer.AUTONAVI,
                    onClick = { onOsmTileServerSelected(OsmTileServer.AUTONAVI) }
                )
            }
            MapProvider.BAIDU -> BaiduMapAkSetting(baiduMapAk, onSaveBaidu, onSaved)
            MapProvider.AMAP -> AmapCredentialSetting(
                amapWebKey,
                amapSecurityCode,
                onSaveAmap,
                onSaved
            )
            MapProvider.GOOGLE -> GoogleCredentialSetting(
                googleMapsApiKey,
                onSaveGoogle,
                onSaved
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Dimensions.SPACING_MEDIUM),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        MapCacheSetting(onClearMapCache)
    }
}

@Composable
private fun MapProviderSelector(
    provider: MapProvider,
    onProviderSelected: (MapProvider) -> Unit
) {
    Column(modifier = Modifier.padding(Dimensions.SPACING_MEDIUM)) {
        Text(
            text = stringResource(R.string.setting_map_provider_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setting_map_provider_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_MEDIUM),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_SMALL)
        ) {
            MapProvider.entries.forEach { option ->
                val selected = option == provider
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onProviderSelected(option) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = stringResource(
                            when (option) {
                                MapProvider.OSMDROID -> R.string.map_provider_osmdroid
                                MapProvider.BAIDU -> R.string.map_provider_baidu
                                MapProvider.AMAP -> R.string.map_provider_amap
                                MapProvider.GOOGLE -> R.string.map_provider_google
                            }
                        ),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BaiduMapAkSetting(
    baiduMapAk: String,
    onSave: (String) -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var draftAk by remember(baiduMapAk) { mutableStateOf(baiduMapAk) }

    Column(modifier = Modifier.padding(Dimensions.SPACING_MEDIUM)) {
        Text(
            text = stringResource(R.string.setting_map_ak_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setting_map_ak_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        OutlinedTextField(
            value = draftAk,
            onValueChange = { draftAk = it },
            label = { Text(stringResource(R.string.setting_map_ak_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_MEDIUM)
        )
        Text(
            text = stringResource(R.string.setting_map_ak_apply_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Dimensions.SPACING_SMALL)
        )
        Row(
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.setting_map_ak_apply_step_one),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.setting_map_ak_open_console),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Dimensions.SPACING_SMALL)
                    .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BAIDU_AK_CONSOLE_URL)))
                }
            )
        }
        Text(
            text = stringResource(R.string.setting_map_ak_apply_step_two),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_SMALL)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        ) {
            Text(
                text = stringResource(R.string.setting_map_ak_apply_step_three),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = BAIDU_MAP_REFERER_HOST,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(
                            ClipData.newPlainText("Baidu Maps Referer host", BAIDU_MAP_REFERER_HOST)
                        )
                }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_EXTRA_SMALL),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(R.string.setting_map_ak_save),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickable {
                    onSave(draftAk.trim())
                    onSaved()
                }
            )
        }
    }

}

@Composable
private fun AmapCredentialSetting(
    webKey: String,
    securityCode: String,
    onSave: (String, String) -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var draftKey by remember(webKey) { mutableStateOf(webKey) }
    var draftSecurityCode by remember(securityCode) { mutableStateOf(securityCode) }
    CredentialSection(
        title = stringResource(R.string.setting_amap_credentials_title),
        description = stringResource(R.string.setting_amap_credentials_description),
        fields = listOf(
            CredentialField(stringResource(R.string.setting_amap_web_key), draftKey) {
                draftKey = it
            },
            CredentialField(stringResource(R.string.setting_amap_security_code), draftSecurityCode) {
                draftSecurityCode = it
            }
        ),
        onOpenConsole = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AMAP_CONSOLE_URL))) },
        onSave = {
            onSave(draftKey.trim(), draftSecurityCode.trim())
            onSaved()
        }
    )
}

@Composable
private fun GoogleCredentialSetting(
    apiKey: String,
    onSave: (String) -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var draftKey by remember(apiKey) { mutableStateOf(apiKey) }
    CredentialSection(
        title = stringResource(R.string.setting_google_credentials_title),
        description = stringResource(R.string.setting_google_credentials_description),
        fields = listOf(
            CredentialField(stringResource(R.string.setting_google_api_key), draftKey) {
                draftKey = it
            }
        ),
        onOpenConsole = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_MAPS_CONSOLE_URL)))
        },
        onSave = {
            onSave(draftKey.trim())
            onSaved()
        }
    )
}

private data class CredentialField(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@Composable
private fun CredentialSection(
    title: String,
    description: String,
    fields: List<CredentialField>,
    onOpenConsole: () -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.padding(Dimensions.SPACING_MEDIUM)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        fields.forEach { field ->
            OutlinedTextField(
                value = field.value,
                onValueChange = field.onValueChange,
                label = { Text(field.label) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimensions.SPACING_SMALL)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_SMALL),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(R.string.setting_map_open_console),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 4.dp)
                    .clickable(onClick = onOpenConsole)
            )
            Text(
                text = stringResource(R.string.setting_map_ak_save),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(onClick = onSave)
            )
        }
    }
}

@Composable
private fun MapCacheSetting(onClearMapCache: () -> Unit) {
    Column(modifier = Modifier.padding(Dimensions.SPACING_MEDIUM)) {
        Text(
            text = stringResource(R.string.setting_map_cache_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setting_map_cache_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_EXTRA_SMALL),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(R.string.setting_map_cache_clear),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickable(onClick = onClearMapCache)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSettingItem(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.setting_language_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                    )
                }
                Text(
                    text = stringResource(selectedLanguage.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                LanguageOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(option.labelRes),
                                    modifier = Modifier.weight(1f)
                                )
                                if (option == selectedLanguage) {
                                    Text(
                                        text = "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onLanguageSelected(option)
                        }
                    )
                }
            }
        }
    }
}

private fun setControlReceiverEnabled(context: android.content.Context, enabled: Boolean) {
    val component = ComponentName(context, ControlReceiver::class.java)
    val newState = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    context.packageManager.setComponentEnabledSetting(
        component,
        newState,
        PackageManager.DONT_KILL_APP
    )
}

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF617570),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, bottom = Dimensions.SPACING_SMALL)
    )
}

@Composable
private fun LocationModeSelector(
    selectedMode: LocationMode,
    onModeSelected: (LocationMode) -> Unit
) {
    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
        LocationModeOption(
            title = stringResource(R.string.location_mode_application_title),
            description = stringResource(R.string.location_mode_application_description),
            selected = selectedMode == LocationMode.APPLICATION_HOOK,
            onClick = { onModeSelected(LocationMode.APPLICATION_HOOK) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LocationModeOption(
            title = stringResource(R.string.location_mode_system_title),
            description = stringResource(R.string.location_mode_system_description),
            selected = selectedMode == LocationMode.SYSTEM_HOOK,
            onClick = { onModeSelected(LocationMode.SYSTEM_HOOK) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LocationModeOption(
            title = stringResource(R.string.location_mode_global_title),
            description = stringResource(R.string.location_mode_global_description),
            selected = selectedMode == LocationMode.GLOBAL_HOOK,
            onClick = { onModeSelected(LocationMode.GLOBAL_HOOK) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LocationModeOption(
            title = stringResource(R.string.location_mode_mock_provider_title),
            description = stringResource(R.string.location_mode_mock_provider_description),
            selected = selectedMode == LocationMode.MOCK_PROVIDER,
            onClick = { onModeSelected(LocationMode.MOCK_PROVIDER) }
        )
    }
}

@Composable
private fun LocationModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimensions.SPACING_MEDIUM),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(end = Dimensions.SPACING_SMALL)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
            )
        }
    }
}

@Composable
fun BooleanSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Reserve breathing room before the switch so explanatory copy never appears to
            // run into the control on the right.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimensions.SPACING_MEDIUM)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (checked) disableDescription else enableDescription
                }
            )
        }
    }
}

@Composable
fun DoubleSettingItem(
    title: String,
    description: String,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    step: Float,
    resetToken: Long = 0L
) {
    SettingItem(
        title = title,
        description = description,
        useValue = useValue,
        onUseValueChange = onUseValueChange,
        value = value,
        onValueChange = onValueChange,
        label = label,
        unit = unit,
        minValue = minValue,
        maxValue = maxValue,
        step = step,
        resetToken = resetToken,
        valueFormatter = { "%.2f".format(it) },
        parseValue = { it.toDouble() }
    )
}

@Composable
fun FloatSettingItem(
    title: String,
    description: String,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    step: Float,
    resetToken: Long = 0L
) {
    SettingItem(
        title = title,
        description = description,
        useValue = useValue,
        onUseValueChange = onUseValueChange,
        value = value,
        onValueChange = onValueChange,
        label = label,
        unit = unit,
        minValue = minValue,
        maxValue = maxValue,
        step = step,
        resetToken = resetToken,
        valueFormatter = { "%.2f".format(it) },
        parseValue = { it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Number> SettingItem(
    title: String,
    description: String,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: T,
    onValueChange: (T) -> Unit,
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    step: Float,
    valueFormatter: (T) -> String,
    parseValue: (Float) -> T,
    resetToken: Long = 0L
) {
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)
    val adjustDescription = stringResource(R.string.setting_adjust_value, title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Keep the same text-to-control gap for value settings as for simple switches.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimensions.SPACING_MEDIUM)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                )
            }

            Switch(
                checked = useValue,
                onCheckedChange = onUseValueChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (useValue) disableDescription else enableDescription
                }
            )
        }

        if (useValue) {
            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

            var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }
            var showExactValue by remember { mutableStateOf(false) }

            LaunchedEffect(value, resetToken) {
                if (sliderValue != value.toFloat()) sliderValue = value.toFloat()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_SMALL),
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayText = stringResource(
                    R.string.setting_value_display,
                    label,
                    valueFormatter(parseValue(sliderValue)),
                    unit
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showExactValue = !showExactValue }
                )

                OutlinedIconButton(
                    onClick = {
                        val newValue = (sliderValue - step).coerceAtLeast(minValue)
                        sliderValue = newValue
                        onValueChange(parseValue(newValue))
                    },
                    enabled = sliderValue > minValue,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "−",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedIconButton(
                    onClick = {
                        val newValue = (sliderValue + step).coerceAtMost(maxValue)
                        sliderValue = newValue
                        onValueChange(parseValue(newValue))
                    },
                    enabled = sliderValue < maxValue,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "+",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.SPACING_SMALL)
            ) {
                Text(
                    text = "${minValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${maxValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { newValue -> sliderValue = newValue },
                onValueChangeFinished = { onValueChange(parseValue(sliderValue)) },
                valueRange = minValue..maxValue,
                steps = ((maxValue - minValue) / step).toInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = adjustDescription }
            )
        }
    }
}

sealed class SettingData {
    abstract val title: String
    abstract val description: String
    abstract val useValueState: State<Boolean>
    abstract val setUseValue: (Boolean) -> Unit
    abstract val label: String
    abstract val unit: String
    abstract val minValue: Float
    abstract val maxValue: Float
    abstract val step: Float
}

data class DoubleSettingData(
    override val title: String,
    override val description: String,
    override val useValueState: State<Boolean>,
    val valueState: State<Double>,
    override val setUseValue: (Boolean) -> Unit,
    val setValue: (Double) -> Unit,
    override val label: String,
    override val unit: String,
    override val minValue: Float,
    override val maxValue: Float,
    override val step: Float
) : SettingData()

data class FloatSettingData(
    override val title: String,
    override val description: String,
    override val useValueState: State<Boolean>,
    val valueState: State<Float>,
    override val setUseValue: (Boolean) -> Unit,
    val setValue: (Float) -> Unit,
    override val label: String,
    override val unit: String,
    override val minValue: Float,
    override val maxValue: Float,
    override val step: Float
) : SettingData()

@Composable
fun DoubleSettingComposable(setting: DoubleSettingData, resetToken: Long = 0L) {
    DoubleSettingItem(
        title = setting.title,
        description = setting.description,
        useValue = setting.useValueState.value,
        onUseValueChange = setting.setUseValue,
        value = setting.valueState.value,
        onValueChange = setting.setValue,
        label = setting.label,
        unit = setting.unit,
        minValue = setting.minValue,
        maxValue = setting.maxValue,
        step = setting.step,
        resetToken = resetToken
    )
}

@Composable
fun FloatSettingComposable(setting: FloatSettingData, resetToken: Long = 0L) {
    FloatSettingItem(
        title = setting.title,
        description = setting.description,
        useValue = setting.useValueState.value,
        onUseValueChange = setting.setUseValue,
        value = setting.valueState.value,
        onValueChange = setting.setValue,
        label = setting.label,
        unit = setting.unit,
        minValue = setting.minValue,
        maxValue = setting.maxValue,
        step = setting.step,
        resetToken = resetToken
    )
}
