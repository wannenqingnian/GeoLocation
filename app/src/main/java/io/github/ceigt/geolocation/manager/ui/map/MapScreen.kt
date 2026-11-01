package io.github.ceigt.geolocation.manager.ui.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Close
import io.github.ceigt.geolocation.manager.ui.navigation.Screen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.MapProvider
import io.github.ceigt.geolocation.data.OsmTileServer
import io.github.ceigt.geolocation.data.model.FavoriteLocation
import io.github.ceigt.geolocation.manager.App
import io.github.ceigt.geolocation.manager.ui.map.components.MapViewContainer
import io.github.ceigt.geolocation.manager.ui.favorites.FavoritesViewModel
import io.github.ceigt.geolocation.data.model.mode.LocationMode
import io.github.ceigt.geolocation.manager.ui.settings.SettingsBottomSheet
import io.github.ceigt.geolocation.manager.ui.settings.SettingsViewModel
import io.github.ceigt.geolocation.manager.ui.targetapps.TargetAppsBottomSheet
import io.github.ceigt.geolocation.BuildConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The HTML prototype uses solid, quiet surfaces. Keeping map controls fully opaque also avoids
// Web-map tile seams becoming visible through Compose overlays.
private val MapOverlaySurfaceColor = Color(0xFFFFFFFF)
private val MapOverlayBorderColor = Color(0xFFD8E7EB)
// Top controls sit directly on the map: use a quiet translucent surface and a hairline border
// instead of an elevation shadow, so the map remains the visual ground plane.
private val MapTopControlSurfaceColor = Color(0xF2FFFFFF)
private const val TELEGRAM_CHANNEL_URL = "https://github.com/ceigt/GeoLocation"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val joystickController = remember { io.github.ceigt.geolocation.manager.route.JoystickOverlayController(context) }
    var isJoystickActive by remember { mutableStateOf(false) }
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val xposedService by App.serviceState.collectAsStateWithLifecycle()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val locationMode by settingsViewModel.locationMode.collectAsStateWithLifecycle()
    val baiduMapAk by settingsViewModel.baiduMapAk.collectAsStateWithLifecycle()
    val mapProvider by settingsViewModel.mapProvider.collectAsStateWithLifecycle()
    val osmTileServer by settingsViewModel.osmTileServer.collectAsStateWithLifecycle()
    val amapWebKey by settingsViewModel.amapWebKey.collectAsStateWithLifecycle()
    val amapSecurityCode by settingsViewModel.amapSecurityCode.collectAsStateWithLifecycle()
    val googleMapsApiKey by settingsViewModel.googleMapsApiKey.collectAsStateWithLifecycle()
    val isPlaying = uiState.isPlaying
    val isFabClickable = uiState.isFabClickable
    val showAddToFavoritesDialog = uiState.addToFavoritesDialogState == DialogState.Visible
    var showOptionsMenu by remember { mutableStateOf(false) }

    val myLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasLoc = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLoc) {
            mapViewModel.triggerCenterMapEvent()
        } else {
            Toast.makeText(context, "未授予定位权限，无法获取当前位置", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }
    var showFavoritesPanel by remember { mutableStateOf(false) }
    var favoritePendingDeletion by remember { mutableStateOf<FavoriteLocation?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSponsorSheet by remember { mutableStateOf(false) }
    var showTargetAppsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var saveRouteName by remember { mutableStateOf("") }
    val hasMapModal =
        showAddToFavoritesDialog || showAboutDialog || showSponsorSheet || showTargetAppsSheet ||
            showSettingsSheet || uiState.showMapQuotaDialog || favoritePendingDeletion != null
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)
    LaunchedEffect(mapViewModel) {
        mapViewModel.saveError.collect { failed ->
            if (failed) {
                snackbarHostState.showSnackbar(context.getString(R.string.setting_save_failed))
                mapViewModel.clearSaveError()
            }
        }
    }
    LaunchedEffect(favoritesViewModel) {
        favoritesViewModel.saveError.collect { failed ->
            if (failed) {
                snackbarHostState.showSnackbar(context.getString(R.string.setting_save_failed))
                favoritesViewModel.clearSaveError()
            }
        }
    }
    LaunchedEffect(uiState.mockFailed) {
        if (uiState.mockFailed) snackbarHostState.showSnackbar(context.getString(R.string.mock_provider_start_failed))
    }
    val dismissSearch = {
        focusManager.clearFocus(force = true)
        mapViewModel.clearPlaceSearch()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF4FAF8),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MapViewContainer(
                    mapViewModel = mapViewModel,
                    mapProvider = mapProvider,
                    osmTileServer = osmTileServer,
                    baiduMapAk = baiduMapAk,
                    amapWebKey = amapWebKey,
                    amapSecurityCode = amapSecurityCode,
                    googleMapsApiKey = googleMapsApiKey,
                    onMapInteraction = {
                        dismissSearch()
                        showOptionsMenu = false
                        showFavoritesPanel = false
                    }
                )
                // Visual-only readability layer. It intentionally has no pointer input, so map
                // pan and zoom gestures continue through this overlay to the WebView map.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x30F7FDFD),
                                    Color(0xD8F7FDFD),
                                    Color(0xFFF7FDFD)
                                )
                            )
                        )
                )
                MapTopControls(
                    query = uiState.placeSearchQuery,
                    isLoading = uiState.isPlaceSearchLoading,
                    results = uiState.placeSearchResults,
                    errorMessageRes = uiState.placeSearchErrorMessageRes,
                    onQueryChange = mapViewModel::updatePlaceSearchQuery,
                    onSearch = mapViewModel::searchPlace,
                    onClear = mapViewModel::clearPlaceSearch,
                    onResultClick = mapViewModel::selectPlaceSearchResult,
                    showOptionsMenu = showOptionsMenu,
                    onShowOptionsMenuChange = { showOptionsMenu = it },
                    onCenterMap = mapViewModel::triggerCenterMapEvent,
                    onAddFavorite = mapViewModel::showAddToFavoritesDialog,
                    favorites = favorites,
                    showFavoritesPanel = showFavoritesPanel,
                    onShowFavoritesPanelChange = { showFavoritesPanel = it },
                    onSelectFavorite = { favorite ->
                        favoritesViewModel.selectFavorite(favorite)
                        showFavoritesPanel = false
                    },
                    onRemoveFavorite = { favorite -> favoritePendingDeletion = favorite },
                    onOpenTargetApps = { showTargetAppsSheet = true },
                    onOpenSettings = { showSettingsSheet = true },
                    onClearLocation = { mapViewModel.updateClickedLocation(null) },
                    clearLocationEnabled = isFabClickable,
                    onShowSponsor = { showSponsorSheet = true },
                    onOpenTelegram = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHANNEL_URL))
                        )
                    },
                    onShowAbout = { showAboutDialog = true },
                    onDismissSearch = dismissSearch,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 0.dp, end = 18.dp)
                )
                if (
                    xposedService == null &&
                    locationMode != LocationMode.MOCK_PROVIDER &&
                    locationMode != LocationMode.ROOT_MODE &&
                    !showOptionsMenu &&
                    !showFavoritesPanel &&
                    uiState.placeSearchResults.isEmpty() &&
                    uiState.placeSearchErrorMessageRes == null
                ) {
                    ModuleInactiveBanner(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(start = 18.dp, top = 72.dp, end = 18.dp)
                    )
                }
                if (uiState.isRoutePlanningMode) {
                    RoutePlanningPanel(
                        uiState = uiState,
                        mapViewModel = mapViewModel,
                        onSaveRoute = { showSaveRouteDialog = true },
                        onOpenRouteLibrary = { navController?.navigate(Screen.Route.route) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                } else {
                    if (uiState.placeSearchResults.isEmpty() && uiState.placeSearchErrorMessageRes == null) {
                        MapLocationInfo(
                            lastClickedLocation = uiState.lastClickedLocation,
                            selectedLocationAddress = uiState.selectedLocationAddress,
                            selectedLocationPoiTitle = uiState.selectedLocationPoiTitle,
                            isAddressLoading = uiState.isSelectedLocationAddressLoading,
                            addressMessageRes = uiState.selectedLocationAddressMessageRes,
                            isPlaying = isPlaying,
                            modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 164.dp)
                        )
                    }
                    if (!hasMapModal) {
                    val rootRunning = locationMode == LocationMode.ROOT_MODE && isPlaying
                    val displayStatus = if (rootRunning) "Root 模式: 运行中 (系统底层注入)" else uiState.hookStatus
                    displayStatus?.let { status ->
                        Text(status, modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 154.dp)
                            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                            .padding(6.dp), fontSize = 12.sp, color = Color(0xFF334155))
                    }
                    MapPlayButton(
                        isPlaying = isPlaying,
                        isStarting = uiState.isStarting,
                        enabled = isFabClickable,
                        onClick = {
                            dismissSearch()
                            val wasPlaying = uiState.isPlaying || uiState.isStarting
                            if (!wasPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            mapViewModel.togglePlaying()
                            if (locationMode != LocationMode.MOCK_PROVIDER || wasPlaying) scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (!wasPlaying) context.getString(R.string.simulation_request_submitted) else fakeLocationUnset
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 30.dp)
                    )
                    }
                }
                MapTargetAppsButton(
                    onClick = { showTargetAppsSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 42.dp, bottom = 47.dp)
                )
                MapMyLocationButton(
                    onClick = {
                        dismissSearch()
                        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLocation) {
                            mapViewModel.triggerCenterMapEvent()
                        } else {
                            myLocationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 42.dp, bottom = 47.dp)
                )

                // Environment Snapshot Manager Quick Button
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color(0xFF006C5F),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 42.dp, bottom = 192.dp)
                        .size(46.dp)
                        .clickable {
                            navController?.navigate(Screen.Environment.route)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "环境全要素采集与模拟",
                            tint = Color(0xFF006C5F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Route Simulation Manager Quick Button
                val isPlanning = uiState.isRoutePlanningMode
                Surface(
                    shape = CircleShape,
                    color = if (isPlanning) Color(0xFF006C5F) else Color.White,
                    contentColor = if (isPlanning) Color.White else Color(0xFF006C5F),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 42.dp, bottom = 120.dp)
                        .size(46.dp)
                        .clickable {
                            mapViewModel.toggleRoutePlanningMode()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "路线规划与跑道模拟",
                            tint = if (isPlanning) Color.White else Color(0xFF006C5F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Return to Mock Location Point Button
                MapMockLocationButton(
                    onClick = {
                        dismissSearch()
                        uiState.lastClickedLocation?.let { p ->
                            mapViewModel.goToPoint(p.latitude, p.longitude)
                        } ?: scope.launch {
                            snackbarHostState.showSnackbar("尚未选定模拟定位点")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 42.dp, bottom = 120.dp)
                )

                // Floating Joystick toggle button
                Surface(
                    shape = CircleShape,
                    color = if (isJoystickActive) Color(0xFF006C5F) else Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 42.dp, bottom = 192.dp)
                        .size(46.dp)
                        .clickable {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("请先授予显示在其他应用上方的悬浮窗权限")
                                }
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                return@clickable
                            }
                            if (isJoystickActive) {
                                joystickController.hide()
                                isJoystickActive = false
                                io.github.ceigt.geolocation.manager.route.RouteSimulationService.stopService(context)
                            } else {
                                // Start route service with manual joystick mode
                                val curLoc = uiState.lastClickedLocation
                                if (curLoc != null) {
                                    val wp = io.github.ceigt.geolocation.data.model.route.RouteWaypoint(
                                        name = "摇杆起点",
                                        latitude = curLoc.latitude,
                                        longitude = curLoc.longitude
                                    )
                                    io.github.ceigt.geolocation.manager.route.RouteSimulationService.engine.start(
                                        waypoints = listOf(wp),
                                        runMode = io.github.ceigt.geolocation.data.model.route.RouteRunMode.MANUAL
                                    )
                                    io.github.ceigt.geolocation.manager.route.RouteSimulationService.startService(context)
                                }
                                joystickController.show()
                                isJoystickActive = true
                                scope.launch {
                                    snackbarHostState.showSnackbar("悬浮窗摇杆已激活并启动运动推进引擎")
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "摇杆悬浮窗",
                            tint = if (isJoystickActive) Color.White else Color(0xFF006C5F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (mapProvider == MapProvider.OSMDROID) {
                    Text(
                        if (osmTileServer == OsmTileServer.OPENSTREETMAP) "© OpenStreetMap contributors" else "© 高德地图",
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse(if (osmTileServer == OsmTileServer.OPENSTREETMAP)
                                        "https://www.openstreetmap.org/copyright" else "https://www.amap.com/")))
                            },
                        fontSize = 11.sp,
                        color = Color(0xFF334155)
                    )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 159.dp),
                    snackbar = { data ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xEE173E4D),
                            contentColor = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }
                    }
                )
            }
        }

        if (showAddToFavoritesDialog) {
            val lastClickedLocation = uiState.lastClickedLocation

            LaunchedEffect(lastClickedLocation) {
                mapViewModel.prefillCoordinatesFromMarker(
                    lastClickedLocation?.latitude,
                    lastClickedLocation?.longitude
                )
            }

            AddToFavoritesBottomSheet(
                mapViewModel = mapViewModel,
                onDismissRequest = { mapViewModel.hideAddToFavoritesDialog() },
                onAddFavorite = { name, address, latitude, longitude ->
                    val favorite = FavoriteLocation(name, latitude, longitude, address)
                    mapViewModel.addFavoriteLocation(favorite)
                    mapViewModel.hideAddToFavoritesDialog()
                }
            )
        }

    if (showAboutDialog) {
        GeoLocationAboutSheet(
            onDismissRequest = { showAboutDialog = false }
        )
    }
    if (showTargetAppsSheet) {
        TargetAppsBottomSheet(onDismissRequest = { showTargetAppsSheet = false })
    }
    if (showSettingsSheet) {
        SettingsBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            navController = navController
        )
    }
    if (uiState.showMapQuotaDialog) {
        BaiduMapQuotaDialog(
            onDismiss = mapViewModel::dismissMapQuotaDialog,
            onOpenMapService = {
                mapViewModel.dismissMapQuotaDialog()
                showSettingsSheet = true
            }
        )
    }
    if (showSaveRouteDialog) {
        AlertDialog(
            onDismissRequest = { showSaveRouteDialog = false },
            title = { Text("收藏当前规划路线", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = saveRouteName,
                    onValueChange = { saveRouteName = it },
                    label = { Text("路线名称") },
                    placeholder = { Text("例: 晨跑操场路线") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    mapViewModel.saveCurrentRouteFavorite(saveRouteName) {
                        scope.launch { snackbarHostState.showSnackbar("路线「$saveRouteName」已成功收藏！") }
                    }
                    showSaveRouteDialog = false
                    saveRouteName = ""
                }) {
                    Text("收藏保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRouteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    favoritePendingDeletion?.let { favorite ->
        AlertDialog(
            onDismissRequest = { favoritePendingDeletion = null },
            title = { Text(stringResource(R.string.favorites_delete_title)) },
            text = { Text(stringResource(R.string.favorites_delete_message, favorite.name)) },
            confirmButton = {
                TextButton(onClick = {
                    favoritesViewModel.removeFavorite(favorite)
                    favoritePendingDeletion = null
                }) {
                    Text(stringResource(R.string.action_delete), color = Color(0xFFC13B42))
                }
            },
            dismissButton = {
                TextButton(onClick = { favoritePendingDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun BaiduMapQuotaDialog(
    onDismiss: () -> Unit,
    onOpenMapService: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFBFDFC),
            shadowElevation = 18.dp
        ) {
            Column(modifier = Modifier.padding(start = 28.dp, top = 28.dp, end = 28.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFF2D6),
                        contentColor = Color(0xFFA86D13)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.map_quota_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C2927),
                        modifier = Modifier.padding(start = 13.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.map_quota_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF52615E),
                    modifier = Modifier.padding(top = 22.dp)
                )
                Text(
                    text = stringResource(R.string.map_quota_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF52615E),
                    modifier = Modifier.padding(top = 12.dp)
                )
                HorizontalDivider(
                    color = Color(0xFFE1E8E5),
                    modifier = Modifier.padding(top = 23.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.map_quota_dialog_dismiss))
                    }
                    TextButton(onClick = onOpenMapService) {
                        Text(
                            text = stringResource(R.string.map_quota_dialog_open_settings),
                            color = Color(0xFF28766A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleInactiveBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE67C68), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFE8E3),
        contentColor = Color(0xFF8B2E20),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.module_inactive_banner),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MapTopControls(
    query: String,
    isLoading: Boolean,
    results: List<PlaceSearchResult>,
    errorMessageRes: Int?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onResultClick: (PlaceSearchResult) -> Unit,
    showOptionsMenu: Boolean,
    onShowOptionsMenuChange: (Boolean) -> Unit,
    onCenterMap: () -> Unit,
    onAddFavorite: () -> Unit,
    favorites: List<FavoriteLocation>,
    showFavoritesPanel: Boolean,
    onShowFavoritesPanelChange: (Boolean) -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onRemoveFavorite: (FavoriteLocation) -> Unit,
    onOpenTargetApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearLocation: () -> Unit,
    clearLocationEnabled: Boolean,
    onShowSponsor: () -> Unit,
    onOpenTelegram: () -> Unit,
    onShowAbout: () -> Unit,
    onDismissSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        MapSearchPanel(
            query = query,
            isLoading = isLoading,
            results = results,
            errorMessageRes = errorMessageRes,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onResultClick = onResultClick,
            modifier = Modifier.fillMaxWidth(),
            searchFieldModifier = Modifier
                .fillMaxWidth()
                .padding(end = 132.dp)
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MapTopControlSurfaceColor,
                contentColor = Color(0xFF174150),
                border = androidx.compose.foundation.BorderStroke(1.dp, MapOverlayBorderColor)
            ) {
                IconButton(onClick = {
                    onDismissSearch()
                    onShowOptionsMenuChange(false)
                    onShowFavoritesPanelChange(!showFavoritesPanel)
                }) {
                    Icon(Icons.Outlined.StarBorder, contentDescription = stringResource(R.string.screen_favorites))
                }
            }
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MapTopControlSurfaceColor,
                contentColor = Color(0xFF174150),
                border = androidx.compose.foundation.BorderStroke(1.dp, MapOverlayBorderColor)
            ) {
                IconButton(onClick = {
                    onDismissSearch()
                    onShowOptionsMenuChange(true)
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_options),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        if (showOptionsMenu) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp)
                    .width(194.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                contentColor = Color(0xFF274552),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.SaveAddress,
                        label = stringResource(R.string.menu_save_address)
                    ) { onShowOptionsMenuChange(false); onAddFavorite() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.ClearLocation,
                        label = stringResource(R.string.map_clear_location),
                        enabled = clearLocationEnabled
                    ) { onShowOptionsMenuChange(false); onClearLocation() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.Telegram,
                        label = stringResource(R.string.telegram)
                    ) { onShowOptionsMenuChange(false); onOpenTelegram() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.Settings,
                        label = stringResource(R.string.screen_settings)
                    ) { onShowOptionsMenuChange(false); onOpenSettings() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.About,
                        label = stringResource(R.string.screen_about)
                    ) { onShowOptionsMenuChange(false); onShowAbout() }
                }
            }
        }
        if (showFavoritesPanel) {
            FavoriteLocationsPanel(
                favorites = favorites,
                onSelectFavorite = onSelectFavorite,
                onRemoveFavorite = onRemoveFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 66.dp)
            )
        }
    }
}

@Composable
private fun MapOptionsMenuItem(
    icon: PrototypeMenuIcon,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        PrototypeMenuIcon(icon, if (enabled) Color(0xFF65808A) else Color(0xFFB3C1C5), Modifier.size(25.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF274552) else Color(0xFF9AA9AE)
        )
    }
}

private enum class PrototypeMenuIcon { SaveAddress, ClearLocation, Settings, Sponsor, Telegram, About }

/** Small outlined glyphs redrawn for the prototype instead of reusing mixed Material icons. */
@Composable
private fun PrototypeMenuIcon(icon: PrototypeMenuIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(width = 1.7f * unit)
        when (icon) {
            PrototypeMenuIcon.SaveAddress -> {
                val pin = Path().apply {
                    moveTo(12f * unit, 21f * unit)
                    cubicTo(10.2f * unit, 18.1f * unit, 5f * unit, 14.3f * unit, 5f * unit, 9.6f * unit)
                    cubicTo(5f * unit, 5.9f * unit, 8.1f * unit, 3f * unit, 12f * unit, 3f * unit)
                    cubicTo(15.9f * unit, 3f * unit, 19f * unit, 5.9f * unit, 19f * unit, 9.6f * unit)
                    cubicTo(19f * unit, 14.3f * unit, 13.8f * unit, 18.1f * unit, 12f * unit, 21f * unit)
                    close()
                }
                drawPath(pin, color, style = stroke)
                drawCircle(color, radius = 2.1f * unit, center = point(12f, 9.5f), style = stroke)
            }
            PrototypeMenuIcon.ClearLocation -> {
                drawCircle(color, radius = 8.6f * unit, center = point(12f, 12f), style = stroke)
                drawLine(color, point(8.5f, 8.5f), point(15.5f, 15.5f), strokeWidth = 1.7f * unit)
                drawLine(color, point(15.5f, 8.5f), point(8.5f, 15.5f), strokeWidth = 1.7f * unit)
            }
            PrototypeMenuIcon.Settings -> {
                listOf(6f to 9f, 12f to 15f, 18f to 10f).forEach { (y, x) ->
                    drawLine(color, point(4f, y), point(20f, y), strokeWidth = 1.5f * unit)
                    drawCircle(color, radius = 2.1f * unit, center = point(x, y), style = stroke)
                }
            }
            PrototypeMenuIcon.Sponsor -> {
                val heart = Path().apply {
                    moveTo(12f * unit, 20.2f * unit)
                    cubicTo(10f * unit, 18.2f * unit, 4.2f * unit, 14.1f * unit, 4.2f * unit, 9.1f * unit)
                    cubicTo(4.2f * unit, 5.9f * unit, 6.6f * unit, 3.7f * unit, 9.4f * unit, 3.7f * unit)
                    cubicTo(10.8f * unit, 3.7f * unit, 11.7f * unit, 4.4f * unit, 12f * unit, 5.1f * unit)
                    cubicTo(12.3f * unit, 4.4f * unit, 13.2f * unit, 3.7f * unit, 14.6f * unit, 3.7f * unit)
                    cubicTo(17.4f * unit, 3.7f * unit, 19.8f * unit, 5.9f * unit, 19.8f * unit, 9.1f * unit)
                    cubicTo(19.8f * unit, 14.1f * unit, 14f * unit, 18.2f * unit, 12f * unit, 20.2f * unit)
                    close()
                }
                drawPath(heart, color, style = stroke)
            }
            PrototypeMenuIcon.Telegram -> {
                val plane = Path().apply {
                    moveTo(3.5f * unit, 10.1f * unit)
                    lineTo(20.3f * unit, 3.7f * unit)
                    lineTo(15.4f * unit, 20.3f * unit)
                    lineTo(10.4f * unit, 14.2f * unit)
                    close()
                }
                drawPath(plane, color, style = stroke)
                drawLine(color, point(10.4f, 14.2f), point(20.3f, 3.7f), strokeWidth = 1.7f * unit)
                drawLine(color, point(10.4f, 14.2f), point(9.6f, 20.1f), strokeWidth = 1.7f * unit)
            }
            PrototypeMenuIcon.About -> {
                drawCircle(color, radius = 8.6f * unit, center = point(12f, 12f), style = stroke)
                drawCircle(color, radius = 1.05f * unit, center = point(12f, 7.8f))
                drawLine(color, point(12f, 10.7f), point(12f, 16.7f), strokeWidth = 1.7f * unit)
            }
        }
    }
}

