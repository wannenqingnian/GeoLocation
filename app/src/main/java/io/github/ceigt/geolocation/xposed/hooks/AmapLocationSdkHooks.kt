package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.ceigt.geolocation.data.CoordinateSystem
import io.github.ceigt.geolocation.manager.ui.map.CoordinateTransform
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class AmapLocationSdkHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[AmapLocationSdkHooks]"
    private val installedMethods = mutableSetOf<Method>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initHooks(loader: ClassLoader = classLoader) {
        runCatching {
            val amapLocationClass = Class.forName("com.amap.api.location.AMapLocation", false, loader)
            val clientClass = runCatching { Class.forName("com.amap.api.location.AMapLocationClient", false, loader) }.getOrNull()
            val listenerClass = runCatching { Class.forName("com.amap.api.location.AMapLocationListener", false, loader) }.getOrNull()

            hookAMapLocationGetters(amapLocationClass)
            if (clientClass != null && listenerClass != null) {
                hookAMapLocationListener(clientClass, listenerClass, amapLocationClass)
            }
            module.log(Log.INFO, tag, "AMap SDK hooks installed successfully")
        }.onFailure {
            module.log(Log.INFO, tag, "AMap SDK classes not present in loader: ${it.message}")
        }
    }

    private fun hookAMapLocationGetters(clazz: Class<*>) {
        val channelSettings = PreferencesUtil.getChannelSettings(io.github.ceigt.geolocation.data.model.channel.LocationChannel.AMAP)
        if (!channelSettings.enabled) return

        // Latitude
        hookAll(clazz, "getLatitude") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val point = getGcj02Point()
                point.latitude
            } else {
                chain.proceed()
            }
        }

        // Longitude
        hookAll(clazz, "getLongitude") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val point = getGcj02Point()
                point.longitude
            } else {
                chain.proceed()
            }
        }

        // isMock
        hookAll(clazz, "isMock") { chain ->
            if (PreferencesUtil.getIsPlaying()) false else chain.proceed()
        }

        // isOffset
        hookAll(clazz, "isOffset") { chain ->
            if (PreferencesUtil.getIsPlaying()) true else chain.proceed()
        }

        // getLocationType: 1 = LOCATION_TYPE_GPS
        hookAll(clazz, "getLocationType") { chain ->
            if (PreferencesUtil.getIsPlaying()) 1 else chain.proceed()
        }

        // getErrorCode: 0 = LOCATION_SUCCESS
        hookAll(clazz, "getErrorCode") { chain ->
            if (PreferencesUtil.getIsPlaying()) 0 else chain.proceed()
        }

        // getErrorInfo: "success"
        hookAll(clazz, "getErrorInfo") { chain ->
            if (PreferencesUtil.getIsPlaying()) "success" else chain.proceed()
        }

        // getAccuracy
        hookAll(clazz, "getAccuracy") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val config = PreferencesUtil.snapshot()
                if (config.useAccuracy) config.accuracy.toFloat() else 5.0f
            } else {
                chain.proceed()
            }
        }

        // getAltitude
        hookAll(clazz, "getAltitude") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val config = PreferencesUtil.snapshot()
                if (config.useAltitude) config.altitude else 15.0
            } else {
                chain.proceed()
            }
        }

        // getSpeed
        hookAll(clazz, "getSpeed") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val config = PreferencesUtil.snapshot()
                if (config.useSpeed) config.speed else 0.0f
            } else {
                chain.proceed()
            }
        }

        // getBearing
        hookAll(clazz, "getBearing") { chain ->
            if (PreferencesUtil.getIsPlaying()) 0.0f else chain.proceed()
        }

        // getSatellites (return synthetic satellite count)
        hookAll(clazz, "getSatellites") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                PreferencesUtil.getGnssUsedSatellites()
            } else {
                chain.proceed()
            }
        }

        // getGpsAccuracyStatus: 1 = GPS_ACCURACY_GOOD
        hookAll(clazz, "getGpsAccuracyStatus") { chain ->
            if (PreferencesUtil.getIsPlaying()) 1 else chain.proceed()
        }

        // Address & POI Mocking - dynamically read from Preferences
        hookAll(clazz, "getAddress") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.formattedAddress.isNotBlank()) meta.formattedAddress else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getPoiName") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.poiName.isNotBlank()) meta.poiName else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getProvince") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.province.isNotBlank()) meta.province else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getCity") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.city.isNotBlank()) meta.city else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getDistrict") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.district.isNotBlank()) meta.district else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getStreet") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.street.isNotBlank()) meta.street else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getAdCode") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.adCode.isNotBlank()) meta.adCode else chain.proceed()
            } else chain.proceed()
        }
        hookAll(clazz, "getCityCode") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.cityCode.isNotBlank()) meta.cityCode else chain.proceed()
            } else chain.proceed()
        }
    }

    private fun hookAMapLocationListener(clientClass: Class<*>, listenerClass: Class<*>, amapLocationClass: Class<*>) {
        clientClass.declaredMethods
            .filter { it.name == "setLocationListener" && it.parameterTypes.any { p -> p == listenerClass } }
            .forEach { method ->
                hook(method) { chain ->
                    val listener = chain.args.firstOrNull { listenerClass.isInstance(it) }
                    if (listener != null) {
                        // Hook onLocationChanged
                        runCatching {
                            val onLocationChanged = listener.javaClass.getMethod("onLocationChanged", amapLocationClass)
                            hook(onLocationChanged) { cbChain ->
                                val location = cbChain.getArg(0)
                                if (location != null && PreferencesUtil.getIsPlaying()) {
                                    val point = getGcj02Point()
                                    runCatching {
                                        location.javaClass.getMethod("setLatitude", Double::class.javaPrimitiveType).invoke(location, point.latitude)
                                        location.javaClass.getMethod("setLongitude", Double::class.javaPrimitiveType).invoke(location, point.longitude)
                                        location.javaClass.getMethod("setMock", Boolean::class.javaPrimitiveType).invoke(location, false)
                                    }
                                }
                                cbChain.proceed()
                            }
                        }
                    }
                    chain.proceed()
                }
            }
    }

    private fun getGcj02Point(): io.github.ceigt.geolocation.manager.ui.map.GeoPoint {
        val config = PreferencesUtil.snapshot()
        LocationUtil.updateLocation(config)
        // Project WGS84 coordinates to GCJ02 for AMap
        return CoordinateTransform.wgs84ToGcj02(LocationUtil.latitude, LocationUtil.longitude)
    }

    private fun hookAll(clazz: Class<*>, methodName: String, interceptor: Hooker) {
        clazz.declaredMethods
            .filter { it.name == methodName }
            .forEach { hook(it, interceptor) }
    }

    private fun hook(method: Method, interceptor: Hooker) {
        synchronized(installedMethods) {
            if (method in installedMethods) return
            runCatching {
                module.hook(method).intercept(interceptor)
                installedMethods.add(method)
            }
        }
    }
}
