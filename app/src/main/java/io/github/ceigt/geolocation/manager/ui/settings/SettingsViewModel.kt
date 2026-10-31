//SettingsViewModel.kt
package io.github.ceigt.geolocation.manager.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ceigt.geolocation.data.*
import io.github.ceigt.geolocation.data.model.mode.HookInstallPolicy
import io.github.ceigt.geolocation.data.model.mode.LocationMode
import io.github.ceigt.geolocation.data.model.mode.MockFlagPolicy
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.manager.App
import kotlinx.coroutines.flow.*

/** One-shot messages surfaced to the settings UI. */
sealed interface SystemHooksEvent {
    data object UnsupportedSystemVersion : SystemHooksEvent
    data object ModuleNotActive : SystemHooksEvent
    data object TargetAppScopeRequired : SystemHooksEvent
    data class ScopeSetupRequired(val missingPackages: List<String>) : SystemHooksEvent
    data object RootPermissionRequired : SystemHooksEvent
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    private val _saveError = MutableStateFlow(false)
    val saveError = _saveError.asStateFlow()
    fun clearSaveError() { _saveError.value = false }
    private val _failedSaveRevision = MutableStateFlow(0L)
    val failedSaveRevision = _failedSaveRevision.asStateFlow()
    private val writer = SettingsWriter { error ->
        android.util.Log.e("SettingsViewModel", "Setting save failed: ${error.javaClass.simpleName}")
        _failedSaveRevision.update { it + 1L }
        _saveError.value = true
    }
    private fun enqueueSave(operation: suspend () -> Unit) { writer.enqueue(operation) }
    override fun onCleared() { writer.close(); super.onCleared() }

    private inner class Preference<T>(
        initialValue: T,
        flow: Flow<T>,
        private val saveOperation: suspend (T) -> Unit,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        val state = flow.stateIn(scope, SharingStarted.Eagerly, initialValue)
        fun setValue(value: T) = enqueueSave { saveOperation(value) }
    }

    // Preferences for Accuracy
    private val _useAccuracyPreference = Preference(
        DEFAULT_USE_ACCURACY,
        preferencesRepository.getUseAccuracyFlow(),
        preferencesRepository::saveUseAccuracy,
        viewModelScope
    )
    val useAccuracy: StateFlow<Boolean> = _useAccuracyPreference.state

    private val _accuracyPreference = Preference(
        DEFAULT_ACCURACY,
        preferencesRepository.getAccuracyFlow(),
        preferencesRepository::saveAccuracy,
        viewModelScope
    )
    val accuracy: StateFlow<Double> = _accuracyPreference.state

    // Preferences for Altitude
    private val _useAltitudePreference = Preference(
        DEFAULT_USE_ALTITUDE,
        preferencesRepository.getUseAltitudeFlow(),
        preferencesRepository::saveUseAltitude,
        viewModelScope
    )
    val useAltitude: StateFlow<Boolean> = _useAltitudePreference.state

    private val _altitudePreference = Preference(
        DEFAULT_ALTITUDE,
        preferencesRepository.getAltitudeFlow(),
        preferencesRepository::saveAltitude,
        viewModelScope
    )
    val altitude: StateFlow<Double> = _altitudePreference.state

    // Preferences for Randomize
    private val _useRandomizePreference = Preference(
        DEFAULT_USE_RANDOMIZE,
        preferencesRepository.getUseRandomizeFlow(),
        preferencesRepository::saveUseRandomize,
        viewModelScope
    )
    val useRandomize: StateFlow<Boolean> = _useRandomizePreference.state

    private val _randomizeRadiusPreference = Preference(
        DEFAULT_RANDOMIZE_RADIUS,
        preferencesRepository.getRandomizeRadiusFlow(),
        preferencesRepository::saveRandomizeRadius,
        viewModelScope
    )
    val randomizeRadius: StateFlow<Double> = _randomizeRadiusPreference.state

