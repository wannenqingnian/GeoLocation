package io.github.ceigt.geolocation.xposed.hooks

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel
import io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

class AccelerometerCadenceHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[AccelerometerCadenceHooks]"
    private val installedMethods = mutableSetOf<Method>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()

    private data class SensorListenerEntry(
        val listener: SensorEventListener,
        val sensor: Sensor,
        val sensorType: Int
    )

    private val activeListeners = Collections.newSetFromMap(ConcurrentHashMap<SensorListenerEntry, Boolean>())
    @Volatile private var isLooping = false
    @Volatile private var totalAccumulatedSteps = 1200L
    private var lastStepTimeNanos = 0L

    // Waveform phase timer
    private var waveStartTimeNanos = SystemClock.elapsedRealtimeNanos()

    private val periodicWaveformRunnable = object : Runnable {
        override fun run() {
            if (!PreferencesUtil.getIsPlaying() || activeListeners.isEmpty()) {
                isLooping = false
                return
            }

            dispatchSyntheticSensorEvents()
            mainHandler.postDelayed(this, 20L) // 50 Hz sensor sample rate
        }
    }

    fun initHooks(loader: ClassLoader = classLoader) {
        runCatching {
            val sensorManagerClass = SensorManager::class.java
            val systemSensorManagerClass = runCatching {
                Class.forName("android.hardware.SystemSensorManager", false, loader)
            }.getOrNull()

            hookRegisterListener(sensorManagerClass)
            if (systemSensorManagerClass != null) {
                hookRegisterListener(systemSensorManagerClass)
            }
            module.log(Log.INFO, tag, "AccelerometerCadenceHooks installed successfully")
        }.onFailure {
            module.log(Log.INFO, tag, "Sensor hooks initialization failed: ${it.message}")
        }
    }

    private fun hookRegisterListener(managerClass: Class<*>) {
        // registerListener(SensorEventListener, Sensor, int, ...)
        managerClass.declaredMethods
            .filter { it.name == "registerListener" && it.parameterTypes.any { p -> p == SensorEventListener::class.java } }
            .forEach { method ->
                hook(method) { chain ->
                    val listener = chain.args.firstOrNull { SensorEventListener::class.java.isInstance(it) } as? SensorEventListener
                    val sensor = chain.args.firstOrNull { Sensor::class.java.isInstance(it) } as? Sensor

                    if (listener != null && sensor != null) {
                        val type = sensor.type
                        if (type == Sensor.TYPE_ACCELEROMETER ||
                            type == Sensor.TYPE_STEP_COUNTER ||
                            type == Sensor.TYPE_STEP_DETECTOR ||
                            type == 10 // TYPE_LINEAR_ACCELERATION
                        ) {
                            activeListeners.add(SensorListenerEntry(listener, sensor, type))
                            startLoopIfNeeded()
                        }
                    }
                    chain.proceed()
                }
            }

        // unregisterListener(SensorEventListener, ...)
        managerClass.declaredMethods
            .filter { it.name == "unregisterListener" && it.parameterTypes.any { p -> p == SensorEventListener::class.java } }
            .forEach { method ->
                hook(method) { chain ->
                    val listener = chain.args.firstOrNull { SensorEventListener::class.java.isInstance(it) } as? SensorEventListener
                    if (listener != null) {
                        activeListeners.removeAll { it.listener == listener }
                    }
                    chain.proceed()
                }
            }
    }

    private fun startLoopIfNeeded() {
        if (!isLooping && PreferencesUtil.getIsPlaying()) {
            isLooping = true
            waveStartTimeNanos = SystemClock.elapsedRealtimeNanos()
            mainHandler.post(periodicWaveformRunnable)
        }
    }

    private fun dispatchSyntheticSensorEvents() {
        if (activeListeners.isEmpty()) return
        val currentSpeed = LocationUtil.speed
        val targetPkg = LocationUtil.targetPackageName ?: ""
        val cadenceConfig = PreferencesUtil.getAppCadenceConfig(targetPkg)

        if (cadenceConfig.mode == TargetCadenceMode.OFF) return

        val customSpm = if (cadenceConfig.mode == TargetCadenceMode.CUSTOM) cadenceConfig.customStepsPerMinute else null
        val spm = LocationUtil.computeCadenceStepsPerMinute(currentSpeed, enabled = true, customSpm = customSpm)
        if (spm <= 0) return

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val stepIntervalNanos = (60_000_000_000L / spm)

        var hasNewStep = false
        if (nowNanos - lastStepTimeNanos >= stepIntervalNanos) {
            lastStepTimeNanos = nowNanos
            totalAccumulatedSteps++
            hasNewStep = true
        }

        // Check if Accelerometer channel is enabled for this target
        val allowAccelerometer = cadenceConfig.channel == TargetCadenceChannel.ACCELEROMETER

        // Compute 3D accelerometer oscillation: f = spm / 60.0 Hz
        val f = spm.toDouble() / 60.0
        val t = (nowNanos - waveStartTimeNanos).toDouble() / 1_000_000_000.0

        val speedRatio = (currentSpeed / 4.0f).coerceIn(0.5f, 1.8f)
        val axAmp = 2.5 * speedRatio
        val ayAmp = 4.0 * speedRatio
        val azAmp = 3.5 * speedRatio

        // ax = Ax * sin(pi * f * t) + noise
        val noiseRatio = 0.15
        val ax = (axAmp * sin(PI * f * t) + random.nextGaussian() * noiseRatio).toFloat()
        // ay = Ay * cos(2 * pi * f * t) + noise
        val ay = (ayAmp * cos(2 * PI * f * t) + random.nextGaussian() * noiseRatio).toFloat()
        // az = 9.81 + Az * sin(2 * pi * f * t) + noise
        val az = (9.81 + azAmp * sin(2 * PI * f * t) + random.nextGaussian() * noiseRatio).toFloat()

        for (entry in activeListeners) {
            runCatching {
                when (entry.sensorType) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (allowAccelerometer) {
                            val event = createSensorEvent(entry.sensor, floatArrayOf(ax, ay, az), nowNanos)
                            if (event != null) entry.listener.onSensorChanged(event)
                        }
                    }
                    Sensor.TYPE_STEP_COUNTER -> {
                        if (hasNewStep) {
                            val event = createSensorEvent(entry.sensor, floatArrayOf(totalAccumulatedSteps.toFloat()), nowNanos)
                            if (event != null) entry.listener.onSensorChanged(event)
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        if (hasNewStep) {
                            val event = createSensorEvent(entry.sensor, floatArrayOf(1.0f), nowNanos)
                            if (event != null) entry.listener.onSensorChanged(event)
                        }
                    }
                }
            }
        }
    }

    private fun createSensorEvent(sensor: Sensor, values: FloatArray, timestampNanos: Long): SensorEvent? {
        return runCatching {
            // SensorEvent has package-private constructor: SensorEvent(int valueSize) or similar
            val constructor: Constructor<SensorEvent> = runCatching {
                SensorEvent::class.java.getDeclaredConstructor(Integer.TYPE).apply { isAccessible = true }
            }.getOrElse {
                SensorEvent::class.java.getDeclaredConstructor().apply { isAccessible = true }
            }

            val event = if (constructor.parameterTypes.isEmpty()) {
                constructor.newInstance()
            } else {
                constructor.newInstance(values.size)
            }

            // Set sensor field
            findField(SensorEvent::class.java, "sensor")?.set(event, sensor)
            // Set timestamp
            findField(SensorEvent::class.java, "timestamp")?.set(event, timestampNanos)
            // Set accuracy
            findField(SensorEvent::class.java, "accuracy")?.set(event, SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
            // Copy values
            val valuesField = findField(SensorEvent::class.java, "values")
            val targetValues = valuesField?.get(event) as? FloatArray
            if (targetValues != null) {
                System.arraycopy(values, 0, targetValues, 0, min(values.size, targetValues.size))
            } else {
                valuesField?.set(event, values)
            }
            event
        }.getOrNull()
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        return runCatching {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }.getOrElse {
            HiddenApiBypass.getInstanceFields(clazz)
                .firstOrNull { (it as? Field)?.name == name } as? Field
        }
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
