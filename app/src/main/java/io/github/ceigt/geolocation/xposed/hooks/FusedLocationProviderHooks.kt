package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class FusedLocationProviderHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[FusedLocationProviderHooks]"
    private val installedMethods = mutableSetOf<Method>()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class FusedChannelSpec(
        val name: String,
        val clientClassName: String,
        val callbackClassName: String,
        val resultClassName: String
    )

    private val specs = listOf(
        FusedChannelSpec(
            name = "Google GMS",
            clientClassName = "com.google.android.gms.location.FusedLocationProviderClient",
            callbackClassName = "com.google.android.gms.location.LocationCallback",
            resultClassName = "com.google.android.gms.location.LocationResult"
        ),
        FusedChannelSpec(
            name = "Huawei HMS",
            clientClassName = "com.huawei.hms.location.FusedLocationProviderClient",
            callbackClassName = "com.huawei.hms.location.LocationCallback",
            resultClassName = "com.huawei.hms.location.LocationResult"
        )
    )

    private val activeCallbacks = Collections.newSetFromMap(ConcurrentHashMap<Any, Boolean>())
    @Volatile private var isLooping = false

    private val periodicPushRunnable = object : Runnable {
        override fun run() {
            if (!PreferencesUtil.getIsPlaying() || activeCallbacks.isEmpty()) {
                isLooping = false
                return
            }

            dispatchSpoofedResult()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    fun initHooks(loader: ClassLoader = classLoader) {
        // Also hook android.location.LocationResult if present (Android 12+)
        runCatching {
            val aospResultClass = Class.forName("android.location.LocationResult", false, loader)
            hookLocationResultGetters(aospResultClass)
        }

        for (spec in specs) {
            runCatching {
                val clientClass = Class.forName(spec.clientClassName, false, loader)
                val callbackClass = Class.forName(spec.callbackClassName, false, loader)
                val resultClass = Class.forName(spec.resultClassName, false, loader)

                hookLocationResultGetters(resultClass)
                hookClientRequests(clientClass, callbackClass, resultClass)
                module.log(Log.INFO, tag, "${spec.name} hooks installed successfully")
            }.onFailure {
                module.log(Log.INFO, tag, "${spec.name} classes not in loader: ${it.message}")
            }
        }
    }

    private fun hookLocationResultGetters(resultClass: Class<*>) {
        // getLastLocation()
        hookAll(resultClass, "getLastLocation") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val fake = LocationUtil.createFakeLocation(provider = "fused")
                LocationUtil.stripMockFlags(fake)
                fake
            } else {
                chain.proceed()
            }
        }

        // getLocations()
        hookAll(resultClass, "getLocations") { chain ->
            if (PreferencesUtil.getIsPlaying()) {
                val fake = LocationUtil.createFakeLocation(provider = "fused")
                LocationUtil.stripMockFlags(fake)
                listOf(fake)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookClientRequests(clientClass: Class<*>, callbackClass: Class<*>, resultClass: Class<*>) {
        // requestLocationUpdates
        clientClass.declaredMethods
            .filter { it.name == "requestLocationUpdates" && it.parameterTypes.any { p -> p == callbackClass } }
            .forEach { method ->
                hook(method) { chain ->
                    val cb = chain.args.firstOrNull { callbackClass.isInstance(it) }
                    if (cb != null) {
                        activeCallbacks.add(cb)
                        startLoopIfNeeded()
                    }
                    chain.proceed()
                }
            }

        // removeLocationUpdates
        clientClass.declaredMethods
            .filter { it.name == "removeLocationUpdates" && it.parameterTypes.any { p -> p == callbackClass } }
            .forEach { method ->
                hook(method) { chain ->
                    val cb = chain.args.firstOrNull { callbackClass.isInstance(it) }
                    if (cb != null) {
                        activeCallbacks.remove(cb)
                    }
                    chain.proceed()
                }
            }
    }

    private fun startLoopIfNeeded() {
        if (!isLooping && PreferencesUtil.getIsPlaying()) {
            isLooping = true
            mainHandler.post(periodicPushRunnable)
        }
    }

    private fun dispatchSpoofedResult() {
        if (activeCallbacks.isEmpty()) return
        val fake = LocationUtil.createFakeLocation(provider = "fused")
        LocationUtil.stripMockFlags(fake)

        for (cb in activeCallbacks) {
            runCatching {
                // Try create LocationResult instance: static create(List<Location>)
                for (spec in specs) {
                    val resultClass = runCatching { Class.forName(spec.resultClassName, false, cb.javaClass.classLoader) }.getOrNull()
                    if (resultClass != null) {
                        val createMethod = runCatching { resultClass.getMethod("create", List::class.java) }.getOrNull()
                        val resultObj = createMethod?.invoke(null, listOf(fake))
                        if (resultObj != null) {
                            val onLocationResult = runCatching { cb.javaClass.getMethod("onLocationResult", resultClass) }.getOrNull()
                            onLocationResult?.invoke(cb, resultObj)
                        }
                    }
                }
            }
        }
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