/** Inline favourite list matching the prototype, backed by the actual saved locations. */
@Composable
private fun FavoriteLocationsPanel(
    favorites: List<FavoriteLocation>,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onRemoveFavorite: (FavoriteLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(320.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFFFFF),
        contentColor = Color(0xFF264653),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MapOverlayBorderColor)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = stringResource(R.string.screen_favorites),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6C858E),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF789099),
                    modifier = Modifier.padding(10.dp)
                )
            } else {
                favorites.forEach { favorite ->
                    FavoriteLocationRow(
                        favorite = favorite,
                        onSelect = onSelectFavorite,
                        onRemove = onRemoveFavorite
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteLocationRow(
    favorite: FavoriteLocation,
    onSelect: (FavoriteLocation) -> Unit,
    onRemove: (FavoriteLocation) -> Unit
) {
    val rowShape = RoundedCornerShape(14.dp)
    val deleteWidth = 76.dp
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { deleteWidth.toPx() }
    var deleteVisible by remember(favorite) { mutableStateOf(false) }
    var isDragging by remember(favorite) { mutableStateOf(false) }
    var dragOffset by remember(favorite) { mutableFloatStateOf(0f) }
    val targetOffset = if (isDragging) {
        dragOffset
    } else if (deleteVisible) {
        -deleteWidthPx
    } else {
        0f
    }
    val foregroundOffset by animateFloatAsState(
        targetValue = targetOffset,
        // While dragging, snap to each pointer position. Once released, continue from that
        // exact position to the settled state instead of jumping back before animating.
        animationSpec = if (isDragging) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "favoriteDeleteReveal"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .height(68.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFC13B42))
                .clickable { onRemove(favorite) },
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = stringResource(R.string.action_delete),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(deleteWidth)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(foregroundOffset.roundToInt(), 0) }
                .background(Color.White)
                .pointerInput(favorite, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = foregroundOffset
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceIn(-deleteWidthPx, 0f)
                            change.consume()
                        },
                        onDragEnd = {
                            deleteVisible = dragOffset <= -deleteWidthPx / 2f
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
                .clickable {
                    if (deleteVisible) deleteVisible = false else onSelect(favorite)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(31.dp),
                shape = CircleShape,
                color = Color(0xFFD9F6F4),
                contentColor = Color(0xFF139F9B)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = favorite.address.ifBlank {
                        stringResource(R.string.coordinates_lat_lon, favorite.latitude, favorite.longitude)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F7780),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (favorite.address.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.coordinates_lat_lon, favorite.latitude, favorite.longitude),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF789099),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MapMyLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MapSideActionButton(MapSideAction.MyLocation, stringResource(R.string.cd_center), onClick)
        Text(stringResource(R.string.map_my_location), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF58727D), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MapMockLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(49.dp),
            shape = CircleShape,
            color = Color.White,
            contentColor = Color(0xFF006C5F),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "回到模拟定位点", tint = Color(0xFF006C5F))
            }
        }
        Text("模拟位置", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF58727D), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MapTargetAppsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MapSideActionButton(MapSideAction.TargetApps, stringResource(R.string.screen_target_apps), onClick)
        Text(stringResource(R.string.screen_target_apps), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF58727D), modifier = Modifier.padding(top = 4.dp))
    }
}

