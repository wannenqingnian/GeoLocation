package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.util.function.Consumer

/**
 * Hooks synchronous & asynchronous location requests made through android.location.LocationManager
 * as well as direct getter & mock detection queries on android.location.Location itself.
 */
internal class LocationManagerHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        hookLocationClassGetters()
        hookLocationManagerMethods()
    }

    /**
     * 1. Direct Getter & Anti-Detection Hooks on android.location.Location.
     * Prevents apps from querying isFromMockProvider() or reading raw location fields.
     */
    private fun hookLocationClassGetters() {
        runCatching {
            val locationClass = Location::class.java

            // Hook isFromMockProvider() and isMock() to always return false
            for (method in locationClass.declaredMethods) {
                if ((method.name == "isFromMockProvider" || method.name == "isMock") && method.parameterCount == 0) {
                    module.hook(method).intercept { chain ->
                        if (PreferencesUtil.getIsPlaying()) {
                            false
                        } else {
                            chain.proceed()
                        }
                    }
                }
            }

            // Hook getLatitude() & getLongitude() as extra safety net
            val getLatMethod = runCatching { locationClass.getDeclaredMethod("getLatitude") }.getOrNull()
            if (getLatMethod != null) {
                module.hook(getLatMethod).intercept { chain ->
                    if (PreferencesUtil.getIsPlaying()) {
                        val loc = chain.thisObject as? Location
                        if (loc != null && !LocationUtil.isSimulated(loc)) {
                            val fake = LocationUtil.createFakeLocation(loc)
                            fake.latitude
                        } else {
                            chain.proceed()
                        }
                    } else {
                        chain.proceed()
                    }
                }
            }

            val getLngMethod = runCatching { locationClass.getDeclaredMethod("getLongitude") }.getOrNull()
            if (getLngMethod != null) {
                module.hook(getLngMethod).intercept { chain ->
                    if (PreferencesUtil.getIsPlaying()) {
                        val loc = chain.thisObject as? Location
                        if (loc != null && !LocationUtil.isSimulated(loc)) {
                            val fake = LocationUtil.createFakeLocation(loc)
                            fake.longitude
                        } else {
                            chain.proceed()
                        }
                    } else {
                        chain.proceed()
                    }
                }
            }
        }.onFailure {
            module.log(Log.WARN, tag, "Failed to hook Location getters: ${it.message}")
        }
    }

    /**
     * 2. Hook LocationManager synchronous (getLastKnownLocation) and asynchronous (getCurrentLocation)
     */
    private fun hookLocationManagerMethods() {
        try {
            val locationManagerClass = Class.forName(
                "android.location.LocationManager",
                false,
                classLoader
            )

            // getLastKnownLocation(String)
            runCatching {
                val method = locationManagerClass.getDeclaredMethod("getLastKnownLocation", String::class.java)
                module.hook(method).intercept { chain ->
                    val provider = (chain.getArg(0) as? String) ?: LocationManager.GPS_PROVIDER
                    ApplicationLocationAccess.init(chain.thisObject as LocationManager)
                    if (PreferencesUtil.snapshot().isPlaying) {
                        val original = chain.proceed() as? Location
                        val fakeLocation = if (original != null) {
                            ApplicationLocationAccess.replace(original, provider)
                        } else {
                            LocationUtil.createFakeLocation(provider = provider)
                        }
                        logLocationEvent { "getLastKnownLocation($provider): $fakeLocation" }
                        fakeLocation
                    } else {
                        chain.proceed()
                    }
                }
            }

            // getCurrentLocation(..., Consumer<Location>) on Android 11+ (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                for (method in locationManagerClass.declaredMethods) {
                    if (method.name == "getCurrentLocation") {
                        val consumerIndex = method.parameterTypes.indexOfFirst {
                            Consumer::class.java.isAssignableFrom(it)
                        }
                        if (consumerIndex >= 0) {
                            module.hook(method).intercept { chain ->
                                val consumer = chain.getArg(consumerIndex) as? Consumer<Location>
                                val provider = chain.args.firstOrNull() as? String ?: LocationManager.FUSED_PROVIDER
                                ApplicationLocationAccess.init(chain.thisObject as LocationManager)

                                if (consumer != null && PreferencesUtil.snapshot().isPlaying) {
                                    // Check cancellation signal if present
                                    val isCanceled = chain.args.any { it is CancellationSignal && it.isCanceled }
                                    if (isCanceled) {
                                        return@intercept chain.proceed()
                                    }

                                    val fake = LocationUtil.createFakeLocation(provider = provider)
                                    consumer.accept(fake)
                                    logLocationEvent { "getCurrentLocation($provider) dispatched directly to Consumer" }
                                    null
                                } else {
                                    chain.proceed()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking LocationManager - ${e.message}")
        }
    }

    private inline fun logLocationEvent(message: () -> String) {
        if (LOG_LOCATION_EVENTS) {
            module.log(Log.INFO, tag, message())
        }
    }

    private companion object {
        const val LOG_LOCATION_EVENTS = false
    }
}
