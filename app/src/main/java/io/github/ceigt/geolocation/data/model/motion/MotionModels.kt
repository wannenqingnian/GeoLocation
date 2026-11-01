package io.github.ceigt.geolocation.data.model.motion

import android.hardware.Sensor
import java.util.UUID

/**
 * 运动类型标记
 */
enum class MotionActivityType(val label: String) {
    STATIONARY("静止站立"),
    SLOW_WALK("慢速步行 (~3 km/h)"),
    BRISK_WALK("健步快走 (~5 km/h)"),
    JOGGING("慢跑 (~8 km/h)"),
    RUNNING("快速跑步 (~12 km/h)"),
    STAIRS("上下楼梯");

    companion object {
        fun fromName(name: String?): MotionActivityType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BRISK_WALK
    }
}

/**
 * 手机携带/佩戴位置
 */
enum class PhoneCarryPosition(val label: String) {
    HANDHELD("手持查看/自然摆臂"),
    PANT_POCKET("裤子口袋 (前袋/侧袋)"),
    JACKET_POCKET("上衣/外套口袋"),
    ARMBAND("手臂运动臂包"),
    BACKPACK("双肩背包/斜挎包");

    companion object {
        fun fromName(name: String?): PhoneCarryPosition =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PANT_POCKET
    }
}

/**
 * 单条原始传感器记录点：
 * 采集编号、传感器类型、原始时间戳、数值、精度状态
 */
data class SensorSample(
    val sampleIndex: Long,
    val sensorType: Int,
    val timestampNanos: Long,
    val values: FloatArray,
    val accuracy: Int
) {
    val sensorName: String
        get() = when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> "加速度计"
            Sensor.TYPE_GYROSCOPE -> "陀螺仪"
            Sensor.TYPE_STEP_COUNTER -> "计步器"
            Sensor.TYPE_STEP_DETECTOR -> "步伐检测器"
            else -> "传感器($sensorType)"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorSample
        return sampleIndex == other.sampleIndex &&
                sensorType == other.sensorType &&
                timestampNanos == other.timestampNanos &&
                values.contentEquals(other.values) &&
                accuracy == other.accuracy
    }

    override fun hashCode(): Int {
        var result = sampleIndex.hashCode()
        result = 31 * result + sensorType
        result = 31 * result + timestampNanos.hashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + accuracy
        return result
    }
}

/**
 * 传感器硬件可用性状态
 */
data class SensorAvailability(
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val hasStepCounter: Boolean,
    val hasStepDetector: Boolean,
    val missingSensors: List<String> = emptyList()
)

/**
 * 完整采集会话元数据与数据包：
 * 手机型号、携带位置、运动类型、开始结束时间、人工统计步数、样本列表
 */
data class MotionRecordSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val deviceModel: String,
    val androidVersion: String,
    val carryPosition: PhoneCarryPosition = PhoneCarryPosition.PANT_POCKET,
    val activityType: MotionActivityType = MotionActivityType.BRISK_WALK,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val manualStepCount: Int,
    val sensorAvailability: SensorAvailability,
    val samples: List<SensorSample> = emptyList(),
    val note: String = ""
) {
    val durationSeconds: Double
        get() = if (endTimeEpochMs > startTimeEpochMs) {
            (endTimeEpochMs - startTimeEpochMs) / 1000.0
        } else 0.0

    val manualCadenceSpm: Double
        get() = if (durationSeconds > 1.0) {
            (manualStepCount.toDouble() / durationSeconds) * 60.0
        } else 0.0
}

/**
 * 运动特征与对照评估报告指标
 */
data class MotionEvaluationReport(
    val sessionId: String,
    val sessionTitle: String,
    val manualStepCount: Int,
    val computedStepCount: Int,
    val stepCountDiff: Int,
    val stepCountErrorPercent: Double,
    val durationSeconds: Double,
    val manualAvgSpm: Double,
    val computedAvgSpm: Double,
    val cadenceErrorPercent: Double,
    // 分段步频（每 10 秒一段的步数与 SPM）
    val segmentedSpmList: List<SegmentedCadenceSlice>,
    // 状态响应指标
    val detectedStationarySlices: Int,
    val detectedMotionSlices: Int,
    val stateResponseOk: Boolean,
    // 重复性测试是否 100% 幂等
    val repeatabilityPass: Boolean,
    val summaryEvaluation: String
)

data class SegmentedCadenceSlice(
    val sliceIndex: Int,
    val startSecond: Double,
    val endSecond: Double,
    val stepsInSlice: Int,
    val instantaneousSpm: Int,
    val isStationary: Boolean
)
