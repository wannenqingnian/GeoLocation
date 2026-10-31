package io.github.ceigt.geolocation.xposed.hooks

import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset
import io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class GnssStatusHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[GnssStatusHooks]"
    private val gnssProvider = GnssStatusProvider()
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class CallbackRegistration(
        val callback: GnssStatus.Callback,
        val executor: Executor?,
        val handler: Handler?
    )

    private val activeRegistrations = Collections.newSetFromMap(ConcurrentHashMap<CallbackRegistration, Boolean>())
    @Volatile private var isLooping = false

    private val dispatchRunnable = object : Runnable {
        override fun run() {
            if (!PreferencesUtil.getIsPlaying()) {
                isLooping = false
                return
            }

            dispatchSyntheticGnssStatus()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    fun initHooks() {
        isolateHook({ module.log(Log.WARN, tag, "GnssStatus hooks failed: ${it.message}") }) {
            hookGnssStatusCallbacks()
            hookLegacyGpsStatus()
        }
    }

    private fun hookGnssStatusCallbacks() {
        val locationManagerClass = LocationManager::class.java

        // 1. registerGnssStatusCallback(GnssStatus.Callback, Handler)
        runCatching {
            val method = locationManagerClass.getMethod(
                "registerGnssStatusCallback",
                GnssStatus.Callback::class.java,
                Handler::class.java
            )
            module.hook(method).intercept { chain ->
                val callback = chain.getArg(0) as? GnssStatus.Callback
                val handler = chain.getArg(1) as? Handler
                if (callback != null) {
                    val reg = CallbackRegistration(callback, executor = null, handler = handler ?: mainHandler)
                    activeRegistrations.add(reg)
                    startLoopIfNeeded()
                    // Prompt initial fix callback
                    notifyCallbackOnFix(reg)
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        // 2. registerGnssStatusCallback(Executor, GnssStatus.Callback) (API 30+)
        runCatching {
            val method = locationManagerClass.getMethod(
                "registerGnssStatusCallback",
                Executor::class.java,
                GnssStatus.Callback::class.java
            )
            module.hook(method).intercept { chain ->
                val executor = chain.getArg(0) as? Executor
                val callback = chain.getArg(1) as? GnssStatus.Callback
                if (callback != null) {
                    val reg = CallbackRegistration(callback, executor = executor, handler = null)
                    activeRegistrations.add(reg)
                    startLoopIfNeeded()
                    notifyCallbackOnFix(reg)
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        // 3. unregisterGnssStatusCallback(GnssStatus.Callback)
        runCatching {
            val method = locationManagerClass.getMethod(
                "unregisterGnssStatusCallback",
                GnssStatus.Callback::class.java
            )
            module.hook(method).intercept { chain ->
                val callback = chain.getArg(0) as? GnssStatus.Callback
                if (callback != null) {
                    activeRegistrations.removeAll { it.callback == callback }
                }
                chain.proceed()
            }
        }
    }

    private fun hookLegacyGpsStatus() {
        val locationManagerClass = LocationManager::class.java

        // addGpsStatusListener
        runCatching {
            val method = locationManagerClass.getMethod("addGpsStatusListener", GpsStatus.Listener::class.java)
            module.hook(method).intercept { chain ->
                if (PreferencesUtil.getIsPlaying()) {
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        // getGpsStatus(GpsStatus)
        runCatching {
            val method = locationManagerClass.getMethod("getGpsStatus", GpsStatus::class.java)
            module.hook(method).intercept { chain ->
                val status = (chain.getArg(0) as? GpsStatus) ?: chain.proceed() as? GpsStatus
                if (status != null && PreferencesUtil.getIsPlaying()) {
                    fillLegacyGpsStatus(status)
                    status
                } else {
                    status
                }
            }
        }
    }

    private fun fillLegacyGpsStatus(status: GpsStatus) {
        runCatching {
            // Reflectively set ttff and satellites in GpsStatus
            val setStatusMethod = runCatching {
                GpsStatus::class.java.getDeclaredMethod(
                    "setStatus",
                    Int::class.javaPrimitiveType,
                    IntArray::class.java,
                    FloatArray::class.java,
                    FloatArray::class.java,
                    FloatArray::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true }
            }.getOrNull()

            val visible = PreferencesUtil.getGnssVisibleSatellites().coerceAtLeast(8)
            val used = PreferencesUtil.getGnssUsedSatellites().coerceAtLeast(4).coerceAtMost(visible)
            val preset = PreferencesUtil.getGnssConstellationPreset()
            val signal = PreferencesUtil.getGnssSignalStrengthPreset()

            val satellites = gnssProvider.generateSatellites(
                visibleCount = visible,
                usedCount = used,
                preset = preset,
                signalPreset = signal
            )
            val count = satellites.size
            val prns = IntArray(count) { satellites[it].svid }
            val snrs = FloatArray(count) { satellites[it].cn0DbHz }
            val elevations = FloatArray(count) { satellites[it].elevationDegrees }
            val azimuths = FloatArray(count) { satellites[it].azimuthDegrees }
            val ephemerisMask = (1 shl count.coerceAtMost(31)) - 1
            val almanacMask = (1 shl count.coerceAtMost(31)) - 1
            val usedInFixMask = (1 shl used.coerceAtMost(31)) - 1

            setStatusMethod?.invoke(
                status,
                count,
                prns,
                snrs,
                elevations,
                azimuths,
                ephemerisMask,
                almanacMask,
                usedInFixMask
            )
        }
    }

    private fun startLoopIfNeeded() {
        if (!isLooping && PreferencesUtil.getIsPlaying()) {
            isLooping = true
            mainHandler.post(dispatchRunnable)
        }
    }

    private fun notifyCallbackOnFix(reg: CallbackRegistration) {
        val exec: (Runnable) -> Unit = { r ->
            if (reg.executor != null) {
                reg.executor.execute(r)
            } else {
                (reg.handler ?: mainHandler).post(r)
            }
        }

        exec(Runnable {
            runCatching {
                reg.callback.onStarted()
                reg.callback.onFirstFix(350)
            }
        })
    }

    private fun dispatchSyntheticGnssStatus() {
        if (activeRegistrations.isEmpty()) return

        val visible = PreferencesUtil.getGnssVisibleSatellites().coerceAtLeast(8)
        val used = PreferencesUtil.getGnssUsedSatellites().coerceAtLeast(4).coerceAtMost(visible)
        val preset = PreferencesUtil.getGnssConstellationPreset()
        val signal = PreferencesUtil.getGnssSignalStrengthPreset()

        val satellites = gnssProvider.generateSatellites(
            visibleCount = visible,
            usedCount = used,
            preset = preset,
            signalPreset = signal
        )
        val gnssStatus = GnssStatusConstructionStrategy.createGnssStatus(satellites) ?: return

        for (reg in activeRegistrations) {
            val exec: (Runnable) -> Unit = { r ->
                if (reg.executor != null) {
                    reg.executor.execute(r)
                } else {
                    (reg.handler ?: mainHandler).post(r)
                }
            }
            exec(Runnable {
                runCatching { reg.callback.onSatelliteStatusChanged(gnssStatus) }
            })
        }
    }
}
