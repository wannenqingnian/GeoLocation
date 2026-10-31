package io.github.ceigt.geolocation.inject

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.util.Log
import io.github.ceigt.geolocation.manager.control.RootCommands
import io.github.ceigt.geolocation.xposed.utils.LocationUtil

/**
 * Root-level Location Injector:
 * Operates without Xposed by utilizing Root access to grant AppOps mock_location permissions,
 * register system test providers across (gps, network, fused), and push stripped mock locations.
 */
object RootLocationHook {
    private const val TAG = "RootLocationHook"

    val TARGET_PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        "fused"
    )

    private val readyProviders = mutableSetOf<String>()

    /**
     * Ensures root permission and appops permissions are in place.
     */
    fun ensureRootEnvironment(context: Context): Boolean {
        if (!RootCommands.hasRoot()) {
            Log.w(TAG, "Root permission not available")
            return false
        }
        return RootCommands.enableMockLocation(context.packageName)
    }

    /**
     * Prepares and adds a system test provider.
     */
    @SuppressLint("WrongConstant")
    fun ensureProvider(locationManager: LocationManager, provider: String): Boolean {
        if (provider in readyProviders) return true

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    provider,
                    ProviderProperties.Builder()
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .setHasAltitudeSupport(true)
                        .setHasSpeedSupport(true)
                        .setHasBearingSupport(true)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    provider,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
            }
        }.recoverCatching {
            // Already added or exists, proceed to enable
        }.mapCatching {
            locationManager.setTestProviderEnabled(provider, true)
            readyProviders += provider
            true
        }.onFailure {
            Log.e(TAG, "Failed to ensure test provider $provider: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Injects location into the specified provider using LocationManager.
     */
    fun injectLocation(locationManager: LocationManager, provider: String, location: Location): Boolean {
        if (!ensureProvider(locationManager, provider)) return false
        return runCatching {
            val fix = Location(location).apply {
                this.provider = provider
            }
            LocationUtil.stripMockFlags(fix)
            locationManager.setTestProviderLocation(provider, fix)
            true
        }.onFailure {
            Log.e(TAG, "Error pushing root test location to $provider: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Cleanup and remove all added test providers.
     */
    fun removeProviders(locationManager: LocationManager) {
        TARGET_PROVIDERS.forEach { provider ->
            runCatching {
                locationManager.removeTestProvider(provider)
            }
        }
        readyProviders.clear()
    }
}