private enum class MapSideAction { TargetApps, MyLocation }

@Composable
private fun MapSideActionButton(
    type: MapSideAction,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(49.dp),
        shape = CircleShape,
        color = Color.White,
        contentColor = Color(0xFF174150),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val unit = size.minDimension / 24f
                fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
                val stroke = Stroke(width = 1.8f * unit)
                when (type) {
                    MapSideAction.TargetApps -> {
                        listOf(5f to 5f, 14f to 5f, 5f to 14f, 14f to 14f).forEach { (x, y) ->
                            drawRoundRect(
                                color = Color(0xFF174150),
                                topLeft = point(x, y),
                                size = androidx.compose.ui.geometry.Size(5f * unit, 5f * unit),
                                cornerRadius = CornerRadius(1f * unit),
                                style = stroke
                            )
                        }
                    }
                    MapSideAction.MyLocation -> {
                        drawCircle(Color(0xFF174150), radius = 6.2f * unit, center = point(12f, 12f), style = stroke)
                        drawCircle(Color(0xFF174150), radius = 1.7f * unit, center = point(12f, 12f))
                        drawLine(Color(0xFF174150), point(12f, 2f), point(12f, 5f), strokeWidth = 1.6f * unit)
                        drawLine(Color(0xFF174150), point(12f, 19f), point(12f, 22f), strokeWidth = 1.6f * unit)
                        drawLine(Color(0xFF174150), point(2f, 12f), point(5f, 12f), strokeWidth = 1.6f * unit)
                        drawLine(Color(0xFF174150), point(19f, 12f), point(22f, 12f), strokeWidth = 1.6f * unit)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddToFavoritesBottomSheet(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onAddFavorite: (name: String, address: String, latitude: Double, longitude: Double) -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val fields = uiState.addToFavoritesState
    HtmlModalSheet(onDismissRequest, stringResource(R.string.map_add_to_favorites)) {
        Text(stringResource(R.string.favorite_information), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF6A858D), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        PrototypeField(fields.name.value, { mapViewModel.updateAddToFavoritesField("name", it) }, stringResource(R.string.field_name), fields.name.errorMessageRes)
        PrototypeField(fields.latitude.value, { mapViewModel.updateAddToFavoritesField("latitude", it) }, stringResource(R.string.field_latitude), fields.latitude.errorMessageRes)
        PrototypeField(fields.longitude.value, { mapViewModel.updateAddToFavoritesField("longitude", it) }, stringResource(R.string.field_longitude), fields.longitude.errorMessageRes)
        PrototypePrimaryButton(stringResource(R.string.map_add_to_favorites)) {
            mapViewModel.validateAndAddFavorite { name, latitude, longitude ->
                onAddFavorite(name, uiState.selectedLocationAddress.orEmpty(), latitude, longitude)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GeoLocationAboutSheet(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    HtmlModalSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.screen_about),
        skipPartiallyExpanded = true
    ) {
        Surface(
            modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageResource(R.mipmap.ic_launcher)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = ctx.getString(R.string.app_name)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = "${stringResource(R.string.app_name)} · GeoLocation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF264653),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            text = "${stringResource(R.string.about_version_label)} ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF607981),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
        )
        Text(
            text = "面向 Android 平台的纯净、全功能位置、步频、环境与信标模拟测试工具。基于 MIT 开源协议发布，严禁用于虚假打卡等违规用途。",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = Color(0xFF607981)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "致谢上游开源项目与技术来源：",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B3B38)
        )

        val openSourceProjects = listOf(
            Triple("ceigt/GeoLocation", "https://github.com/ceigt/GeoLocation", "现代 Compose UI 与 Xposed 核心框架上游"),
            Triple("osmdroid", "https://github.com/osmdroid/osmdroid", "原生瓦片地图与图层渲染引擎"),
            Triple("LocusMimic", "https://github.com/wchunlin1006/LocusMimic", "多厂商系统服务适配与核心算法来源"),
            Triple("FakeGPS-next", "https://github.com/Elysia-SHY/FakeGPS-next", "国内高德卫星与矢量瓦片配置参考"),
            Triple("libxposed", "https://github.com/libxposed/api", "现代化非侵入式 LSPosed API 100/101 标准"),
            Triple("HiddenApiBypass", "https://github.com/LSPosed/AndroidHiddenApiBypass", "Android 11~16 隐藏 API 豁免安全底座")
        )

        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            openSourceProjects.forEach { (name, url, desc) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F6F5),
                    modifier = Modifier.fillMaxWidth().clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF006C5F))
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF55717C))
                        Text(url, style = MaterialTheme.typography.labelSmall, color = Color(0xFF88A0A8), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HtmlModalSheet(
    onDismissRequest: () -> Unit,
    title: String,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFFF5FBFC),
        scrimColor = Color(0x33000000),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF203F4C), modifier = Modifier.weight(1f))
                Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = Color(0xFFE4F0F3), contentColor = Color(0xFF55717C)) {
                    IconButton(onClick = onDismissRequest) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_back)) }
                }
            }
            content()
        }
    }
}

