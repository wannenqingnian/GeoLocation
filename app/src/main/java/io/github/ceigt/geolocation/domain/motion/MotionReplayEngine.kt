package io.github.ceigt.geolocation.domain.motion

import android.hardware.Sensor
import io.github.ceigt.geolocation.data.model.motion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MotionReplayState(
    val isPlaying: Boolean = false,
    val isFinished: Boolean = false,
    val currentSampleIndex: Int = 0,
    val totalSamples: Int = 0,
    val elapsedPlaybackSeconds: Double = 0.0,
    val totalDurationSeconds: Double = 0.0,
    val currentAccMagnitude: Float = 9.81f,
    val currentValues: FloatArray = floatArrayOf(0f, 0f, 9.81f),
    val accumulatedSteps: Int = 0,
    val instantaneousSpm: Int = 0,
    val playbackSpeed: Double = 1.0,
    // Recent window of magnitude values for chart rendering (last 80 points)
    val recentMagnitudeWindow: List<Float> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MotionReplayState
        return isPlaying == other.isPlaying &&
                isFinished == other.isFinished &&
                currentSampleIndex == other.currentSampleIndex &&
                totalSamples == other.totalSamples &&
                elapsedPlaybackSeconds == other.elapsedPlaybackSeconds &&
                totalDurationSeconds == other.totalDurationSeconds &&
                currentAccMagnitude == other.currentAccMagnitude &&
                currentValues.contentEquals(other.currentValues) &&
                accumulatedSteps == other.accumulatedSteps &&
                instantaneousSpm == other.instantaneousSpm &&
                playbackSpeed == other.playbackSpeed &&
                recentMagnitudeWindow == other.recentMagnitudeWindow
    }

    override fun hashCode(): Int {
        var result = isPlaying.hashCode()
        result = 31 * result + isFinished.hashCode()
        result = 31 * result + currentSampleIndex
        result = 31 * result + totalSamples
        result = 31 * result + elapsedPlaybackSeconds.hashCode()
        result = 31 * result + totalDurationSeconds.hashCode()
        result = 31 * result + currentAccMagnitude.hashCode()
        result = 31 * result + currentValues.contentHashCode()
        result = 31 * result + accumulatedSteps
        result = 31 * result + instantaneousSpm
        result = 31 * result + playbackSpeed.hashCode()
        result = 31 * result + recentMagnitudeWindow.hashCode()
        return result
    }
}

/**
 * 离线传感器数据回放与实时特征计算引擎
 * 忠于真实采样时间间隔（不压缩停靠与断采时间），且步数计算与离线评估算法 100% 对齐。
 */
class MotionReplayEngine {

    private val _state = MutableStateFlow(MotionReplayState())
    val state: StateFlow<MotionReplayState> = _state.asStateFlow()

    private var session: MotionRecordSession? = null
    private var replayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun loadSession(loadedSession: MotionRecordSession) {
        stop()
        session = loadedSession
        val samples = loadedSession.samples
        _state.value = MotionReplayState(
            isPlaying = false,
            isFinished = false,
            currentSampleIndex = 0,
            totalSamples = samples.size,
            elapsedPlaybackSeconds = 0.0,
            totalDurationSeconds = loadedSession.durationSeconds,
            currentAccMagnitude = 9.81f,
            currentValues = floatArrayOf(0f, 0f, 9.81f),
            accumulatedSteps = 0,
            instantaneousSpm = 0
        )
    }

