package io.github.ceigt.geolocation.domain.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import io.github.ceigt.geolocation.data.model.motion.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

data class MotionCollectorState(
    val isRecording: Boolean = false,
    val sampleCount: Long = 0L,
    val durationSeconds: Long = 0L,
    val latestAccMagnitude: Float = 9.81f,
    val latestValues: FloatArray = floatArrayOf(0f, 0f, 9.81f),
    val hardwareSensorSteps: Int = 0,
    val stepDetectorHits: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MotionCollectorState
        return isRecording == other.isRecording &&
                sampleCount == other.sampleCount &&
                durationSeconds == other.durationSeconds &&
                latestAccMagnitude == other.latestAccMagnitude &&
                latestValues.contentEquals(other.latestValues) &&
                hardwareSensorSteps == other.hardwareSensorSteps &&
                stepDetectorHits == other.stepDetectorHits
    }

    override fun hashCode(): Int {
        var result = isRecording.hashCode()
        result = 31 * result + sampleCount.hashCode()
        result = 31 * result + durationSeconds.hashCode()
        result = 31 * result + latestAccMagnitude.hashCode()
        result = 31 * result + latestValues.contentHashCode()
        result = 31 * result + hardwareSensorSteps
        result = 31 * result + stepDetectorHits
        return result
    }
}

/**
 * 真实传感器运动数据采集器
 */
class MotionDataCollector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _state = MutableStateFlow(MotionCollectorState())
    val state: StateFlow<MotionCollectorState> = _state.asStateFlow()

    private val sampleCounter = AtomicLong(0L)
    private val recordedSamples = Collections.synchronizedList(mutableListOf<SensorSample>())

    private var startTimestampEpochMs: Long = 0L
    private var baseStepCounterValue: Int? = null
    private var initialStepCount: Int = 0

    // Availability cache
    val availability: SensorAvailability by lazy {
        val hasAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val hasStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val hasStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null

        val missing = mutableListOf<String>()
        if (!hasAcc) missing.add("加速度计 (ACCELEROMETER)")
        if (!hasGyro) missing.add("陀螺仪 (GYROSCOPE)")
        if (!hasStepCounter) missing.add("计步器 (STEP_COUNTER)")
        if (!hasStepDetector) missing.add("步伐检测器 (STEP_DETECTOR)")

        SensorAvailability(hasAcc, hasGyro, hasStepCounter, hasStepDetector, missing)
    }

    fun startRecording() {
        if (_state.value.isRecording) return
        recordedSamples.clear()
        sampleCounter.set(0L)
        startTimestampEpochMs = System.currentTimeMillis()
        baseStepCounterValue = null
        initialStepCount = 0

        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        // Register at 50Hz (SENSOR_DELAY_GAME = 20,000 microseconds)
        acc?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        counter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        detector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }

        _state.value = MotionCollectorState(isRecording = true)
    }

    fun stopRecording(
        title: String,
        activityType: MotionActivityType,
        carryPosition: PhoneCarryPosition,
        manualStepCount: Int,
        note: String = ""
    ): MotionRecordSession {
        sensorManager.unregisterListener(this)
        val endTime = System.currentTimeMillis()
        _state.value = _state.value.copy(isRecording = false)

        val samplesSnapshot = synchronized(recordedSamples) { ArrayList(recordedSamples) }

        return MotionRecordSession(
            title = title.ifBlank { "真实采集-${activityType.label}" },
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            carryPosition = carryPosition,
            activityType = activityType,
            startTimeEpochMs = startTimestampEpochMs,
            endTimeEpochMs = endTime,
            manualStepCount = manualStepCount,
            sensorAvailability = availability,
            samples = samplesSnapshot,
            note = note
        )
    }

    fun cancelRecording() {
        sensorManager.unregisterListener(this)
        recordedSamples.clear()
        _state.value = MotionCollectorState(isRecording = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!_state.value.isRecording || event == null) return

        val index = sampleCounter.incrementAndGet()
        val vals = event.values.clone()
        val sample = SensorSample(
            sampleIndex = index,
            sensorType = event.sensor.type,
            timestampNanos = event.timestamp,
            values = vals,
            accuracy = event.accuracy
        )
        recordedSamples.add(sample)

        val nowEpoch = System.currentTimeMillis()
        val dur = ((nowEpoch - startTimestampEpochMs) / 1000L).coerceAtLeast(0L)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val mag = kotlin.math.sqrt(vals[0] * vals[0] + vals[1] * vals[1] + vals[2] * vals[2])
                _state.value = _state.value.copy(
                    sampleCount = index,
                    durationSeconds = dur,
                    latestAccMagnitude = mag,
                    latestValues = vals
                )
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val rawCount = vals[0].toInt()
                if (baseStepCounterValue == null) {
                    baseStepCounterValue = rawCount
                }
                val delta = (rawCount - (baseStepCounterValue ?: rawCount)).coerceAtLeast(0)
                _state.value = _state.value.copy(
                    sampleCount = index,
                    durationSeconds = dur,
                    hardwareSensorSteps = delta
                )
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                _state.value = _state.value.copy(
                    sampleCount = index,
                    durationSeconds = dur,
                    stepDetectorHits = _state.value.stepDetectorHits + 1
                )
            }
            else -> {
                _state.value = _state.value.copy(
                    sampleCount = index,
                    durationSeconds = dur
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