@Composable
private fun PrototypeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessageRes: Int?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = errorMessageRes != null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = Color(0xFFD9E8EB),
            unfocusedBorderColor = Color(0xFFD9E8EB)
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
    errorMessageRes?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
    }
}

@Composable
private fun PrototypePrimaryButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF237FE3),
        contentColor = Color.White,
        shadowElevation = 5.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapSearchPanel(
    query: String,
    isLoading: Boolean,
    results: List<PlaceSearchResult>,
    errorMessageRes: Int?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onResultClick: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    searchFieldModifier: Modifier = Modifier
) {
    var isSearchFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = searchFieldModifier
                .height(58.dp)
                .onFocusChanged { isSearchFocused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(29.dp),
            placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search_place),
                    modifier = Modifier.size(26.dp),
                    tint = Color(0xFF5D6B68)
                )
            },
            trailingIcon = {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                    query.isNotEmpty() -> IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear_search)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MapTopControlSurfaceColor,
                unfocusedContainerColor = MapTopControlSurfaceColor,
                focusedBorderColor = MapOverlayBorderColor,
                unfocusedBorderColor = MapOverlayBorderColor,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        if (isSearchFocused && (results.isNotEmpty() || errorMessageRes != null)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                if (results.isNotEmpty()) {
                    Column {
                        results.forEach { result ->
                            PlaceSearchResultRow(result = result, onClick = { onResultClick(result) })
                        }
                    }
                } else if (errorMessageRes != null) {
                    Text(
                        text = stringResource(errorMessageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPlayButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isStarting: Boolean = false
) {
    val shape = CircleShape
    val background = if (enabled) {
        Brush.linearGradient(
            if (isPlaying) listOf(Color(0xFFFF5B76), Color(0xFFE51F48))
            else listOf(Color(0xFF14B9F4), Color(0xFF8751DE))
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFDDE6E3),
                Color(0xFFC7D3D0)
            )
        )
    }
    val outerRingColor = when {
        !enabled -> Color(0x4D96AAA9)
        isPlaying -> Color(0x70FF5B76)
        else -> Color(0x7053D6F6)
    }
    Box(
        modifier = modifier
            .size(122.dp)
            .border(
                1.dp,
                outerRingColor,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(background)
                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isStarting) stringResource(R.string.map_waiting_cancel) else if (isPlaying) stringResource(R.string.map_stop_simulation) else stringResource(R.string.map_start_simulation),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color(0xFF73817D),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlaceSearchResultRow(
    result: PlaceSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = result.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(
                R.string.map_status_coordinates,
                result.latitude,
                result.longitude
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MapLocationInfo(
    lastClickedLocation: GeoPoint?,
    selectedLocationAddress: String?,
    selectedLocationPoiTitle: String?,
    isAddressLoading: Boolean,
    addressMessageRes: Int?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val title = when {
        lastClickedLocation == null -> stringResource(R.string.map_status_waiting)
        !selectedLocationPoiTitle.isNullOrBlank() -> selectedLocationPoiTitle
        !selectedLocationAddress.isNullOrBlank() -> selectedLocationAddress.toAddressLeafLabel()
        isAddressLoading -> stringResource(R.string.map_status_address_loading)
        addressMessageRes != null -> stringResource(addressMessageRes)
        else -> stringResource(R.string.map_status_address_loading)
    }
    val address = selectedLocationAddress?.takeIf { it.isNotBlank() }
    val coordinates = lastClickedLocation?.let {
        stringResource(R.string.map_status_coordinates, it.latitude, it.longitude)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isPlaying) stringResource(R.string.map_status_active) else stringResource(R.string.map_status_selected),
            style = MaterialTheme.typography.labelSmall,
            color = if (isPlaying) Color(0xFFF14E66) else Color(0xFF168F8A),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF183B49),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        address?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF69838D), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        coordinates?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8097A0), maxLines = 1)
        }
    }
}

