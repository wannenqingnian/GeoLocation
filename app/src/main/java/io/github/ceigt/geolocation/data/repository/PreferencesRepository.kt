// PreferencesRepository.kt
package io.github.ceigt.geolocation.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import io.github.ceigt.geolocation.data.*
import io.github.ceigt.geolocation.data.model.FavoriteLocation
import io.github.ceigt.geolocation.data.model.LastClickedLocation
import io.github.ceigt.geolocation.data.model.mode.HookInstallPolicy
import io.github.ceigt.geolocation.data.model.mode.LocationMode
import io.github.ceigt.geolocation.data.model.mode.MockFlagPolicy
import io.github.ceigt.geolocation.manager.App
import io.github.ceigt.geolocation.manager.mock.MockLocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manager state is durable locally. Hook settings are mirrored through PreferenceSync;
 * offline edits remain pending until LSPosed reconnects. Map credentials stay local.
 *
 * Doubles are encoded as raw long bits because [SharedPreferences] has no putDouble,
 * keeping read/write symmetry with the hook-side PreferencesUtil.
 */
class PreferencesRepository(context: Context) {
    private companion object {
        val writeMutex = Mutex()
        val appCoordinateMutex = Mutex()
        val favoritesMutex = Mutex()
    }
    private val tag = "PreferencesRepository"

    private val appContext = context.applicationContext

