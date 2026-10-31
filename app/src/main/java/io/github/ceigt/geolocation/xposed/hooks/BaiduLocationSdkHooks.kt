package io.github.ceigt.geolocation.xposed.hooks

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.ceigt.geolocation.data.CoordinateSystem
import io.github.ceigt.geolocation.manager.ui.map.CoordinateTransform
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

class BaiduLocationSdkHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[BaiduLocationSdkHooks]"
    private val installedMethods = mutableSetOf<Method>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initHooks(loader: ClassLoader = classLoader) {
        runCatching {
            val bdLocationClass = Class.forName("com.baidu.location.BDLocation", false, loader)
            val clientClass = runCatching { Class.forName("com.baidu.location.LocationClient", false, loader) }.getOrNull()
            val listenerClass = runCatching { Class.forName("com.baidu.location.BDLocationListener", false, loader) }.getOrNull()
            val abstractListenerClass = runCatching { Class.forName("com.baidu.location.BDAbstractLocationListener", false, loader) }.getOrNull()

            hookBDLocationGetters(bdLocationClass)
            if (clientClass != null) {
                if (listenerClass != null) hookRegisterListener(clientClass, listenerClass, bdLocationClass)
                if (abstractListenerClass != null) hookRegisterListener(clientClass, abstractListenerClass, bdLocationClass)
            }
            module.log(Log.INFO, tag, "Baidu SDK hooks installed successfully")
        }.onFailure {
            module.log(Log.INFO, tag, "Baidu SDK classes not present in loader: ${it.message}")
        }
    }

    private fun hookBDLocationGetters(clazz: Class<*>) {
        val channelSettings = PreferencesUtil.getChannelSettings(io.github.ceigt.geolocation.data.model.channel.LocationChannel.BAIDU)
        if (!channelSettings.enabled) return

        // Latitude (BD09)
        hookAll(clazz, "getLatitude") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val point = getBd09Point()
                point.latitude
            } else {
                chain.proceed()
            }
        }

        // Longitude (BD09)
        hookAll(clazz, "getLongitude") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val point = getBd09Point()
                point.longitude
            } else {
                chain.proceed()
            }
        }

        // getRadius (accuracy)
        hookAll(clazz, "getRadius") { chain ->
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
                if (config.useAltitude) config.altitude else 20.0
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

        // getDirection
        hookAll(clazz, "getDirection") { chain ->
            if (PreferencesUtil.getIsPlaying()) 0.0f else chain.proceed()
        }

        // getSatelliteNumber
        hookAll(clazz, "getSatelliteNumber") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                PreferencesUtil.getGnssUsedSatellites()
            } else {
                chain.proceed()
            }
        }

        // getLocType: 61 = BDLocation.TypeGpsLocation
        hookAll(clazz, "getLocType") { chain ->
            if (PreferencesUtil.getIsPlaying()) 61 else chain.proceed()
        }

        // Anti-cheat / Mock detection
        hookAll(clazz, "isMockGps") { chain ->
            if (PreferencesUtil.getIsPlaying()) 0 else chain.proceed()
        }
        hookAll(clazz, "getMockGpsStrategy") { chain ->
            if (PreferencesUtil.getIsPlaying()) 0 else chain.proceed()
        }
        hookAll(clazz, "getMockGpsProbability") { chain ->
            if (PreferencesUtil.getIsPlaying()) 0 else chain.proceed()
        }
        // Address & POI Mocking - dynamically read from Preferences
        hookAll(clazz, "getAddrStr") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val meta = PreferencesUtil.getLocationAddressMetadata()
                if (meta.formattedAddress.isNotBlank()) meta.formattedAddress else chain.proceed()
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

    private fun hookRegisterListener(clientClass: Class<*>, listenerClass: Class<*>, bdLocationClass: Class<*>) {
        clientClass.declaredMethods
            .filter { it.name == "registerLocationListener" && it.parameterTypes.any { p -> p == listenerClass } }
            .forEach { method ->
                hook(method) { chain ->
                    val listener = chain.args.firstOrNull { listenerClass.isInstance(it) }
                    if (listener != null) {
                        runCatching {
                            val onReceive = listener.javaClass.getMethod("onReceiveLocation", bdLocationClass)
                            hook(onReceive) { cbChain ->
                                val location = cbChain.getArg(0)
                                if (location != null && PreferencesUtil.getIsPlaying()) {
                                    val point = getBd09Point()
                                    runCatching {
                                        location.javaClass.getMethod("setLatitude", Double::class.javaPrimitiveType).invoke(location, point.latitude)
                                        location.javaClass.getMethod("setLongitude", Double::class.javaPrimitiveType).invoke(location, point.longitude)
                                        location.javaClass.getMethod("setLocType", Int::class.javaPrimitiveType).invoke(location, 61)
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

    private fun getBd09Point(): GeoPoint {
        val config = PreferencesUtil.snapshot()
        LocationUtil.updateLocation(config)
        // Project WGS84 to BD09 for Baidu SDK
        return CoordinateTransform.wgs84ToBd09(GeoPoint(LocationUtil.latitude, LocationUtil.longitude))
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
