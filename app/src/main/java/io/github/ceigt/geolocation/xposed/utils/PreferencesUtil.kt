// PreferencesUtil.kt
package io.github.ceigt.geolocation.xposed.utils

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.data.JsonCodec
import io.github.ceigt.geolocation.data.CoordinateSystem
import io.github.ceigt.geolocation.data.DEFAULT_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_ALTITUDE
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_GLOBAL_HOOKS
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_ROOT_MODE
import io.github.ceigt.geolocation.data.DEFAULT_LOCATION_MODE
import io.github.ceigt.geolocation.data.DEFAULT_MOCK_FLAG_POLICY
import io.github.ceigt.geolocation.data.DEFAULT_HOOK_INSTALL_POLICY
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_CONSTELLATION_PRESET
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_SIGNAL_STRENGTH_PRESET
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_VISIBLE_SATELLITES
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_USED_SATELLITES
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_JITTER_ENABLED
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_JITTER_ALGORITHM
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_JITTER_RADIUS
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_JITTER_SMOOTHING
import io.github.ceigt.geolocation.data.DEFAULT_GNSS_JITTER_DRIFT_SPEED
import io.github.ceigt.geolocation.data.KEY_GNSS_CONSTELLATION_PRESET
import io.github.ceigt.geolocation.data.KEY_GNSS_SIGNAL_STRENGTH_PRESET
import io.github.ceigt.geolocation.data.KEY_GNSS_VISIBLE_SATELLITES
import io.github.ceigt.geolocation.data.KEY_GNSS_USED_SATELLITES
import io.github.ceigt.geolocation.data.KEY_GNSS_JITTER_ENABLED
import io.github.ceigt.geolocation.data.KEY_GNSS_JITTER_ALGORITHM
import io.github.ceigt.geolocation.data.KEY_GNSS_JITTER_RADIUS
import io.github.ceigt.geolocation.data.KEY_GNSS_JITTER_SMOOTHING
import io.github.ceigt.geolocation.data.KEY_GNSS_JITTER_DRIFT_SPEED
import io.github.ceigt.geolocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import io.github.ceigt.geolocation.data.DEFAULT_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_RANDOMIZE_RADIUS
import io.github.ceigt.geolocation.data.DEFAULT_SPEED
import io.github.ceigt.geolocation.data.DEFAULT_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_ALTITUDE
import io.github.ceigt.geolocation.data.DEFAULT_USE_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_RANDOMIZE
import io.github.ceigt.geolocation.data.DEFAULT_USE_SPEED
import io.github.ceigt.geolocation.data.DEFAULT_USE_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_GLOBAL_CADENCE_MODE
import io.github.ceigt.geolocation.data.KEY_GLOBAL_CADENCE_CHANNEL
import io.github.ceigt.geolocation.data.KEY_GLOBAL_CUSTOM_SPM
import io.github.ceigt.geolocation.data.KEY_APP_CADENCE_CONFIGS
import io.github.ceigt.geolocation.data.DEFAULT_GLOBAL_CADENCE_MODE
import io.github.ceigt.geolocation.data.DEFAULT_GLOBAL_CADENCE_CHANNEL
import io.github.ceigt.geolocation.data.DEFAULT_GLOBAL_CUSTOM_SPM
import io.github.ceigt.geolocation.data.DEFAULT_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_ACCURACY
import io.github.ceigt.geolocation.data.KEY_APP_COORDINATE_SYSTEMS
import io.github.ceigt.geolocation.data.KEY_ALTITUDE
import io.github.ceigt.geolocation.data.KEY_ENABLE_SYSTEM_HOOKS
import io.github.ceigt.geolocation.data.KEY_ENABLE_GLOBAL_HOOKS
import io.github.ceigt.geolocation.data.KEY_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.KEY_ENABLE_ROOT_MODE
import io.github.ceigt.geolocation.data.KEY_LOCATION_MODE
import io.github.ceigt.geolocation.data.KEY_LOCATION_ADDRESS_METADATA
import io.github.ceigt.geolocation.data.KEY_CHANNEL_SETTINGS
import io.github.ceigt.geolocation.data.KEY_MOCK_FLAG_POLICY
import io.github.ceigt.geolocation.data.KEY_HOOK_INSTALL_POLICY
import io.github.ceigt.geolocation.data.KEY_APP_HOOK_INSTALL_POLICIES
import io.github.ceigt.geolocation.data.KEY_HIDE_FAKE_LOCATION_TOAST
import io.github.ceigt.geolocation.data.KEY_IS_PLAYING
import io.github.ceigt.geolocation.data.KEY_LAST_CLICKED_LOCATION
import io.github.ceigt.geolocation.data.KEY_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.KEY_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_RANDOMIZE_RADIUS
import io.github.ceigt.geolocation.data.KEY_SPEED
import io.github.ceigt.geolocation.data.KEY_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.KEY_TARGET_APPS
import io.github.ceigt.geolocation.data.KEY_USE_ACCURACY
import io.github.ceigt.geolocation.data.KEY_USE_ALTITUDE
import io.github.ceigt.geolocation.data.KEY_USE_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.KEY_USE_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_USE_RANDOMIZE
import io.github.ceigt.geolocation.data.KEY_USE_SPEED
import io.github.ceigt.geolocation.data.KEY_USE_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.KEY_USE_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.model.LastClickedLocation
import io.github.ceigt.geolocation.data.model.mode.HookInstallPolicy
import io.github.ceigt.geolocation.data.model.mode.LocationMode
import io.github.ceigt.geolocation.data.model.mode.MockFlagPolicy

