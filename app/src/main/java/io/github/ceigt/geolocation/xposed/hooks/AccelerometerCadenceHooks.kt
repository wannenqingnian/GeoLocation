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

/**
 * High-fidelity biomechanical multi-sensor cadence & movement simulation.
 * Reconstructs realistic human walking/running gait cycles:
 * - Heel-strike impact shock impulses (垂直触地冲击尖峰)
 * - Push-off & center-of-mass vertical oscillation harmonics (重心双谐波起伏)
 * - Lateral alternating sway for left/right legs (左右脚交替侧向微晃)
 * - Natural biological timing jitter (步间微颤动)
 * - Synchronous delivery to ACCELEROMETER, LINEAR_ACCELERATION, GRAVITY, STEP_COUNTER, STEP_DETECTOR
 */
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
    @Volatile private var totalAccumulatedSteps = 1520L
    private var lastStepTimeNanos = 0L
    private var currentStepPeriodNanos = 375_000_000L // ~160 SPM

    // Biomechanical phase accumulator (radians)
    private var phaseRad = 0.0
    private var lastTickNanos = SystemClock.elapsedRealtimeNanos()

    private val periodicWaveformRunnable = object : Runnable {
        override fun run() {
            if (!PreferencesUtil.getIsPlaying() || activeListeners.isEmpty()) {
                isLooping = false
                return
            }

            dispatchSyntheticSensorEvents()
            mainHandler.postDelayed(this, 20L) // 50 Hz sample rate
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
            module.log(Log.INFO, tag, "Biomechanical CadenceHooks installed successfully")
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
                            type == Sensor.TYPE_LINEAR_ACCELERATION ||
                            type == Sensor.TYPE_GRAVITY
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
            lastTickNanos = SystemClock.elapsedRealtimeNanos()
            mainHandler.post(periodicWaveformRunnable)
        }
    }

    private fun dispatchSyntheticSensorEvents() {
        if (activeListeners.isEmpty()) return
        val currentSpeed = LocationUtil.speed
        val targetPkg = LocationUtil.targetPackageName ?: ""
        val cadenceConfig = PreferencesUtil.getAppCadenceConfig(targetPkg)

        if (cadenceConfig.mode == TargetCadenceMode.OFF) return

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val dt = ((nowNanos - lastTickNanos).toDouble() / 1_000_000_000.0).coerceIn(0.005, 0.1)
        lastTickNanos = nowNanos

        // 1. Static / Stationary state (standing still, red light, or speed < 0.2 m/s)
        if (currentSpeed < 0.2f) {
            dispatchStationaryState(nowNanos)
            return
        }

        // 2. Active Movement state: Compute realistic biological cadence (SPM)
        val customSpm = if (cadenceConfig.mode == TargetCadenceMode.CUSTOM) cadenceConfig.customStepsPerMinute else null
        val baseSpm = LocationUtil.computeCadenceStepsPerMinute(currentSpeed, enabled = true, customSpm = customSpm)
        if (baseSpm <= 0) return

        // Compute step frequency (Hz): each step is half a gait cycle (2 steps per full stride)
        val fStep = baseSpm.toDouble() / 60.0
        val dPhase = 2.0 * PI * fStep * dt
        phaseRad = (phaseRad + dPhase) % (2.0 * PI)

        // Step event check with 3.5% human biological jitter
        var hasNewStep = false
        if (nowNanos - lastStepTimeNanos >= currentStepPeriodNanos) {
            lastStepTimeNanos = nowNanos
            totalAccumulatedSteps++
            hasNewStep = true
            val jitterRatio = 1.0 + (random.nextGaussian() * 0.035).coerceIn(-0.08, 0.08)
            currentStepPeriodNanos = ((60_000_000_000L / baseSpm) * jitterRatio).toLong()
        }

        val allowAccelerometer = cadenceConfig.channel == TargetCadenceChannel.ACCELEROMETER

        // 3. Biomechanical 3D Accelerometer Waveform Synthesis
        // Speed scaling factor:
        val speedFactor = (currentSpeed / 2.5).coerceIn(0.6, 2.2)

        // Phase within the current step (0 to PI)
        val stepPhase = phaseRad % PI
        val stepNormalized = stepPhase / PI // 0.0 .. 1.0 within step

        // a. Heel-Strike Impact Spike (足后跟落地脉冲冲击，前 18% 周期内产生高频阻尼冲击)
        val impactSpike = if (stepNormalized < 0.18) {
            val tau = stepNormalized / 0.18
            (5.5 * speedFactor * (1.0 - tau) * sin(tau * 3.0 * PI)).toFloat()
        } else 0f

        // b. Vertical oscillation bounce (身体重心升降谐波，频率为 2 * fStep)
        val bounceVertical = (2.8 * speedFactor * sin(2.0 * phaseRad)).toFloat()

        // c. Forward-backward push-off & swing (前后向摆动，基频 fStep)
        val forwardAcc = (2.2 * speedFactor * sin(phaseRad + 0.5 * PI)).toFloat()

        // d. Lateral leg alternation sway (左右脚交替侧向微晃，频率为 0.5 * fStep，即全步幅周期)
        val lateralSway = (1.4 * speedFactor * sin(0.5 * phaseRad)).toFloat()

        // e. Biological noise (微小肌肉与衣物晃动噪声)
        val noiseX = (random.nextGaussian() * 0.12).toFloat()
        val noiseY = (random.nextGaussian() * 0.12).toFloat()
        val noiseZ = (random.nextGaussian() * 0.18).toFloat()

        // Total raw accelerometer (with Earth's gravity 9.81 m/s^2 on Z-axis)
        val ax = lateralSway + noiseX
        val ay = forwardAcc + noiseY
        val az = 9.81f + bounceVertical + impactSpike + noiseZ

        // Pure linear acceleration (without gravity)
        val linAx = lateralSway + noiseX
        val linAy = forwardAcc + noiseY
        val linAz = bounceVertical + impactSpike + noiseZ

        for (entry in activeListeners) {
            runCatching {
                when (entry.sensorType) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (allowAccelerometer) {
                            val event = createSensorEvent(entry.sensor, floatArrayOf(ax, ay, az), nowNanos)
                            if (event != null) entry.listener.onSensorChanged(event)
                        }
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        if (allowAccelerometer) {
                            val event = createSensorEvent(entry.sensor, floatArrayOf(linAx, linAy, linAz), nowNanos)
                            if (event != null) entry.listener.onSensorChanged(event)
                        }
                    }
                    Sensor.TYPE_GRAVITY -> {
                        val event = createSensorEvent(entry.sensor, floatArrayOf(0f, 0f, 9.81f), nowNanos)
                        if (event != null) entry.listener.onSensorChanged(event)
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

    private fun dispatchStationaryState(nowNanos: Long) {
        val staticNoise = 0.03f
        val ax = (random.nextGaussian() * staticNoise).toFloat()
        val ay = (random.nextGaussian() * staticNoise).toFloat()
        val az = 9.81f + (random.nextGaussian() * staticNoise).toFloat()

        for (entry in activeListeners) {
            runCatching {
                when (entry.sensorType) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val event = createSensorEvent(entry.sensor, floatArrayOf(ax, ay, az), nowNanos)
                        if (event != null) entry.listener.onSensorChanged(event)
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val event = createSensorEvent(entry.sensor, floatArrayOf(ax, ay, 0f), nowNanos)
                        if (event != null) entry.listener.onSensorChanged(event)
                    }
                    Sensor.TYPE_GRAVITY -> {
                        val event = createSensorEvent(entry.sensor, floatArrayOf(0f, 0f, 9.81f), nowNanos)
                        if (event != null) entry.listener.onSensorChanged(event)
                    }
                }
            }
        }
    }

    private fun createSensorEvent(sensor: Sensor, values: FloatArray, timestampNanos: Long): SensorEvent? {
        return runCatching {
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

            findField(SensorEvent::class.java, "sensor")?.set(event, sensor)
            findField(SensorEvent::class.java, "timestamp")?.set(event, timestampNanos)
            findField(SensorEvent::class.java, "accuracy")?.set(event, SensorManager.SENSOR_STATUS_ACCURACY_HIGH)

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