    private val localPrefs: SharedPreferences =
        appContext.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)

    // The manager always reads its durable cache, including in standalone Mock Provider mode.
    fun sharedPrefs(): SharedPreferences? = localPrefs

    // region Flow helpers

    @Suppress("UNUSED_PARAMETER")
    private fun <T> sharedFlow(key: String, default: T, read: (SharedPreferences) -> T): Flow<T> =
        prefsChangeFlow(localPrefs, key) { read(localPrefs) }

    private fun <T> localFlow(key: String, read: (SharedPreferences) -> T): Flow<T> =
        prefsChangeFlow(localPrefs, key) { read(localPrefs) }

    private fun <T> prefsChangeFlow(
        prefs: SharedPreferences,
        key: String,
        read: () -> T
    ): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey == key) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // endregion

    // region Write helpers

    private suspend fun editHookSetting(action: SharedPreferences.Editor.() -> Unit) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val shared = runCatching { App.service?.getRemotePreferences(REMOTE_PREFS_GROUP) }.getOrNull()
            PreferenceSync.edit(localPrefs, shared, action)
            App.requestSync()
        }
    }

    private inline fun editLocal(action: SharedPreferences.Editor.() -> Unit) {
        localPrefs.edit(action = action)
    }

    private suspend fun editLocalChecked(key: String, action: SharedPreferences.Editor.() -> Unit) =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val previous = localPrefs.all
                val editor = localPrefs.edit().apply(action)
                commitOrRestore(localPrefs, editor, previous, setOf(key))
            }
        }

    private fun readSharedDouble(key: String, default: Double): Double {
        val prefs = sharedPrefs() ?: return default
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    // endregion

    // region Disclaimer (local)
    fun hasAcceptedDisclaimer(): Boolean =
        localPrefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)

    fun saveDisclaimerAccepted() {
        editLocal { putBoolean(KEY_DISCLAIMER_ACCEPTED, true) }
    }
    // endregion

    // region Is Playing (hook-shared)
    fun getPendingHookSettingsFlow(): Flow<Boolean> = localFlow(PreferenceSync.PENDING) {
        it.getStringSet(PreferenceSync.PENDING, emptySet()).orEmpty().isNotEmpty()
    }

    fun getIsPlayingFlow(): Flow<Boolean> = sharedFlow(KEY_IS_PLAYING, false) {
        it.getBoolean(KEY_IS_PLAYING, false)
    }
    suspend fun saveIsPlaying(isPlaying: Boolean) {
        if (isPlaying) require(getLastClickedLocation()?.let {
            it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        } == true) { "Select a valid location before starting simulation" }
        editHookSetting { putBoolean(KEY_IS_PLAYING, isPlaying) }
        MockLocationService.sync(appContext, isPlaying && getEnableMockProvider())
        io.github.ceigt.geolocation.manager.root.RootLocationService.sync(appContext, isPlaying && getEnableRootMode())
    }
    fun getIsPlaying(): Boolean = sharedPrefs()?.getBoolean(KEY_IS_PLAYING, false) ?: false
    // endregion

    // region Last Clicked Location (hook-shared)
    fun getLastClickedLocationFlow(): Flow<LastClickedLocation?> =
        sharedFlow<LastClickedLocation?>(KEY_LAST_CLICKED_LOCATION, null) {
            parseLastClickedLocation(it.getString(KEY_LAST_CLICKED_LOCATION, null))
        }

    suspend fun saveLastClickedLocation(latitude: Double, longitude: Double) {
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val json = JsonCodec.encodeLocation(LastClickedLocation(latitude, longitude))
        editHookSetting { putString(KEY_LAST_CLICKED_LOCATION, json) }
        if (getIsPlaying() && getEnableMockProvider()) {
            MockLocationService.sync(appContext, true)
        }
    }

    fun getLastClickedLocation(): LastClickedLocation? =
        parseLastClickedLocation(
            sharedPrefs()?.getString(KEY_LAST_CLICKED_LOCATION, null)
                ?: localPrefs.getString(KEY_LAST_CLICKED_LOCATION, null)
        )

    suspend fun clearLastClickedLocation() {
        editHookSetting { remove(KEY_LAST_CLICKED_LOCATION) }
        saveIsPlaying(false)
        Log.d(tag, "Cleared 'LastClickedLocation' and set 'IsPlaying' to false")
    }

    private fun parseLastClickedLocation(json: String?): LastClickedLocation? {
        if (json == null) return null
        return try {
            JsonCodec.decodeLocation(json)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing LastClickedLocation: ${e.message}")
            null
        }
    }
    // endregion

    // region Use Accuracy / Accuracy (hook-shared)
    fun getUseAccuracyFlow(): Flow<Boolean> = sharedFlow(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) { it.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) }
    suspend fun saveUseAccuracy(useAccuracy: Boolean) {
        editHookSetting { putBoolean(KEY_USE_ACCURACY, useAccuracy) }
    }
    fun getUseAccuracy(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY)
        ?: localPrefs.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY)

    fun getAccuracyFlow(): Flow<Double> = sharedFlow(KEY_ACCURACY, DEFAULT_ACCURACY) { readSharedDouble(KEY_ACCURACY, DEFAULT_ACCURACY) }
    suspend fun saveAccuracy(accuracy: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(accuracy)
        editHookSetting { putLong(KEY_ACCURACY, bits) }
    }
    fun getAccuracy(): Double {
        val shared = sharedPrefs()
        if (shared != null) {
            return readSharedDouble(KEY_ACCURACY, DEFAULT_ACCURACY)
        }
        val bits = localPrefs.getLong(KEY_ACCURACY, java.lang.Double.doubleToRawLongBits(DEFAULT_ACCURACY))
        return java.lang.Double.longBitsToDouble(bits)
    }
    // endregion

    // region Use Altitude / Altitude (hook-shared)
    fun getUseAltitudeFlow(): Flow<Boolean> = sharedFlow(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) { it.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) }
    suspend fun saveUseAltitude(useAltitude: Boolean) = editHookSetting { putBoolean(KEY_USE_ALTITUDE, useAltitude) }
    fun getUseAltitude(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) ?: DEFAULT_USE_ALTITUDE

    fun getAltitudeFlow(): Flow<Double> = sharedFlow(KEY_ALTITUDE, DEFAULT_ALTITUDE) { readSharedDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE) }
    suspend fun saveAltitude(altitude: Double) = editHookSetting { putLong(KEY_ALTITUDE, java.lang.Double.doubleToRawLongBits(altitude)) }
    fun getAltitude(): Double = readSharedDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE)
    // endregion

    // region Use Randomize / Randomize Radius (hook-shared)
    fun getUseRandomizeFlow(): Flow<Boolean> = sharedFlow(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) { it.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) }
    suspend fun saveUseRandomize(randomize: Boolean) = editHookSetting { putBoolean(KEY_USE_RANDOMIZE, randomize) }
    fun getUseRandomize(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) ?: DEFAULT_USE_RANDOMIZE

    fun getRandomizeRadiusFlow(): Flow<Double> = sharedFlow(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) { readSharedDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) }
    suspend fun saveRandomizeRadius(radius: Double) = editHookSetting { putLong(KEY_RANDOMIZE_RADIUS, java.lang.Double.doubleToRawLongBits(radius)) }
    fun getRandomizeRadius(): Double = readSharedDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS)
    // endregion

    // region Vertical Accuracy (hook-shared)
    fun getUseVerticalAccuracyFlow(): Flow<Boolean> = sharedFlow(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) { it.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) }
    suspend fun saveUseVerticalAccuracy(useVerticalAccuracy: Boolean) = editHookSetting { putBoolean(KEY_USE_VERTICAL_ACCURACY, useVerticalAccuracy) }
    fun getUseVerticalAccuracy(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) ?: DEFAULT_USE_VERTICAL_ACCURACY

    fun getVerticalAccuracyFlow(): Flow<Float> = sharedFlow(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) { it.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) }
    suspend fun saveVerticalAccuracy(verticalAccuracy: Float) = editHookSetting { putFloat(KEY_VERTICAL_ACCURACY, verticalAccuracy) }
    fun getVerticalAccuracy(): Float = sharedPrefs()?.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) ?: DEFAULT_VERTICAL_ACCURACY
    // endregion

    // region Mean Sea Level (hook-shared)
    fun getUseMeanSeaLevelFlow(): Flow<Boolean> = sharedFlow(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) }
    suspend fun saveUseMeanSeaLevel(useMeanSeaLevel: Boolean) = editHookSetting { putBoolean(KEY_USE_MEAN_SEA_LEVEL, useMeanSeaLevel) }
    fun getUseMeanSeaLevel(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) ?: DEFAULT_USE_MEAN_SEA_LEVEL

    fun getMeanSeaLevelFlow(): Flow<Double> = sharedFlow(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) { readSharedDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) }
    suspend fun saveMeanSeaLevel(meanSeaLevel: Double) = editHookSetting { putLong(KEY_MEAN_SEA_LEVEL, java.lang.Double.doubleToRawLongBits(meanSeaLevel)) }
    fun getMeanSeaLevel(): Double = readSharedDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL)

    fun getUseMeanSeaLevelAccuracyFlow(): Flow<Boolean> = sharedFlow(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveUseMeanSeaLevelAccuracy(useMeanSeaLevelAccuracy: Boolean) = editHookSetting { putBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, useMeanSeaLevelAccuracy) }
    fun getUseMeanSeaLevelAccuracy(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY

    fun getMeanSeaLevelAccuracyFlow(): Flow<Float> = sharedFlow(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) { it.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveMeanSeaLevelAccuracy(meanSeaLevelAccuracy: Float) = editHookSetting { putFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, meanSeaLevelAccuracy) }
    fun getMeanSeaLevelAccuracy(): Float = sharedPrefs()?.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
    // endregion

    // region Speed (hook-shared)
    fun getUseSpeedFlow(): Flow<Boolean> = sharedFlow(KEY_USE_SPEED, DEFAULT_USE_SPEED) { it.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) }
    suspend fun saveUseSpeed(useSpeed: Boolean) = editHookSetting { putBoolean(KEY_USE_SPEED, useSpeed) }
    fun getUseSpeed(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) ?: DEFAULT_USE_SPEED

    fun getSpeedFlow(): Flow<Float> = sharedFlow(KEY_SPEED, DEFAULT_SPEED) { it.getFloat(KEY_SPEED, DEFAULT_SPEED) }
    suspend fun saveSpeed(speed: Float) = editHookSetting { putFloat(KEY_SPEED, speed) }
    fun getSpeed(): Float = sharedPrefs()?.getFloat(KEY_SPEED, DEFAULT_SPEED) ?: DEFAULT_SPEED

    fun getUseSpeedAccuracyFlow(): Flow<Boolean> = sharedFlow(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) { it.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) }
    suspend fun saveUseSpeedAccuracy(useSpeedAccuracy: Boolean) = editHookSetting { putBoolean(KEY_USE_SPEED_ACCURACY, useSpeedAccuracy) }
    fun getUseSpeedAccuracy(): Boolean = sharedPrefs()?.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) ?: DEFAULT_USE_SPEED_ACCURACY

    fun getSpeedAccuracyFlow(): Flow<Float> = sharedFlow(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) { it.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) }
    suspend fun saveSpeedAccuracy(speedAccuracy: Float) = editHookSetting { putFloat(KEY_SPEED_ACCURACY, speedAccuracy) }
    fun getSpeedAccuracy(): Float = sharedPrefs()?.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) ?: DEFAULT_SPEED_ACCURACY
    // endregion

    // region Enable System-Level Hooks (hook-shared)
    fun getEnableSystemHooksFlow(): Flow<Boolean> = sharedFlow(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) { it.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) }
    suspend fun saveEnableSystemHooks(enabled: Boolean) = editHookSetting { putBoolean(KEY_ENABLE_SYSTEM_HOOKS, enabled) }
    fun getEnableSystemHooks(): Boolean = sharedPrefs()?.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) ?: DEFAULT_ENABLE_SYSTEM_HOOKS

    fun getEnableGlobalHooksFlow(): Flow<Boolean> = sharedFlow(KEY_ENABLE_GLOBAL_HOOKS, DEFAULT_ENABLE_GLOBAL_HOOKS) { it.getBoolean(KEY_ENABLE_GLOBAL_HOOKS, DEFAULT_ENABLE_GLOBAL_HOOKS) }
    suspend fun saveEnableGlobalHooks(enabled: Boolean) = editHookSetting { putBoolean(KEY_ENABLE_GLOBAL_HOOKS, enabled) }
    fun getEnableGlobalHooks(): Boolean = sharedPrefs()?.getBoolean(KEY_ENABLE_GLOBAL_HOOKS, DEFAULT_ENABLE_GLOBAL_HOOKS) ?: DEFAULT_ENABLE_GLOBAL_HOOKS

    fun getEnableRootModeFlow(): Flow<Boolean> = sharedFlow(KEY_ENABLE_ROOT_MODE, DEFAULT_ENABLE_ROOT_MODE) { it.getBoolean(KEY_ENABLE_ROOT_MODE, DEFAULT_ENABLE_ROOT_MODE) }
    suspend fun saveEnableRootMode(enabled: Boolean) = editHookSetting { putBoolean(KEY_ENABLE_ROOT_MODE, enabled) }
    fun getEnableRootMode(): Boolean = sharedPrefs()?.getBoolean(KEY_ENABLE_ROOT_MODE, DEFAULT_ENABLE_ROOT_MODE) ?: DEFAULT_ENABLE_ROOT_MODE
    // endregion

    // region LocationMode (hook-shared)
    fun getLocationModeFlow(): Flow<LocationMode> = sharedFlow(KEY_LOCATION_MODE, LocationMode.APPLICATION_HOOK) {
        LocationMode.fromStored(it.getString(KEY_LOCATION_MODE, DEFAULT_LOCATION_MODE))
    }
    suspend fun saveLocationMode(mode: LocationMode) {
        val mockProvider = mode == LocationMode.MOCK_PROVIDER
        val systemHooks = mode == LocationMode.SYSTEM_HOOK || mode == LocationMode.GLOBAL_HOOK
        val globalHooks = mode == LocationMode.GLOBAL_HOOK
        val rootMode = mode == LocationMode.ROOT_MODE

        editHookSetting {
            putString(KEY_LOCATION_MODE, mode.storedValue)
            putBoolean(KEY_ENABLE_MOCK_PROVIDER, mockProvider)
            putBoolean(KEY_ENABLE_SYSTEM_HOOKS, systemHooks)
            putBoolean(KEY_ENABLE_GLOBAL_HOOKS, globalHooks)
            putBoolean(KEY_ENABLE_ROOT_MODE, rootMode)
        }
        val isPlaying = getIsPlaying()
        MockLocationService.sync(appContext, mockProvider && isPlaying)
        io.github.ceigt.geolocation.manager.root.RootLocationService.sync(appContext, rootMode && isPlaying)
    }
    fun getLocationMode(): LocationMode =
        LocationMode.fromStored(sharedPrefs()?.getString(KEY_LOCATION_MODE, DEFAULT_LOCATION_MODE))

    fun getMockFlagPolicyFlow(): Flow<MockFlagPolicy> = sharedFlow(KEY_MOCK_FLAG_POLICY, MockFlagPolicy.HIDE_ALL) {
        MockFlagPolicy.fromStored(it.getString(KEY_MOCK_FLAG_POLICY, DEFAULT_MOCK_FLAG_POLICY))
    }
    suspend fun saveMockFlagPolicy(policy: MockFlagPolicy) = editHookSetting {
        putString(KEY_MOCK_FLAG_POLICY, policy.storedValue)
    }
    fun getMockFlagPolicy(): MockFlagPolicy =
        MockFlagPolicy.fromStored(sharedPrefs()?.getString(KEY_MOCK_FLAG_POLICY, DEFAULT_MOCK_FLAG_POLICY))

    fun getHookInstallPolicyFlow(): Flow<HookInstallPolicy> = sharedFlow(KEY_HOOK_INSTALL_POLICY, HookInstallPolicy.IMMEDIATE) {
        HookInstallPolicy.fromStored(it.getString(KEY_HOOK_INSTALL_POLICY, DEFAULT_HOOK_INSTALL_POLICY))
    }
    suspend fun saveHookInstallPolicy(policy: HookInstallPolicy) = editHookSetting {
        putString(KEY_HOOK_INSTALL_POLICY, policy.storedValue)
    }
    fun getHookInstallPolicy(): HookInstallPolicy =
        HookInstallPolicy.fromStored(sharedPrefs()?.getString(KEY_HOOK_INSTALL_POLICY, DEFAULT_HOOK_INSTALL_POLICY))

    fun getAppHookInstallPoliciesFlow(): Flow<Map<String, HookInstallPolicy>> = sharedFlow(KEY_APP_HOOK_INSTALL_POLICIES, emptyMap()) {
        parseAppHookInstallPolicies(it.getString(KEY_APP_HOOK_INSTALL_POLICIES, null))
    }
    suspend fun saveAppHookInstallPolicy(packageName: String, policy: HookInstallPolicy) {
        val current = parseAppHookInstallPolicies(sharedPrefs()?.getString(KEY_APP_HOOK_INSTALL_POLICIES, null)).toMutableMap()
        current[packageName] = policy
        val json = JsonCodec.encodeStringMap(current.mapValues { it.value.storedValue })
        editHookSetting { putString(KEY_APP_HOOK_INSTALL_POLICIES, json) }
    }
    private fun parseAppHookInstallPolicies(json: String?): Map<String, HookInstallPolicy> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            JsonCodec.decodeStringMap(json).mapValues { HookInstallPolicy.fromStored(it.value) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    // endregion

    // region GNSS & Jitter settings (hook-shared)
    fun getGnssConstellationPresetFlow(): Flow<io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset> =
        sharedFlow(KEY_GNSS_CONSTELLATION_PRESET, io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.AUTO_MIXED) {
            io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.fromStored(it.getString(KEY_GNSS_CONSTELLATION_PRESET, DEFAULT_GNSS_CONSTELLATION_PRESET))
        }
    suspend fun saveGnssConstellationPreset(preset: io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset) = editHookSetting {
        putString(KEY_GNSS_CONSTELLATION_PRESET, preset.storedValue)
    }

    fun getGnssSignalStrengthPresetFlow(): Flow<io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset> =
        sharedFlow(KEY_GNSS_SIGNAL_STRENGTH_PRESET, io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.NORMAL) {
            io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.fromStored(it.getString(KEY_GNSS_SIGNAL_STRENGTH_PRESET, DEFAULT_GNSS_SIGNAL_STRENGTH_PRESET))
        }
    suspend fun saveGnssSignalStrengthPreset(preset: io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset) = editHookSetting {
        putString(KEY_GNSS_SIGNAL_STRENGTH_PRESET, preset.storedValue)
    }

    fun getGnssVisibleSatellitesFlow(): Flow<Int> = sharedFlow(KEY_GNSS_VISIBLE_SATELLITES, DEFAULT_GNSS_VISIBLE_SATELLITES) {
        it.getInt(KEY_GNSS_VISIBLE_SATELLITES, DEFAULT_GNSS_VISIBLE_SATELLITES)
    }
    suspend fun saveGnssVisibleSatellites(count: Int) = editHookSetting {
        putInt(KEY_GNSS_VISIBLE_SATELLITES, count)
    }

    fun getGnssUsedSatellitesFlow(): Flow<Int> = sharedFlow(KEY_GNSS_USED_SATELLITES, DEFAULT_GNSS_USED_SATELLITES) {
        it.getInt(KEY_GNSS_USED_SATELLITES, DEFAULT_GNSS_USED_SATELLITES)
    }
    suspend fun saveGnssUsedSatellites(count: Int) = editHookSetting {
        putInt(KEY_GNSS_USED_SATELLITES, count)
    }

    fun getGnssJitterEnabledFlow(): Flow<Boolean> = sharedFlow(KEY_GNSS_JITTER_ENABLED, DEFAULT_GNSS_JITTER_ENABLED) {
        it.getBoolean(KEY_GNSS_JITTER_ENABLED, DEFAULT_GNSS_JITTER_ENABLED)
    }
    suspend fun saveGnssJitterEnabled(enabled: Boolean) = editHookSetting {
        putBoolean(KEY_GNSS_JITTER_ENABLED, enabled)
    }

    fun getGnssJitterAlgorithmFlow(): Flow<io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm> =
        sharedFlow(KEY_GNSS_JITTER_ALGORITHM, io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FIRST_ORDER_MARKOV) {
            io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.fromStored(it.getString(KEY_GNSS_JITTER_ALGORITHM, DEFAULT_GNSS_JITTER_ALGORITHM))
        }
    suspend fun saveGnssJitterAlgorithm(algo: io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm) = editHookSetting {
        putString(KEY_GNSS_JITTER_ALGORITHM, algo.storedValue)
    }

    fun getGnssJitterRadiusFlow(): Flow<Double> = sharedFlow(KEY_GNSS_JITTER_RADIUS, DEFAULT_GNSS_JITTER_RADIUS) {
        readSharedDouble(KEY_GNSS_JITTER_RADIUS, DEFAULT_GNSS_JITTER_RADIUS)
    }
    suspend fun saveGnssJitterRadius(radius: Double) = editHookSetting {
        putLong(KEY_GNSS_JITTER_RADIUS, java.lang.Double.doubleToRawLongBits(radius))
    }

    fun getGnssJitterSmoothingFlow(): Flow<Double> = sharedFlow(KEY_GNSS_JITTER_SMOOTHING, DEFAULT_GNSS_JITTER_SMOOTHING) {
        readSharedDouble(KEY_GNSS_JITTER_SMOOTHING, DEFAULT_GNSS_JITTER_SMOOTHING)
    }
    suspend fun saveGnssJitterSmoothing(smoothing: Double) = editHookSetting {
        putLong(KEY_GNSS_JITTER_SMOOTHING, java.lang.Double.doubleToRawLongBits(smoothing))
    }

    fun getGnssJitterDriftSpeedFlow(): Flow<Double> = sharedFlow(KEY_GNSS_JITTER_DRIFT_SPEED, DEFAULT_GNSS_JITTER_DRIFT_SPEED) {
        readSharedDouble(KEY_GNSS_JITTER_DRIFT_SPEED, DEFAULT_GNSS_JITTER_DRIFT_SPEED)
    }
    suspend fun saveGnssJitterDriftSpeed(speed: Double) = editHookSetting {
        putLong(KEY_GNSS_JITTER_DRIFT_SPEED, java.lang.Double.doubleToRawLongBits(speed))
    }
    // endregion

    // region Channel Settings & Address Metadata (hook-shared)
    fun getChannelSettingsFlow(): Flow<Map<String, io.github.ceigt.geolocation.data.model.channel.ChannelSettings>> =
        sharedFlow(KEY_CHANNEL_SETTINGS, emptyMap()) {
            JsonCodec.decodeChannelSettingsMap(it.getString(KEY_CHANNEL_SETTINGS, null))
        }

    suspend fun saveChannelSettings(channel: io.github.ceigt.geolocation.data.model.channel.LocationChannel, settings: io.github.ceigt.geolocation.data.model.channel.ChannelSettings) = editHookSetting {
        val current = JsonCodec.decodeChannelSettingsMap(sharedPrefs()?.getString(KEY_CHANNEL_SETTINGS, null)).toMutableMap()
        current[channel.channelKey] = settings
        putString(KEY_CHANNEL_SETTINGS, JsonCodec.encodeChannelSettingsMap(current))
    }

    fun getChannelSettings(channel: io.github.ceigt.geolocation.data.model.channel.LocationChannel): io.github.ceigt.geolocation.data.model.channel.ChannelSettings {
        val map = JsonCodec.decodeChannelSettingsMap(sharedPrefs()?.getString(KEY_CHANNEL_SETTINGS, null))
        return map[channel.channelKey] ?: io.github.ceigt.geolocation.data.model.channel.ChannelSettings()
    }

    fun getLocationAddressMetadataFlow(): Flow<io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata> =
        sharedFlow(KEY_LOCATION_ADDRESS_METADATA, io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata()) {
            JsonCodec.decodeAddressMetadata(it.getString(KEY_LOCATION_ADDRESS_METADATA, null))
        }

    suspend fun saveLocationAddressMetadata(metadata: io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata) = editHookSetting {
        putString(KEY_LOCATION_ADDRESS_METADATA, JsonCodec.encodeAddressMetadata(metadata))
    }

    fun getLocationAddressMetadata(): io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata =
        JsonCodec.decodeAddressMetadata(sharedPrefs()?.getString(KEY_LOCATION_ADDRESS_METADATA, null))
    // endregion

    // region Cadence Settings (hook-shared)
    fun getGlobalCadenceModeFlow(): Flow<io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode> =
        sharedFlow(KEY_GLOBAL_CADENCE_MODE, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO) {
            io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.fromStored(it.getString(KEY_GLOBAL_CADENCE_MODE, DEFAULT_GLOBAL_CADENCE_MODE))
        }
    suspend fun saveGlobalCadenceMode(mode: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode) = editHookSetting {
        putString(KEY_GLOBAL_CADENCE_MODE, mode.storedValue)
    }

    fun getGlobalCadenceChannelFlow(): Flow<io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel> =
        sharedFlow(KEY_GLOBAL_CADENCE_CHANNEL, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.ACCELEROMETER) {
            io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.fromStored(it.getString(KEY_GLOBAL_CADENCE_CHANNEL, DEFAULT_GLOBAL_CADENCE_CHANNEL))
        }
    suspend fun saveGlobalCadenceChannel(channel: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel) = editHookSetting {
        putString(KEY_GLOBAL_CADENCE_CHANNEL, channel.storedValue)
    }

    fun getGlobalCustomSpmFlow(): Flow<Int> = sharedFlow(KEY_GLOBAL_CUSTOM_SPM, DEFAULT_GLOBAL_CUSTOM_SPM) {
        it.getInt(KEY_GLOBAL_CUSTOM_SPM, DEFAULT_GLOBAL_CUSTOM_SPM)
    }
    suspend fun saveGlobalCustomSpm(spm: Int) = editHookSetting {
        putInt(KEY_GLOBAL_CUSTOM_SPM, spm)
    }

    fun getAppCadenceConfigsFlow(): Flow<Map<String, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig>> =
        sharedFlow(KEY_APP_CADENCE_CONFIGS, emptyMap()) {
            JsonCodec.decodeCadenceConfigs(it.getString(KEY_APP_CADENCE_CONFIGS, null))
        }
    suspend fun saveAppCadenceConfig(packageName: String, config: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig) {
        val current = JsonCodec.decodeCadenceConfigs(sharedPrefs()?.getString(KEY_APP_CADENCE_CONFIGS, null)).toMutableMap()
        current[packageName] = config
        editHookSetting {
            putString(KEY_APP_CADENCE_CONFIGS, JsonCodec.encodeCadenceConfigs(current))
        }
    }
    // endregion

    // region BLE Profiles (hook-shared)
    fun getBleProfilesFlow(): Flow<List<io.github.ceigt.geolocation.domain.ble.BleProfile>> =
        sharedFlow(KEY_BLE_PROFILES, emptyList()) {
            JsonCodec.decodeBleProfiles(it.getString(KEY_BLE_PROFILES, null))
        }

    suspend fun saveBleProfiles(profiles: List<io.github.ceigt.geolocation.domain.ble.BleProfile>) = editHookSetting {
        putString(KEY_BLE_PROFILES, JsonCodec.encodeBleProfiles(profiles))
    }

    fun getBleProfiles(): List<io.github.ceigt.geolocation.domain.ble.BleProfile> =
        JsonCodec.decodeBleProfiles(sharedPrefs()?.getString(KEY_BLE_PROFILES, null))

    fun getActiveBleProfileIdFlow(): Flow<String?> = sharedFlow(KEY_ACTIVE_BLE_PROFILE_ID, null) {
        it.getString(KEY_ACTIVE_BLE_PROFILE_ID, null)
    }
    suspend fun saveActiveBleProfileId(id: String?) = editHookSetting {
        if (id == null) remove(KEY_ACTIVE_BLE_PROFILE_ID) else putString(KEY_ACTIVE_BLE_PROFILE_ID, id)
    }

    fun getBleDeliveryModeFlow(): Flow<io.github.ceigt.geolocation.domain.ble.BleDeliveryMode> =
        sharedFlow(KEY_BLE_DELIVERY_MODE, io.github.ceigt.geolocation.domain.ble.BleDeliveryMode.HOOK_VIRTUAL) {
            val v = it.getString(KEY_BLE_DELIVERY_MODE, "HOOK_VIRTUAL")
            runCatching { io.github.ceigt.geolocation.domain.ble.BleDeliveryMode.valueOf(v ?: "HOOK_VIRTUAL") }
                .getOrDefault(io.github.ceigt.geolocation.domain.ble.BleDeliveryMode.HOOK_VIRTUAL)
        }
    suspend fun saveBleDeliveryMode(mode: io.github.ceigt.geolocation.domain.ble.BleDeliveryMode) = editHookSetting {
        putString(KEY_BLE_DELIVERY_MODE, mode.name)
    }
    // endregion

    // region NFC Profiles (hook-shared)
    fun getNfcProfilesFlow(): Flow<List<io.github.ceigt.geolocation.domain.nfc.NfcProfile>> =
        sharedFlow(KEY_NFC_PROFILES, emptyList()) {
            JsonCodec.decodeNfcProfiles(it.getString(KEY_NFC_PROFILES, null))
        }

    suspend fun saveNfcProfiles(profiles: List<io.github.ceigt.geolocation.domain.nfc.NfcProfile>) = editHookSetting {
        putString(KEY_NFC_PROFILES, JsonCodec.encodeNfcProfiles(profiles))
    }

    fun getNfcProfiles(): List<io.github.ceigt.geolocation.domain.nfc.NfcProfile> =
        JsonCodec.decodeNfcProfiles(sharedPrefs()?.getString(KEY_NFC_PROFILES, null))

    fun getActiveNfcProfileIdFlow(): Flow<String?> = sharedFlow(KEY_ACTIVE_NFC_PROFILE_ID, null) {
        it.getString(KEY_ACTIVE_NFC_PROFILE_ID, null)
    }
    suspend fun saveActiveNfcProfileId(id: String?) = editHookSetting {
        if (id == null) remove(KEY_ACTIVE_NFC_PROFILE_ID) else putString(KEY_ACTIVE_NFC_PROFILE_ID, id)
    }
    // endregion

    // region Route Favorites (hook-shared)
    fun getRouteFavoritesFlow(): Flow<List<io.github.ceigt.geolocation.data.model.route.RouteFavorite>> =
        sharedFlow(KEY_ROUTE_FAVORITES, emptyList()) {
            JsonCodec.decodeRouteFavorites(it.getString(KEY_ROUTE_FAVORITES, null))
        }

    suspend fun saveRouteFavorites(favorites: List<io.github.ceigt.geolocation.data.model.route.RouteFavorite>) = editHookSetting {
        putString(KEY_ROUTE_FAVORITES, JsonCodec.encodeRouteFavorites(favorites))
    }

    fun getRouteFavorites(): List<io.github.ceigt.geolocation.data.model.route.RouteFavorite> =
        JsonCodec.decodeRouteFavorites(sharedPrefs()?.getString(KEY_ROUTE_FAVORITES, null))
    // endregion

    // region Environment Profiles (hook-shared)
    fun getEnvironmentProfilesFlow(): Flow<List<io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile>> =
        sharedFlow(KEY_ENVIRONMENT_PROFILES, emptyList()) {
            JsonCodec.decodeEnvironmentProfiles(it.getString(KEY_ENVIRONMENT_PROFILES, null))
        }

    suspend fun saveEnvironmentProfiles(profiles: List<io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile>) = editHookSetting {
        putString(KEY_ENVIRONMENT_PROFILES, JsonCodec.encodeEnvironmentProfiles(profiles))
    }

    fun getEnvironmentProfiles(): List<io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile> =
        JsonCodec.decodeEnvironmentProfiles(sharedPrefs()?.getString(KEY_ENVIRONMENT_PROFILES, null))

    fun getActiveEnvironmentProfileIdFlow(): Flow<String?> = sharedFlow(KEY_ACTIVE_ENVIRONMENT_PROFILE_ID, null) {
        it.getString(KEY_ACTIVE_ENVIRONMENT_PROFILE_ID, null)
    }
    suspend fun saveActiveEnvironmentProfileId(id: String?) = editHookSetting {
        if (id == null) remove(KEY_ACTIVE_ENVIRONMENT_PROFILE_ID) else putString(KEY_ACTIVE_ENVIRONMENT_PROFILE_ID, id)
    }
    // endregion

    // region Mock Provider compatibility mode (local)
    fun getEnableMockProviderFlow(): Flow<Boolean> = localFlow(KEY_ENABLE_MOCK_PROVIDER) {
        it.getBoolean(KEY_ENABLE_MOCK_PROVIDER, DEFAULT_ENABLE_MOCK_PROVIDER)
    }

    /** Commit both mode flags together before reconciling the local provider service. */
    suspend fun saveLocationMode(mockProvider: Boolean, systemHooks: Boolean) {
        require(!(mockProvider && systemHooks))
        val mode = when {
            mockProvider -> LocationMode.MOCK_PROVIDER
            systemHooks -> LocationMode.SYSTEM_HOOK
            else -> LocationMode.APPLICATION_HOOK
        }
        editHookSetting {
            putString(KEY_LOCATION_MODE, mode.storedValue)
            putBoolean(KEY_ENABLE_MOCK_PROVIDER, mockProvider)
            putBoolean(KEY_ENABLE_SYSTEM_HOOKS, systemHooks)
            putBoolean(KEY_ENABLE_GLOBAL_HOOKS, false)
            putBoolean(KEY_ENABLE_ROOT_MODE, false)
        }
        MockLocationService.sync(appContext, mockProvider && getIsPlaying())
    }

    suspend fun saveEnableMockProvider(enabled: Boolean) {
        // Mirror the selected mode so already-injected Xposed hooks can immediately become
        // inert while Mock Provider is the active location source.
        editHookSetting { putBoolean(KEY_ENABLE_MOCK_PROVIDER, enabled) }
        val shouldRun = enabled && localPrefs.getBoolean(KEY_IS_PLAYING, false)
        MockLocationService.sync(appContext, shouldRun)
    }

    fun getEnableMockProvider(): Boolean =
        localPrefs.getBoolean(KEY_ENABLE_MOCK_PROVIDER, DEFAULT_ENABLE_MOCK_PROVIDER)
    // endregion

    // region Hide Fake Location Toast (hook-shared)
    fun getHideFakeLocationToastFlow(): Flow<Boolean> = sharedFlow(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) { it.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) }
    suspend fun saveHideFakeLocationToast(hideFakeLocationToast: Boolean) = editHookSetting { putBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, hideFakeLocationToast) }
    fun getHideFakeLocationToast(): Boolean = sharedPrefs()?.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) ?: DEFAULT_HIDE_FAKE_LOCATION_TOAST
    // endregion

    // region Target Apps (hook-shared)
    fun getTargetAppsFlow(): Flow<Set<String>> =
        sharedFlow(KEY_TARGET_APPS, emptySet()) { parseTargetApps(it.getString(KEY_TARGET_APPS, null)) }

    suspend fun saveTargetApps(packageNames: Set<String>) {
        val normalized = packageNames
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val json = JsonCodec.encodeStrings(normalized)
        editHookSetting { putString(KEY_TARGET_APPS, json) }
    }

    private fun parseTargetApps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            JsonCodec.decodeStringSet(json)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing target apps: ${e.message}")
            emptySet()
        }
    }
    // endregion

    // region Per-app coordinate systems (hook-shared)
    fun getAppCoordinateSystemsFlow(): Flow<Map<String, CoordinateSystem>> =
        sharedFlow(KEY_APP_COORDINATE_SYSTEMS, emptyMap()) {
            parseAppCoordinateSystems(it.getString(KEY_APP_COORDINATE_SYSTEMS, null))
        }

    suspend fun saveAppCoordinateSystem(packageName: String, coordinateSystem: CoordinateSystem) {
        appCoordinateMutex.withLock {
            val updated = getAppCoordinateSystems().toMutableMap()
            if (coordinateSystem == CoordinateSystem.WGS84) {
                updated.remove(packageName)
            } else {
                updated[packageName] = coordinateSystem
            }
            editHookSetting {
                putString(
                    KEY_APP_COORDINATE_SYSTEMS,
                    JsonCodec.encodeStringMap(updated.mapValues { it.value.name })
                )
            }
        }
    }

    fun getAppCoordinateSystems(): Map<String, CoordinateSystem> =
        parseAppCoordinateSystems(sharedPrefs()?.getString(KEY_APP_COORDINATE_SYSTEMS, null))

    private fun parseAppCoordinateSystems(json: String?): Map<String, CoordinateSystem> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            JsonCodec.decodeStringMap(json).mapValues { CoordinateSystem.fromStored(it.value) }
        }.onFailure {
            Log.e(tag, "Error parsing app coordinate systems: ${it.message}")
        }.getOrDefault(emptyMap())
    }
    // endregion

    // region Target app list display (local)
    fun getShowSystemAppsFlow(): Flow<Boolean> =
        localFlow(KEY_SHOW_SYSTEM_APPS) { it.getBoolean(KEY_SHOW_SYSTEM_APPS, false) }

    fun saveShowSystemApps(show: Boolean) {
        editLocal { putBoolean(KEY_SHOW_SYSTEM_APPS, show) }
    }
    // endregion

    // region Favorites (local)
    fun getFavoritesFlow(): Flow<List<FavoriteLocation>> =
        localFlow(KEY_FAVORITES) { parseFavorites(it.getString(KEY_FAVORITES, null)) }

    suspend fun addFavorite(favorite: FavoriteLocation) {
        favoritesMutex.withLock {
            val updated = favoritesForEdit().toMutableList().apply { add(favorite) }
            saveFavorites(updated)
        }
    }

    suspend fun removeFavorite(favorite: FavoriteLocation) {
        favoritesMutex.withLock {
            val updated = favoritesForEdit().toMutableList().apply { remove(favorite) }
            saveFavorites(updated)
        }
    }

    fun getFavorites(): List<FavoriteLocation> = parseFavorites(localPrefs.getString(KEY_FAVORITES, null))

    private fun favoritesForEdit(): List<FavoriteLocation> {
        val json = localPrefs.getString(KEY_FAVORITES, null)
        return if (json.isNullOrBlank()) emptyList() else JsonCodec.decodeFavorites(json)
    }

    private suspend fun saveFavorites(favorites: List<FavoriteLocation>) {
        val json = JsonCodec.encodeFavorites(favorites)
        editLocalChecked(KEY_FAVORITES) { putString(KEY_FAVORITES, json) }
    }

    private fun parseFavorites(json: String?): List<FavoriteLocation> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            JsonCodec.decodeFavorites(json)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Favorites: ${e.message}")
            emptyList()
        }
    }
    // endregion

    // region Broadcast Control (local)
    fun getEnableBroadcastControlFlow(): Flow<Boolean> = localFlow(KEY_ENABLE_BROADCAST_CONTROL) { it.getBoolean(KEY_ENABLE_BROADCAST_CONTROL, DEFAULT_ENABLE_BROADCAST_CONTROL) }
    suspend fun saveEnableBroadcastControl(enable: Boolean) = editLocalChecked(KEY_ENABLE_BROADCAST_CONTROL) { putBoolean(KEY_ENABLE_BROADCAST_CONTROL, enable) }
    // endregion

    // region Language (local; shared with LocaleController)
    fun getLanguageTagFlow(): Flow<String> = localFlow(KEY_LANGUAGE_TAG) { it.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG }
    suspend fun saveLanguageTag(languageTag: String) = editLocalChecked(KEY_LANGUAGE_TAG) { putString(KEY_LANGUAGE_TAG, languageTag) }
    // endregion

    // region Baidu Map AK (local)
    fun getBaiduMapAkFlow(): Flow<String> = localFlow(KEY_BAIDU_MAP_AK) {
        CredentialStore.read(it, KEY_BAIDU_MAP_AK, DEFAULT_BAIDU_MAP_AK)
    }

    suspend fun saveBaiduMapAk(ak: String) = editLocalChecked(KEY_BAIDU_MAP_AK) {
        putString(KEY_BAIDU_MAP_AK, CredentialStore.encrypted(ak.trim()))
    }
    // endregion

    // region Web map provider and credentials (local)
    fun getMapProviderFlow(): Flow<MapProvider> = localFlow(KEY_MAP_PROVIDER) {
        MapProvider.fromStored(it.getString(KEY_MAP_PROVIDER, DEFAULT_MAP_PROVIDER))
    }

    suspend fun saveMapProvider(provider: MapProvider) =
        editLocalChecked(KEY_MAP_PROVIDER) { putString(KEY_MAP_PROVIDER, provider.storedValue) }

    fun getOsmTileServerFlow(): Flow<OsmTileServer> = localFlow(KEY_OSM_TILE_SERVER) {
        OsmTileServer.fromStored(it.getString(KEY_OSM_TILE_SERVER, DEFAULT_OSM_TILE_SERVER))
    }

    suspend fun saveOsmTileServer(server: OsmTileServer) =
        editLocalChecked(KEY_OSM_TILE_SERVER) { putString(KEY_OSM_TILE_SERVER, server.storedValue) }

    fun getAmapWebKeyFlow(): Flow<String> = localFlow(KEY_AMAP_WEB_KEY) {
        CredentialStore.read(it, KEY_AMAP_WEB_KEY, DEFAULT_AMAP_WEB_KEY)
    }

    suspend fun saveAmapWebKey(key: String) =
        editLocalChecked(KEY_AMAP_WEB_KEY) { putString(KEY_AMAP_WEB_KEY, CredentialStore.encrypted(key.trim())) }

    fun getAmapSecurityCodeFlow(): Flow<String> = localFlow(KEY_AMAP_SECURITY_CODE) {
        CredentialStore.read(it, KEY_AMAP_SECURITY_CODE, DEFAULT_AMAP_SECURITY_CODE)
    }

    suspend fun saveAmapSecurityCode(code: String) =
        editLocalChecked(KEY_AMAP_SECURITY_CODE) {
            putString(KEY_AMAP_SECURITY_CODE, CredentialStore.encrypted(code.trim()))
        }

    fun getGoogleMapsApiKeyFlow(): Flow<String> = localFlow(KEY_GOOGLE_MAPS_API_KEY) {
        CredentialStore.read(it, KEY_GOOGLE_MAPS_API_KEY, DEFAULT_GOOGLE_MAPS_API_KEY)
    }

    suspend fun saveGoogleMapsApiKey(key: String) =
        editLocalChecked(KEY_GOOGLE_MAPS_API_KEY) {
            putString(KEY_GOOGLE_MAPS_API_KEY, CredentialStore.encrypted(key.trim()))
        }
    // endregion
}