@Composable
private fun MapStatusPanel(
    lastClickedLocation: GeoPoint?,
    selectedLocationAddress: String?,
    selectedLocationPoiTitle: String?,
    isAddressLoading: Boolean,
    addressMessageRes: Int?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val title = when {
        lastClickedLocation != null -> selectedLocationPoiTitle?.takeIf { it.isNotBlank() }
            ?: selectedLocationAddress?.takeIf { it.isNotBlank() }?.toAddressLeafLabel()
            // Reverse geocoding is asynchronous. Keep the title aligned with the current
            // simulation state while waiting instead of flashing a generic selection label.
            ?: stringResource(
                if (isPlaying) R.string.map_status_active else R.string.map_status_address_loading
            )
        else -> stringResource(R.string.map_status_waiting)
    }
    val coordinatesText = if (lastClickedLocation != null) {
        stringResource(
            R.string.map_status_coordinates,
            lastClickedLocation.latitude,
            lastClickedLocation.longitude
        )
    } else {
        stringResource(R.string.map_status_hint)
    }
    val addressText = when {
        lastClickedLocation == null -> null
        selectedLocationAddress?.isNotBlank() == true -> {
            stringResource(R.string.map_status_address, selectedLocationAddress)
        }
        isAddressLoading -> stringResource(R.string.map_status_address_loading)
        addressMessageRes != null -> stringResource(addressMessageRes)
        else -> null
    }
    // This is the map's only persistent information surface. Match the bottom navigation's
    // cool-grey translucent fill so the map remains visible beneath the rounded card.
    val accentColor = if (isPlaying) Color(0xFFFF4269) else Color(0xFF12A9ED)
    val statusLabel = stringResource(
        if (isPlaying) R.string.map_status_active else R.string.map_status_stopped
    )
    val supportingText = if (isPlaying) {
        selectedLocationAddress?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.map_status_active_hint)
    } else {
        addressText
    }

    val panelShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, panelShape, clip = false)
            .clip(panelShape)
            .background(MapOverlaySurfaceColor)
            .border(1.dp, MapOverlayBorderColor, panelShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = accentColor.copy(alpha = 0.12f),
                contentColor = accentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF123C3A),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = coordinatesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5B7370),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5B7370),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The POI-formatted address is stored as the final segment after the administrative prefix. */