object PreferencesUtil {
    private const val TAG = "[PreferencesUtil]"

    @Volatile var logger: ((Int, String, String) -> Unit)? = null
    private fun log(msg: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, msg)


    @Volatile private var preferences: SharedPreferences? = null
    @Volatile private var registeredPrefs: SharedPreferences? = null
    @Volatile private var cache: PreferencesSnapshot = PreferencesSnapshot()
    @Volatile private var lastRefreshNanos: Long = 0L
    private var lastDiagnostic: String? = null

    // IMPORTANT: keep a strong reference. SharedPreferences holds listeners *weakly*,
    // so a listener that isn't referenced anywhere gets GC'd and silently stops firing.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        refreshCache(prefs)
    }

    private val modeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun onModeChange(listener: () -> Unit) { modeListeners.add(listener) }

    fun init(prefs: SharedPreferences) {
        preferences = prefs
        if (registeredPrefs !== prefs) {
            registeredPrefs?.let {
                runCatching { it.unregisterOnSharedPreferenceChangeListener(changeListener) }
            }
            prefs.registerOnSharedPreferenceChangeListener(changeListener)
            registeredPrefs = prefs
        }
        refreshCache(prefs)
        log("Initialized with cached remote preferences")
    }

    fun refresh() {
        refreshCache()
    }

    fun getIsPlaying(): Boolean {
        return snapshot().isPlaying
    }

    fun snapshot(): PreferencesSnapshot {
        maybeRefreshCache()
        return cache
    }

    fun getLastClickedLocation(): LastClickedLocation? = cache.lastClickedLocation
    fun getUseAccuracy(): Boolean = cache.useAccuracy
    fun getAccuracy(): Double = cache.accuracy
    fun getUseAltitude(): Boolean = cache.useAltitude
    fun getAltitude(): Double = cache.altitude
    fun getUseRandomize(): Boolean = cache.useRandomize
    fun getRandomizeRadius(): Double = cache.randomizeRadius
    fun getUseVerticalAccuracy(): Boolean = cache.useVerticalAccuracy
    fun getVerticalAccuracy(): Float = cache.verticalAccuracy
    fun getUseMeanSeaLevel(): Boolean = cache.useMeanSeaLevel
    fun getMeanSeaLevel(): Double = cache.meanSeaLevel
    fun getUseMeanSeaLevelAccuracy(): Boolean = cache.useMeanSeaLevelAccuracy
    fun getMeanSeaLevelAccuracy(): Float = cache.meanSeaLevelAccuracy
    fun getUseSpeed(): Boolean = cache.useSpeed
    fun getSpeed(): Float = cache.speed
    fun getUseSpeedAccuracy(): Boolean = cache.useSpeedAccuracy
    fun getSpeedAccuracy(): Float = cache.speedAccuracy
    fun getHideFakeLocationToast(): Boolean = cache.hideFakeLocationToast
    fun getEnableSystemHooks(): Boolean = cache.enableSystemHooks
    fun getEnableGlobalHooks(): Boolean = cache.enableGlobalHooks
    fun getEnableRootMode(): Boolean = cache.enableRootMode
    fun getLocationMode(): LocationMode = cache.locationMode
    fun getMockFlagPolicy(): MockFlagPolicy = cache.mockFlagPolicy
    fun getHookInstallPolicy(): HookInstallPolicy = cache.hookInstallPolicy
    fun getAppHookInstallPolicy(packageName: String): HookInstallPolicy =
        cache.appHookInstallPolicies[packageName] ?: cache.hookInstallPolicy

    fun getGnssConstellationPreset(): io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset = cache.gnssConstellationPreset
    fun getGnssSignalStrengthPreset(): io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset = cache.gnssSignalStrengthPreset
    fun getGnssVisibleSatellites(): Int = cache.gnssVisibleSatellites
    fun getGnssUsedSatellites(): Int = cache.gnssUsedSatellites
    fun getGnssJitterEnabled(): Boolean = cache.gnssJitterEnabled
    fun getGnssJitterAlgorithm(): io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm = cache.gnssJitterAlgorithm
    fun getGnssJitterRadius(): Double = cache.gnssJitterRadius
    fun getGnssJitterSmoothing(): Double = cache.gnssJitterSmoothing
    fun getGnssJitterDriftSpeed(): Double = cache.gnssJitterDriftSpeed
    fun getChannelSettings(channel: io.github.ceigt.geolocation.data.model.channel.LocationChannel): io.github.ceigt.geolocation.data.model.channel.ChannelSettings =
        cache.channelSettingsMap[channel.channelKey] ?: io.github.ceigt.geolocation.data.model.channel.ChannelSettings()
    fun getLocationAddressMetadata(): io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata = cache.locationAddressMetadata
    fun getGlobalCadenceMode(): io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode = cache.globalCadenceMode
    fun getGlobalCadenceChannel(): io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel = cache.globalCadenceChannel
    fun getGlobalCustomSpm(): Int = cache.globalCustomSpm
    fun getAppCadenceConfig(packageName: String): io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig {
        val appConfig = cache.appCadenceConfigs[packageName]
        if (appConfig == null || appConfig.mode == io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.INHERIT) {
            return io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig(
                mode = cache.globalCadenceMode,
                customStepsPerMinute = cache.globalCustomSpm,
                channel = cache.globalCadenceChannel
            )
        }
        return appConfig
    }
    fun getTargetApps(): Set<String> {
        return snapshot().targetApps
    }

    private fun maybeRefreshCache() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - lastRefreshNanos < REFRESH_INTERVAL_NANOS) return
        synchronized(this) {
            val recheckedNow = SystemClock.elapsedRealtimeNanos()
            if (recheckedNow - lastRefreshNanos >= REFRESH_INTERVAL_NANOS) {
                refreshCache()
            }
        }
    }

    @Synchronized
    private fun refreshCache(prefs: SharedPreferences? = preferences) {
        if (prefs == null) {
            cache = PreferencesSnapshot()
            lastRefreshNanos = SystemClock.elapsedRealtimeNanos()
            return
        }

        val mockProviderEnabled = prefs.getBoolean(
            KEY_ENABLE_MOCK_PROVIDER,
            DEFAULT_ENABLE_MOCK_PROVIDER
        )
        val point = parseLastClickedLocation(prefs.getString(KEY_LAST_CLICKED_LOCATION, null))
        val previousMode = cache.isPlaying && cache.enableSystemHooks
        cache = PreferencesSnapshot(
            // Mock Provider and Xposed replacement are mutually exclusive location sources.
            // Keeping installed interceptors inert also makes mode changes take effect without
            // restarting target apps that already have this module loaded.
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false) && !mockProviderEnabled && point != null,
            lastClickedLocation = point,
            useAccuracy = prefs.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY),
            accuracy = readDouble(prefs, KEY_ACCURACY, DEFAULT_ACCURACY),
            useAltitude = prefs.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE),
            altitude = readDouble(prefs, KEY_ALTITUDE, DEFAULT_ALTITUDE),
            useRandomize = prefs.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE),
            randomizeRadius = readDouble(prefs, KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS),
            useVerticalAccuracy = prefs.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY),
            verticalAccuracy = prefs.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY),
            useMeanSeaLevel = prefs.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL),
            meanSeaLevel = readDouble(prefs, KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL),
            useMeanSeaLevelAccuracy = prefs.getBoolean(
                KEY_USE_MEAN_SEA_LEVEL_ACCURACY,
                DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
            ),
            meanSeaLevelAccuracy = prefs.getFloat(
                KEY_MEAN_SEA_LEVEL_ACCURACY,
                DEFAULT_MEAN_SEA_LEVEL_ACCURACY
            ),
            useSpeed = prefs.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED),
            speed = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED),
            useSpeedAccuracy = prefs.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY),
            speedAccuracy = prefs.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY),
            hideFakeLocationToast = prefs.getBoolean(
                KEY_HIDE_FAKE_LOCATION_TOAST,
                DEFAULT_HIDE_FAKE_LOCATION_TOAST
            ),
            enableSystemHooks = prefs.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS),
            enableGlobalHooks = prefs.getBoolean(KEY_ENABLE_GLOBAL_HOOKS, DEFAULT_ENABLE_GLOBAL_HOOKS),
            enableRootMode = prefs.getBoolean(KEY_ENABLE_ROOT_MODE, DEFAULT_ENABLE_ROOT_MODE),
            locationMode = LocationMode.fromStored(prefs.getString(KEY_LOCATION_MODE, DEFAULT_LOCATION_MODE)),
            mockFlagPolicy = MockFlagPolicy.fromStored(prefs.getString(KEY_MOCK_FLAG_POLICY, DEFAULT_MOCK_FLAG_POLICY)),
            hookInstallPolicy = HookInstallPolicy.fromStored(prefs.getString(KEY_HOOK_INSTALL_POLICY, DEFAULT_HOOK_INSTALL_POLICY)),
            appHookInstallPolicies = parseAppHookInstallPolicies(prefs.getString(KEY_APP_HOOK_INSTALL_POLICIES, null)),
            gnssConstellationPreset = io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.fromStored(prefs.getString(KEY_GNSS_CONSTELLATION_PRESET, DEFAULT_GNSS_CONSTELLATION_PRESET)),
            gnssSignalStrengthPreset = io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.fromStored(prefs.getString(KEY_GNSS_SIGNAL_STRENGTH_PRESET, DEFAULT_GNSS_SIGNAL_STRENGTH_PRESET)),
            gnssVisibleSatellites = prefs.getInt(KEY_GNSS_VISIBLE_SATELLITES, DEFAULT_GNSS_VISIBLE_SATELLITES),
            gnssUsedSatellites = prefs.getInt(KEY_GNSS_USED_SATELLITES, DEFAULT_GNSS_USED_SATELLITES),
            gnssJitterEnabled = prefs.getBoolean(KEY_GNSS_JITTER_ENABLED, DEFAULT_GNSS_JITTER_ENABLED),
            gnssJitterAlgorithm = io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.fromStored(prefs.getString(KEY_GNSS_JITTER_ALGORITHM, DEFAULT_GNSS_JITTER_ALGORITHM)),
            gnssJitterRadius = readDouble(prefs, KEY_GNSS_JITTER_RADIUS, DEFAULT_GNSS_JITTER_RADIUS),
            gnssJitterSmoothing = readDouble(prefs, KEY_GNSS_JITTER_SMOOTHING, DEFAULT_GNSS_JITTER_SMOOTHING),
            gnssJitterDriftSpeed = readDouble(prefs, KEY_GNSS_JITTER_DRIFT_SPEED, DEFAULT_GNSS_JITTER_DRIFT_SPEED),
            locationAddressMetadata = JsonCodec.decodeAddressMetadata(prefs.getString(KEY_LOCATION_ADDRESS_METADATA, null)),
            channelSettingsMap = JsonCodec.decodeChannelSettingsMap(prefs.getString(KEY_CHANNEL_SETTINGS, null)),
            globalCadenceMode = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.fromStored(prefs.getString(KEY_GLOBAL_CADENCE_MODE, DEFAULT_GLOBAL_CADENCE_MODE)),
            globalCadenceChannel = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.fromStored(prefs.getString(KEY_GLOBAL_CADENCE_CHANNEL, DEFAULT_GLOBAL_CADENCE_CHANNEL)),
            globalCustomSpm = prefs.getInt(KEY_GLOBAL_CUSTOM_SPM, DEFAULT_GLOBAL_CUSTOM_SPM),
            appCadenceConfigs = JsonCodec.decodeCadenceConfigs(prefs.getString(KEY_APP_CADENCE_CONFIGS, null)),
            targetApps = parseTargetApps(prefs.getString(KEY_TARGET_APPS, null)),
            appCoordinateSystems = parseAppCoordinateSystems(
                prefs.getString(KEY_APP_COORDINATE_SYSTEMS, null)
            )
        )
        lastRefreshNanos = SystemClock.elapsedRealtimeNanos()
        if (previousMode != (cache.isPlaying && cache.enableSystemHooks)) {
            modeListeners.forEach { listener -> runCatching { listener() } }
        }
        val diagnostic = "hookActive=${cache.isPlaying}, mockProvider=$mockProviderEnabled, " +
            "hasPoint=${cache.lastClickedLocation != null}, systemHooks=${cache.enableSystemHooks}"
        if (diagnostic != lastDiagnostic) {
            lastDiagnostic = diagnostic
            log(diagnostic)
        }
    }

    private fun parseLastClickedLocation(json: String?): LastClickedLocation? {
        if (json.isNullOrBlank()) return null
        return runCatching { JsonCodec.decodeLocation(json) }
            .onFailure { log("Invalid saved location configuration", Log.ERROR) }
            .getOrNull()
            ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    }

    private fun parseTargetApps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return runCatching {
            JsonCodec.decodeStringSet(json)
        }.onFailure {
            log("Error parsing $KEY_TARGET_APPS JSON: ${it.message}", Log.ERROR)
        }.getOrDefault(emptySet())
    }

    private fun parseAppCoordinateSystems(json: String?): Map<String, CoordinateSystem> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            JsonCodec.decodeStringMap(json).mapValues { CoordinateSystem.fromStored(it.value) }
        }.onFailure {
            log("Error parsing $KEY_APP_COORDINATE_SYSTEMS JSON: ${it.message}", Log.ERROR)
        }.getOrDefault(emptyMap())
    }

    private fun readDouble(prefs: SharedPreferences, key: String, default: Double): Double {
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    private fun parseAppHookInstallPolicies(json: String?): Map<String, HookInstallPolicy> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            JsonCodec.decodeStringMap(json).mapValues { HookInstallPolicy.fromStored(it.value) }
        }.onFailure {
            log("Error parsing $KEY_APP_HOOK_INSTALL_POLICIES JSON: ${it.message}", Log.ERROR)
        }.getOrDefault(emptyMap())
    }

    data class PreferencesSnapshot(
        val isPlaying: Boolean = false,
        val lastClickedLocation: LastClickedLocation? = null,
        val useAccuracy: Boolean = DEFAULT_USE_ACCURACY,
        val accuracy: Double = DEFAULT_ACCURACY,
        val useAltitude: Boolean = DEFAULT_USE_ALTITUDE,
        val altitude: Double = DEFAULT_ALTITUDE,
        val useRandomize: Boolean = DEFAULT_USE_RANDOMIZE,
        val randomizeRadius: Double = DEFAULT_RANDOMIZE_RADIUS,
        val useVerticalAccuracy: Boolean = DEFAULT_USE_VERTICAL_ACCURACY,
        val verticalAccuracy: Float = DEFAULT_VERTICAL_ACCURACY,
        val useMeanSeaLevel: Boolean = DEFAULT_USE_MEAN_SEA_LEVEL,
        val meanSeaLevel: Double = DEFAULT_MEAN_SEA_LEVEL,
        val useMeanSeaLevelAccuracy: Boolean = DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        val meanSeaLevelAccuracy: Float = DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        val useSpeed: Boolean = DEFAULT_USE_SPEED,
        val speed: Float = DEFAULT_SPEED,
        val useSpeedAccuracy: Boolean = DEFAULT_USE_SPEED_ACCURACY,
        val speedAccuracy: Float = DEFAULT_SPEED_ACCURACY,
        val hideFakeLocationToast: Boolean = DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        val enableSystemHooks: Boolean = DEFAULT_ENABLE_SYSTEM_HOOKS,
        val enableGlobalHooks: Boolean = DEFAULT_ENABLE_GLOBAL_HOOKS,
        val enableRootMode: Boolean = DEFAULT_ENABLE_ROOT_MODE,
        val locationMode: LocationMode = LocationMode.APPLICATION_HOOK,
        val mockFlagPolicy: MockFlagPolicy = MockFlagPolicy.HIDE_ALL,
        val hookInstallPolicy: HookInstallPolicy = HookInstallPolicy.IMMEDIATE,
        val appHookInstallPolicies: Map<String, HookInstallPolicy> = emptyMap(),
        val gnssConstellationPreset: io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset = io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.AUTO_MIXED,
        val gnssSignalStrengthPreset: io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset = io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.NORMAL,
        val gnssVisibleSatellites: Int = DEFAULT_GNSS_VISIBLE_SATELLITES,
        val gnssUsedSatellites: Int = DEFAULT_GNSS_USED_SATELLITES,
        val gnssJitterEnabled: Boolean = DEFAULT_GNSS_JITTER_ENABLED,
        val gnssJitterAlgorithm: io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm = io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FIRST_ORDER_MARKOV,
        val gnssJitterRadius: Double = DEFAULT_GNSS_JITTER_RADIUS,
        val gnssJitterSmoothing: Double = DEFAULT_GNSS_JITTER_SMOOTHING,
        val gnssJitterDriftSpeed: Double = DEFAULT_GNSS_JITTER_DRIFT_SPEED,
        val locationAddressMetadata: io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata = io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata(),
        val channelSettingsMap: Map<String, io.github.ceigt.geolocation.data.model.channel.ChannelSettings> = emptyMap(),
        val globalCadenceMode: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO,
        val globalCadenceChannel: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.ACCELEROMETER,
        val globalCustomSpm: Int = DEFAULT_GLOBAL_CUSTOM_SPM,
        val appCadenceConfigs: Map<String, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig> = emptyMap(),
        val targetApps: Set<String> = emptySet(),
        val appCoordinateSystems: Map<String, CoordinateSystem> = emptyMap()
    )

    // Listener notifications are immediate; this is only a missed-notification safety net.
    private const val REFRESH_INTERVAL_NANOS = 10_000_000_000L
}
