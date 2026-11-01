package io.github.ceigt.geolocation.domain.motion

import android.hardware.Sensor
import io.github.ceigt.geolocation.data.model.motion.*
import kotlin.math.*

object MotionEvaluator {

    /**
     * 对已记录的真实传感器会话进行离线全面对照评估：
     * 1. 总步数误差（计算步数与人工步数相差多少）
     * 2. 分段步频误差（每 10 秒时间片内的步数与 SPM 响应）
     * 3. 状态响应（静止、起步、运动、停止是否准确切分）
     * 4. 重复性（同一文件多次计算是否 100% 幂等一致）
     */
    fun evaluate(session: MotionRecordSession): MotionEvaluationReport {
        // Run pass 1
        val (computedSteps1, slices1, statSlices1, motionSlices1) = analyzeSamples(session)
        // Run pass 2 for repeatability verification
        val (computedSteps2, _, _, _) = analyzeSamples(session)
        val repeatabilityPass = (computedSteps1 == computedSteps2)

        val manualSteps = session.manualStepCount
        val stepDiff = computedSteps1 - manualSteps
        val stepErrorPct = if (manualSteps > 0) {
            (abs(stepDiff).toDouble() / manualSteps.toDouble()) * 100.0
        } else if (computedSteps1 == 0) 0.0 else 100.0

        val durSec = session.durationSeconds.coerceAtLeast(0.1)
        val manualSpm = session.manualCadenceSpm
        val computedSpm = (computedSteps1.toDouble() / durSec) * 60.0
        val cadenceErrorPct = if (manualSpm > 0) {
            (abs(computedSpm - manualSpm) / manualSpm) * 100.0
        } else if (computedSpm < 5.0) 0.0 else 100.0

        val stateResponseOk = when (session.activityType) {
            MotionActivityType.STATIONARY -> statSlices1 > 0 && motionSlices1 == 0
            else -> motionSlices1 > 0
        }

        val summary = buildString {
            append("【评估总结】")
            if (abs(stepDiff) <= 3 || stepErrorPct <= 5.0) {
                append("步数高精度拟合 (误差 ${"%.1f".format(stepErrorPct)}%)；")
            } else if (stepErrorPct <= 15.0) {
                append("步数良度拟合 (误差 ${"%.1f".format(stepErrorPct)}%)；")
            } else {
                append("步数存在偏差 (相差 $stepDiff 步)；")
            }
            append("状态识别: ${if (stateResponseOk) "精准响应" else "存在过渡模糊"}；")
            append("回放确定性: ${if (repeatabilityPass) "100% 确定性幂等" else "非确定性"}")
        }

        return MotionEvaluationReport(
            sessionId = session.id,
            sessionTitle = session.title,
            manualStepCount = manualSteps,
            computedStepCount = computedSteps1,
            stepCountDiff = stepDiff,
            stepCountErrorPercent = stepErrorPct,
            durationSeconds = durSec,
            manualAvgSpm = manualSpm,
            computedAvgSpm = computedSpm,
            cadenceErrorPercent = cadenceErrorPct,
            segmentedSpmList = slices1,
            detectedStationarySlices = statSlices1,
            detectedMotionSlices = motionSlices1,
            stateResponseOk = stateResponseOk,
            repeatabilityPass = repeatabilityPass,
            summaryEvaluation = summary
        )
    }

    private data class AnalysisResult(
        val totalSteps: Int,
        val slices: List<SegmentedCadenceSlice>,
        val stationarySlices: Int,
        val motionSlices: Int
    )

    private fun analyzeSamples(session: MotionRecordSession): AnalysisResult {
        val samples = session.samples
        if (samples.isEmpty()) {
            return AnalysisResult(0, emptyList(), 0, 0)
        }

        // 1. Try hardware Step Detector count first
        val detectorSteps = samples.count { it.sensorType == Sensor.TYPE_STEP_DETECTOR }

        // 2. Also run robust acceleration magnitude peak-detection algorithm
        val accSamples = samples.filter { it.sensorType == Sensor.TYPE_ACCELEROMETER }
        val peakSteps = if (accSamples.isNotEmpty()) {
            detectStepsFromAccelerometer(accSamples)
        } else 0

        // Use detector if available, otherwise fallback to peak detection
        val effectiveSteps = if (detectorSteps > 0) detectorSteps else peakSteps

        // Segment into 10-second slices
        val startTs = samples.first().timestampNanos
        val endTs = samples.last().timestampNanos
        val totalDurSec = ((endTs - startTs).toDouble() / 1_000_000_000.0).coerceAtLeast(1.0)
        val sliceCount = max(1, ceil(totalDurSec / 10.0).toInt())

        val slices = mutableListOf<SegmentedCadenceSlice>()
        var stationaryCount = 0
        var motionCount = 0

        for (i in 0 until sliceCount) {
            val t0 = i * 10.0
            val t1 = min((i + 1) * 10.0, totalDurSec)
            val dt = (t1 - t0).coerceAtLeast(0.1)

            val sStartNanos = startTs + (t0 * 1_000_000_000.0).toLong()
            val sEndNanos = startTs + (t1 * 1_000_000_000.0).toLong()

            val stepsInSlice = if (detectorSteps > 0) {
                samples.count { it.sensorType == Sensor.TYPE_STEP_DETECTOR && it.timestampNanos in sStartNanos..sEndNanos }
            } else {
                val sliceAcc = accSamples.filter { it.timestampNanos in sStartNanos..sEndNanos }
                detectStepsFromAccelerometer(sliceAcc)
            }

            val spm = ((stepsInSlice.toDouble() / dt) * 60.0).roundToInt()
            val isStationary = stepsInSlice == 0 || spm < 30
            if (isStationary) stationaryCount++ else motionCount++

            slices.add(
                SegmentedCadenceSlice(
                    sliceIndex = i + 1,
                    startSecond = t0,
                    endSecond = t1,
                    stepsInSlice = stepsInSlice,
                    instantaneousSpm = spm,
                    isStationary = isStationary
                )
            )
        }

        return AnalysisResult(effectiveSteps, slices, stationaryCount, motionCount)
    }

    /**
     * 基于加速度模长峰值与动态阈值的经典计步滤波检测器
     */
    fun detectStepsFromAccelerometer(samples: List<SensorSample>): Int {
        if (samples.size < 4) return 0
        var steps = 0
        var lastPeakTimeNanos = 0L

        // Compute magnitude with gravity baseline removal
        val magnitudes = DoubleArray(samples.size)
        for (i in samples.indices) {
            val v = samples[i].values
            magnitudes[i] = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2].toDouble())
        }

        // Peak detection threshold: > 10.8 m/s^2 (impact threshold) with minimum 240ms refractory period (~250 SPM max)
        val minIntervalNanos = 240_000_000L
        val peakThreshold = 10.9

        for (i in 1 until samples.size - 1) {
            val prev = magnitudes[i - 1]
            val curr = magnitudes[i]
            val next = magnitudes[i + 1]

            if (curr > prev && curr > next && curr >= peakThreshold) {
                val ts = samples[i].timestampNanos
                if (ts - lastPeakTimeNanos >= minIntervalNanos) {
                    steps++
                    lastPeakTimeNanos = ts
                }
            }
        }
        return steps
    }
}