    fun play(playbackSpeed: Double = _state.value.playbackSpeed) {
        val s = session ?: return
        val samples = s.samples
        if (samples.isEmpty()) return

        replayJob?.cancel()
        _state.value = _state.value.copy(isPlaying = true, isFinished = false, playbackSpeed = playbackSpeed)

        val hasStepDetectorEvents = samples.any { it.sensorType == Sensor.TYPE_STEP_DETECTOR }

        replayJob = scope.launch {
            var idx = _state.value.currentSampleIndex
            val startTs = samples.first().timestampNanos
            val magWindow = mutableListOf<Float>()
            var steps = _state.value.accumulatedSteps
            var lastStepTs = 0L

            var prevMag = 9.81
            var currMag = 9.81

            while (isActive && idx < samples.size) {
                val current = samples[idx]
                val next = if (idx + 1 < samples.size) samples[idx + 1] else null

                val elapsedSec = (current.timestampNanos - startTs).toDouble() / 1_000_000_000.0

                if (current.sensorType == Sensor.TYPE_ACCELEROMETER) {
                    val mag = sqrt(current.values[0] * current.values[0] + current.values[1] * current.values[1] + current.values[2] * current.values[2].toDouble())
                    magWindow.add(mag.toFloat())
                    if (magWindow.size > 80) magWindow.removeAt(0)

                    // If device lacks hardware STEP_DETECTOR, detect steps from accelerometer peaks
                    // identical to MotionEvaluator.detectStepsFromAccelerometer
                    if (!hasStepDetectorEvents && idx > 0 && idx < samples.size - 1) {
                        val nextSample = samples[idx + 1]
                        if (nextSample.sensorType == Sensor.TYPE_ACCELEROMETER) {
                            val nextMag = sqrt(nextSample.values[0] * nextSample.values[0] + nextSample.values[1] * nextSample.values[1] + nextSample.values[2] * nextSample.values[2].toDouble())
                            if (mag > prevMag && mag > nextMag && mag >= 10.9) {
                                val nowNanos = current.timestampNanos
                                if (nowNanos - lastStepTs >= 240_000_000L) {
                                    steps++
                                    val intervalSec = if (lastStepTs > 0L) (nowNanos - lastStepTs).toDouble() / 1_000_000_000.0 else 0.5
                                    val spm = if (intervalSec in 0.25..2.0) (60.0 / intervalSec).roundToInt() else 0
                                    lastStepTs = nowNanos

                                    _state.value = _state.value.copy(
                                        accumulatedSteps = steps,
                                        instantaneousSpm = spm
                                    )
                                }
                            }
                        }
                    }
                    prevMag = currMag
                    currMag = mag

                    _state.value = _state.value.copy(
                        currentSampleIndex = idx,
                        elapsedPlaybackSeconds = elapsedSec,
                        currentAccMagnitude = mag.toFloat(),
                        currentValues = current.values,
                        recentMagnitudeWindow = ArrayList(magWindow)
                    )
                } else if (current.sensorType == Sensor.TYPE_STEP_DETECTOR && hasStepDetectorEvents) {
                    steps++
                    val nowNanos = current.timestampNanos
                    val spm = if (lastStepTs > 0L) {
                        val intervalSec = (nowNanos - lastStepTs).toDouble() / 1_000_000_000.0
                        if (intervalSec in 0.25..2.0) (60.0 / intervalSec).roundToInt() else 0
                    } else 0
                    lastStepTs = nowNanos

                    _state.value = _state.value.copy(
                        currentSampleIndex = idx,
                        elapsedPlaybackSeconds = elapsedSec,
                        accumulatedSteps = steps,
                        instantaneousSpm = spm
                    )
                }

                idx++

                // Faithfully preserve recorded timestamp delta without artificially chopping long pauses
                if (next != null) {
                    val deltaNanos = (next.timestampNanos - current.timestampNanos).coerceAtLeast(0L)
                    val delayMillis = ((deltaNanos / 1_000_000L).toDouble() / playbackSpeed).toLong()
                    if (delayMillis > 0L) {
                        delay(delayMillis)
                    }
                }
            }

            _state.value = _state.value.copy(isPlaying = false, isFinished = true)
        }
    }

    fun pause() {
        replayJob?.cancel()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun reset() {
        stop()
        session?.let { loadSession(it) }
    }

    fun stop() {
        replayJob?.cancel()
        _state.value = _state.value.copy(isPlaying = false)
    }
}