private fun String.toAddressLeafLabel(): String {
    val trimmed = trim()
    return trimmed.substringAfterLast(" · ").trim().ifBlank { trimmed }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RoutePlanningPanel(
    uiState: MapViewModel.MapUiState,
    mapViewModel: MapViewModel,
    onSaveRoute: () -> Unit,
    onOpenRouteLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF7FFFFFF),
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCE8E4))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = Color(0xFF006C5F), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "路线规划模式 (已选 ${uiState.plannedWaypoints.size} 航点)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A37)
                    )
                }
                IconButton(onClick = { mapViewModel.toggleRoutePlanningMode() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "退出规划", tint = Color(0xFF55717C))
                }
            }

            // Route metrics
            val distKm = "%.2f".format(uiState.routeDistanceMeters / 1000.0)
            val durationMin = ((uiState.routeDistanceMeters / uiState.routeSpeedMps.coerceAtLeast(0.5)) / 60.0).roundToInt()
            Text(
                text = "总里程: $distKm km | 预计用时: $durationMin 分钟 | 预估步数: ${uiState.routeEstimatedSteps} 步",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF006C5F),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            // Tool buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { mapViewModel.removeLastRouteWaypoint() },
                    enabled = uiState.plannedWaypoints.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("撤销", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { mapViewModel.clearRouteWaypoints() },
                    enabled = uiState.plannedWaypoints.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("清空", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val cur = uiState.lastClickedLocation
                        if (cur != null) {
                            mapViewModel.autoGenerateRouteTo(cur)
                        } else {
                            Toast.makeText(context, "请先在地图选定目标终点", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("自动规划", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val cur = uiState.lastClickedLocation ?: uiState.plannedWaypoints.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                        if (cur != null) {
                            val wps = io.github.ceigt.geolocation.manager.route.TrackGenerator.generateStadiumTrack(
                                io.github.ceigt.geolocation.data.model.route.TrackEditorConfig(
                                    centerLat = cur.latitude,
                                    centerLon = cur.longitude,
                                    circumferenceMeters = 400.0,
                                    startAtSelectedPoint = true
                                )
                            )
                            mapViewModel.clearRouteWaypoints()
                            wps.forEach { mapViewModel.addRouteWaypoint(GeoPoint(it.latitude, it.longitude)) }
                        } else {
                            Toast.makeText(context, "请在地图上选定起跑点", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("操场跑道", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Speed & Cadence config
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "速度: ${"%.1f".format(uiState.routeSpeedMps)} m/s (${(uiState.routeSpeedMps * 3.6).roundToInt()} km/h)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = uiState.routeLoopMode == io.github.ceigt.geolocation.data.model.route.RouteLoopMode.CYCLE,
                        onClick = { mapViewModel.setRouteLoopMode(io.github.ceigt.geolocation.data.model.route.RouteLoopMode.CYCLE) },
                        label = { Text("闭环", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = uiState.routeLoopMode == io.github.ceigt.geolocation.data.model.route.RouteLoopMode.BOUNCE,
                        onClick = { mapViewModel.setRouteLoopMode(io.github.ceigt.geolocation.data.model.route.RouteLoopMode.BOUNCE) },
                        label = { Text("折返", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Slider(
                value = uiState.routeSpeedMps.toFloat(),
                onValueChange = { mapViewModel.setRouteSpeedMps(it.toDouble()) },
                valueRange = 0.5f..20.0f,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Cadence Mode row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("步频:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = uiState.routeCadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO,
                    onClick = { mapViewModel.setRouteCadence(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO, uiState.routeCustomSpm) },
                    label = { Text("自适应", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = uiState.routeCadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM,
                    onClick = { mapViewModel.setRouteCadence(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM, uiState.routeCustomSpm) },
                    label = { Text("固定SPM", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = uiState.routeCadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.OFF,
                    onClick = { mapViewModel.setRouteCadence(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.OFF, uiState.routeCustomSpm) },
                    label = { Text("关闭", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Text(
                "💡 路线专属步频仅在巡航时生效；全局/分应用接管可在「设置-步频与传感器」统一配置。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF55717C),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            if (uiState.routeCadenceMode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.FilledTonalIconButton(
                        onClick = { mapViewModel.setRouteCadence(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM, (uiState.routeCustomSpm - 5).coerceIn(40, 240)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "-5")
                    }
                    Text(
                        text = "目标步频: ${uiState.routeCustomSpm} SPM",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.FilledTonalIconButton(
                        onClick = { mapViewModel.setRouteCadence(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM, (uiState.routeCustomSpm + 5).coerceIn(40, 240)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+5")
                    }
                }
                Slider(
                    value = uiState.routeCustomSpm.toFloat(),
                    onValueChange = { mapViewModel.setRouteCadence(io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.CUSTOM, it.toInt()) },
                    valueRange = 40f..240f,
                    steps = 200
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSaveRoute,
                    enabled = uiState.plannedWaypoints.size >= 2,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("收藏", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onOpenRouteLibrary,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("路线库", style = MaterialTheme.typography.labelMedium)
                }

                val isSimulating = uiState.isRouteSimulating || io.github.ceigt.geolocation.manager.route.RouteSimulationService.engine.state.isActive
                Button(
                    onClick = {
                        if (isSimulating) {
                            mapViewModel.stopSimulatingRoute(context)
                        } else {
                            if (uiState.plannedWaypoints.size >= 2) {
                                mapViewModel.startSimulatingRoute(context)
                            } else {
                                Toast.makeText(context, "请在地图上至少规划 2 个航点", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulating) MaterialTheme.colorScheme.error else Color(0xFF006C5F)
                    ),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSimulating) "停止巡航" else "启动巡航", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