    // Preferences for Vertical Accuracy
    private val _useVerticalAccuracyPreference = Preference(
        DEFAULT_USE_VERTICAL_ACCURACY,
        preferencesRepository.getUseVerticalAccuracyFlow(),
        preferencesRepository::saveUseVerticalAccuracy,
        viewModelScope
    )
    val useVerticalAccuracy: StateFlow<Boolean> = _useVerticalAccuracyPreference.state

    private val _verticalAccuracyPreference = Preference(
        DEFAULT_VERTICAL_ACCURACY,
        preferencesRepository.getVerticalAccuracyFlow(),
        preferencesRepository::saveVerticalAccuracy,
        viewModelScope
    )
    val verticalAccuracy: StateFlow<Float> = _verticalAccuracyPreference.state

    // Preferences for Mean Sea Level
    private val _useMeanSeaLevelPreference = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL,
        preferencesRepository.getUseMeanSeaLevelFlow(),
        preferencesRepository::saveUseMeanSeaLevel,
        viewModelScope
    )
    val useMeanSeaLevel: StateFlow<Boolean> = _useMeanSeaLevelPreference.state

    private val _meanSeaLevelPreference = Preference(
        DEFAULT_MEAN_SEA_LEVEL,
        preferencesRepository.getMeanSeaLevelFlow(),
        preferencesRepository::saveMeanSeaLevel,
        viewModelScope
    )
    val meanSeaLevel: StateFlow<Double> = _meanSeaLevelPreference.state

    // Preferences for Mean Sea Level Accuracy
    private val _useMeanSeaLevelAccuracyPreference = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getUseMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveUseMeanSeaLevelAccuracy,
        viewModelScope
    )
    val useMeanSeaLevelAccuracy: StateFlow<Boolean> = _useMeanSeaLevelAccuracyPreference.state

    private val _meanSeaLevelAccuracyPreference = Preference(
        DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveMeanSeaLevelAccuracy,
        viewModelScope
    )
    val meanSeaLevelAccuracy: StateFlow<Float> = _meanSeaLevelAccuracyPreference.state

    // Preferences for Speed
    private val _useSpeedPreference = Preference(
        DEFAULT_USE_SPEED,
        preferencesRepository.getUseSpeedFlow(),
        preferencesRepository::saveUseSpeed,
        viewModelScope
    )
    val useSpeed: StateFlow<Boolean> = _useSpeedPreference.state

    private val _speedPreference = Preference(
        DEFAULT_SPEED,
        preferencesRepository.getSpeedFlow(),
        preferencesRepository::saveSpeed,
        viewModelScope
    )
    val speed: StateFlow<Float> = _speedPreference.state

    // Preferences for Speed Accuracy
    private val _useSpeedAccuracyPreference = Preference(
        DEFAULT_USE_SPEED_ACCURACY,
        preferencesRepository.getUseSpeedAccuracyFlow(),
        preferencesRepository::saveUseSpeedAccuracy,
        viewModelScope
    )
    val useSpeedAccuracy: StateFlow<Boolean> = _useSpeedAccuracyPreference.state

    private val _speedAccuracyPreference = Preference(
        DEFAULT_SPEED_ACCURACY,
        preferencesRepository.getSpeedAccuracyFlow(),
        preferencesRepository::saveSpeedAccuracy,
        viewModelScope
    )
    val speedAccuracy: StateFlow<Float> = _speedAccuracyPreference.state

    // Preferences for Hide Fake Location Toast
    private val _hideFakeLocationToastPreference = Preference(
        DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        preferencesRepository.getHideFakeLocationToastFlow(),
        preferencesRepository::saveHideFakeLocationToast,
        viewModelScope
    )
    val hideFakeLocationToast: StateFlow<Boolean> = _hideFakeLocationToastPreference.state

    // Preference for External Broadcast Control
    private val _enableBroadcastControlPreference = Preference(
        DEFAULT_ENABLE_BROADCAST_CONTROL,
        preferencesRepository.getEnableBroadcastControlFlow(),
        preferencesRepository::saveEnableBroadcastControl,
        viewModelScope
    )
    val enableBroadcastControl: StateFlow<Boolean> = _enableBroadcastControlPreference.state

    private val _enableMockProviderPreference = Preference(
        DEFAULT_ENABLE_MOCK_PROVIDER,
        preferencesRepository.getEnableMockProviderFlow(),
        preferencesRepository::saveEnableMockProvider,
        viewModelScope
    )
    val enableMockProvider: StateFlow<Boolean> = _enableMockProviderPreference.state

    // Preference for System-Level Hooks. The switch only changes after the required scopes are
    // already present in the Xposed manager.
    val enableSystemHooks: StateFlow<Boolean> = preferencesRepository.getEnableSystemHooksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ENABLE_SYSTEM_HOOKS)

    val locationMode: StateFlow<LocationMode> = preferencesRepository.getLocationModeFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocationMode.APPLICATION_HOOK)

    val mockFlagPolicy: StateFlow<MockFlagPolicy> = preferencesRepository.getMockFlagPolicyFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MockFlagPolicy.HIDE_ALL)

    val hookInstallPolicy: StateFlow<HookInstallPolicy> = preferencesRepository.getHookInstallPolicyFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HookInstallPolicy.IMMEDIATE)

    val appHookInstallPolicies: StateFlow<Map<String, HookInstallPolicy>> = preferencesRepository.getAppHookInstallPoliciesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val gnssConstellationPreset: StateFlow<io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset> =
        preferencesRepository.getGnssConstellationPresetFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset.AUTO_MIXED)

    val gnssSignalStrengthPreset: StateFlow<io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset> =
        preferencesRepository.getGnssSignalStrengthPresetFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset.NORMAL)

    val gnssVisibleSatellites: StateFlow<Int> = preferencesRepository.getGnssVisibleSatellitesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GNSS_VISIBLE_SATELLITES)

    val gnssUsedSatellites: StateFlow<Int> = preferencesRepository.getGnssUsedSatellitesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GNSS_USED_SATELLITES)

    val gnssJitterEnabled: StateFlow<Boolean> = preferencesRepository.getGnssJitterEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GNSS_JITTER_ENABLED)

    val gnssJitterAlgorithm: StateFlow<io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm> =
        preferencesRepository.getGnssJitterAlgorithmFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm.FIRST_ORDER_MARKOV)

    val gnssJitterRadius: StateFlow<Double> = preferencesRepository.getGnssJitterRadiusFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GNSS_JITTER_RADIUS)

    val gnssJitterSmoothing: StateFlow<Double> = preferencesRepository.getGnssJitterSmoothingFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GNSS_JITTER_SMOOTHING)

    val gnssJitterDriftSpeed: StateFlow<Double> = preferencesRepository.getGnssJitterDriftSpeedFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GNSS_JITTER_DRIFT_SPEED)

    val globalCadenceMode: StateFlow<io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode> =
        preferencesRepository.getGlobalCadenceModeFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.AUTO)

    val globalCadenceChannel: StateFlow<io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel> =
        preferencesRepository.getGlobalCadenceChannelFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.ACCELEROMETER)

    val globalCustomSpm: StateFlow<Int> = preferencesRepository.getGlobalCustomSpmFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GLOBAL_CUSTOM_SPM)

    private val _systemHooksEvents = MutableSharedFlow<SystemHooksEvent>(extraBufferCapacity = 1)
    val systemHooksEvents: SharedFlow<SystemHooksEvent> = _systemHooksEvents.asSharedFlow()

    // Preference for Language
    private val _languageTagPreference = Preference(
        DEFAULT_LANGUAGE_TAG,
        preferencesRepository.getLanguageTagFlow(),
        preferencesRepository::saveLanguageTag,
        viewModelScope
    )
    val languageTag: StateFlow<String> = _languageTagPreference.state

    private val _baiduMapAkPreference = Preference(
        DEFAULT_BAIDU_MAP_AK,
        preferencesRepository.getBaiduMapAkFlow(),
        preferencesRepository::saveBaiduMapAk,
        viewModelScope
    )
    val baiduMapAk: StateFlow<String> = _baiduMapAkPreference.state

    val mapProvider: StateFlow<MapProvider> = preferencesRepository.getMapProviderFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapProvider.OSMDROID)

    val osmTileServer: StateFlow<OsmTileServer> = preferencesRepository.getOsmTileServerFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, OsmTileServer.OPENSTREETMAP)

    private val _amapWebKeyPreference = Preference(
        DEFAULT_AMAP_WEB_KEY,
        preferencesRepository.getAmapWebKeyFlow(),
        preferencesRepository::saveAmapWebKey,
        viewModelScope
    )
    val amapWebKey: StateFlow<String> = _amapWebKeyPreference.state

    private val _amapSecurityCodePreference = Preference(
        DEFAULT_AMAP_SECURITY_CODE,
        preferencesRepository.getAmapSecurityCodeFlow(),
        preferencesRepository::saveAmapSecurityCode,
        viewModelScope
    )
    val amapSecurityCode: StateFlow<String> = _amapSecurityCodePreference.state

    private val _googleMapsApiKeyPreference = Preference(
        DEFAULT_GOOGLE_MAPS_API_KEY,
        preferencesRepository.getGoogleMapsApiKeyFlow(),
        preferencesRepository::saveGoogleMapsApiKey,
        viewModelScope
    )
    val googleMapsApiKey: StateFlow<String> = _googleMapsApiKeyPreference.state

    // Setter methods for all preferences
    fun setUseAccuracy(value: Boolean) = _useAccuracyPreference.setValue(value)
    fun setAccuracy(value: Double) = _accuracyPreference.setValue(value)
    fun setUseAltitude(value: Boolean) = _useAltitudePreference.setValue(value)
    fun setAltitude(value: Double) = _altitudePreference.setValue(value)
    fun setUseRandomize(value: Boolean) = _useRandomizePreference.setValue(value)
    fun setRandomizeRadius(value: Double) = _randomizeRadiusPreference.setValue(value)
    fun setUseVerticalAccuracy(value: Boolean) = _useVerticalAccuracyPreference.setValue(value)
    fun setVerticalAccuracy(value: Float) = _verticalAccuracyPreference.setValue(value)
    fun setUseMeanSeaLevel(value: Boolean) = _useMeanSeaLevelPreference.setValue(value)
    fun setMeanSeaLevel(value: Double) = _meanSeaLevelPreference.setValue(value)
    fun setUseMeanSeaLevelAccuracy(value: Boolean) = _useMeanSeaLevelAccuracyPreference.setValue(value)
    fun setMeanSeaLevelAccuracy(value: Float) = _meanSeaLevelAccuracyPreference.setValue(value)
    fun setUseSpeed(value: Boolean) = _useSpeedPreference.setValue(value)
    fun setSpeed(value: Float) = _speedPreference.setValue(value)
    fun setUseSpeedAccuracy(value: Boolean) = _useSpeedAccuracyPreference.setValue(value)
    fun setSpeedAccuracy(value: Float) = _speedAccuracyPreference.setValue(value)
    fun setHideFakeLocationToast(value: Boolean) = _hideFakeLocationToastPreference.setValue(value)
    fun setEnableBroadcastControl(value: Boolean) = _enableBroadcastControlPreference.setValue(value)
    fun setLanguageTag(value: String) = _languageTagPreference.setValue(value)
    fun setBaiduMapAk(value: String) = _baiduMapAkPreference.setValue(value.trim())
    fun setMapProvider(value: MapProvider) = enqueueSave {
        preferencesRepository.saveMapProvider(value)
    }
    fun setOsmTileServer(value: OsmTileServer) = enqueueSave {
        preferencesRepository.saveOsmTileServer(value)
    }
    fun setAmapWebKey(value: String) = _amapWebKeyPreference.setValue(value.trim())
    fun setAmapSecurityCode(value: String) = _amapSecurityCodePreference.setValue(value.trim())
    fun setGoogleMapsApiKey(value: String) = _googleMapsApiKeyPreference.setValue(value.trim())

    fun setMockFlagPolicy(policy: MockFlagPolicy) = enqueueSave {
        preferencesRepository.saveMockFlagPolicy(policy)
    }

    fun setHookInstallPolicy(policy: HookInstallPolicy) = enqueueSave {
        preferencesRepository.saveHookInstallPolicy(policy)
    }

    fun setAppHookInstallPolicy(packageName: String, policy: HookInstallPolicy) = enqueueSave {
        preferencesRepository.saveAppHookInstallPolicy(packageName, policy)
    }

    fun setGnssConstellationPreset(preset: io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset) = enqueueSave {
        preferencesRepository.saveGnssConstellationPreset(preset)
    }

    fun setGnssSignalStrengthPreset(preset: io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset) = enqueueSave {
        preferencesRepository.saveGnssSignalStrengthPreset(preset)
    }

    fun setGnssVisibleSatellites(count: Int) = enqueueSave {
        preferencesRepository.saveGnssVisibleSatellites(count)
    }

    fun setGnssUsedSatellites(count: Int) = enqueueSave {
        preferencesRepository.saveGnssUsedSatellites(count)
    }

    fun setGnssJitterEnabled(enabled: Boolean) = enqueueSave {
        preferencesRepository.saveGnssJitterEnabled(enabled)
    }

    fun setGnssJitterAlgorithm(algo: io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm) = enqueueSave {
        preferencesRepository.saveGnssJitterAlgorithm(algo)
    }

    fun setGnssJitterRadius(radius: Double) = enqueueSave {
        preferencesRepository.saveGnssJitterRadius(radius)
    }

    fun setGnssJitterSmoothing(smoothing: Double) = enqueueSave {
        preferencesRepository.saveGnssJitterSmoothing(smoothing)
    }

    fun setGnssJitterDriftSpeed(speed: Double) = enqueueSave {
        preferencesRepository.saveGnssJitterDriftSpeed(speed)
    }

    fun setGlobalCadenceMode(mode: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode) = enqueueSave {
        preferencesRepository.saveGlobalCadenceMode(mode)
    }

    fun setGlobalCadenceChannel(channel: io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel) = enqueueSave {
        preferencesRepository.saveGlobalCadenceChannel(channel)
    }

    fun setGlobalCustomSpm(spm: Int) = enqueueSave {
        preferencesRepository.saveGlobalCustomSpm(spm)
    }

    private val modeSelection = LatestSelection<LocationMode>(
        ::enqueueSave,
        { mode ->
            when (mode) {
                LocationMode.SYSTEM_HOOK, LocationMode.GLOBAL_HOOK -> validateSystemScope()
                LocationMode.ROOT_MODE -> validateRootAccess()
                LocationMode.APPLICATION_HOOK -> validateAppHookAccess()
                LocationMode.MOCK_PROVIDER -> true
            }
        },
        { mode -> preferencesRepository.saveLocationMode(mode) }
    )

    fun selectLocationMode(mode: LocationMode) = modeSelection.select(mode)

    private fun validateRootAccess(): Boolean {
        val hasRoot = io.github.ceigt.geolocation.manager.control.RootCommands.hasRoot()
        if (!hasRoot) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.RootPermissionRequired)
            return false
        }
        return true
    }

    private fun validateAppHookAccess(): Boolean {
        // App-level hook requires LSPosed module to be active
        if (App.service == null) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.ModuleNotActive)
            // Allow user to select mode so settings can be mirrored when module activates
            return true
        }
        return true
    }

    private fun validateSystemScope(): Boolean {
        if (!SystemHookSupport.supports(android.os.Build.VERSION.SDK_INT)) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.UnsupportedSystemVersion)
            return false
        }
        val service = App.service
        if (service == null) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.ModuleNotActive)
            return false
        }
        // The writer performs this blocking Binder query off the UI thread.
        val currentScope = try { service.scope.toSet() }
        catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _systemHooksEvents.tryEmit(SystemHooksEvent.ModuleNotActive)
            return false
        }
        val missing = SYSTEM_HOOK_PACKAGES.filterNot(currentScope::contains)
        if (missing.isNotEmpty()) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.ScopeSetupRequired(missing))
            return false
        }
        return true
    }
}
