package io.github.ceigt.geolocation.data.model.cadence

import kotlin.math.roundToInt

object CadenceCalculator {

    /**
     * Compute realistic biomechanical step cadence (SPM - steps per minute).
     * Single authoritative implementation for both Route Simulation and Sensor Hooks.
     *
     * Rules:
     * - If disabled or mode is OFF -> 0 SPM
     * - If speed < 0.2 m/s (stationary / dwelling) -> 0 SPM
     * - If mode is CUSTOM and customSpm > 0 -> customSpm
     * - If mode is AUTO (or custom not set):
     *   * Walking (< 2.2 m/s, ~8 km/h): 90..150 SPM linear progression (80 + 32 * v)
     *   * Running (>= 2.2 m/s): 150..205 SPM progression (150 + 8 * (v - 2.2))
     */
    fun computeSpm(
        speedMps: Double,
        mode: TargetCadenceMode = TargetCadenceMode.AUTO,
        customSpm: Int? = null
    ): Int {
        if (mode == TargetCadenceMode.OFF || speedMps < 0.2) return 0
        if (mode == TargetCadenceMode.CUSTOM && customSpm != null && customSpm > 0) {
            return customSpm.coerceIn(40, 260)
        }
        return if (speedMps < 2.2) {
            (speedMps * 32.0 + 80.0).roundToInt().coerceIn(90, 150)
        } else {
            (150.0 + (speedMps - 2.2) * 8.0).roundToInt().coerceIn(150, 205)
        }
    }

    /**
     * Compute dynamic stride length in meters based on speed and cadence.
     * Ensures physical coherence: Speed = StrideLength * (SPM / 60)
     */
    fun computeStrideLengthMeters(speedMps: Double, spm: Int): Double {
        if (spm <= 0 || speedMps < 0.1) return 0.0
        return (speedMps / (spm.toDouble() / 60.0)).coerceIn(0.45, 1.15)
    }

    /**
     * Calculate delta steps for a time slice dt seconds.
     * When mode == CUSTOM: step increment is strictly governed by customSpm / 60 * dt,
     * guaranteeing exact agreement between set SPM and total accumulated steps over time!
     * When mode == AUTO: steps = (speed * dt) / strideLength == (spm / 60) * dt.
     * When mode == OFF: 0.0 steps.
     */
    fun computeDeltaSteps(
        speedMps: Double,
        dtSeconds: Double,
        mode: TargetCadenceMode,
        customSpm: Int?
    ): Double {
        if (mode == TargetCadenceMode.OFF || speedMps < 0.2 || dtSeconds <= 0.0) return 0.0
        val spm = computeSpm(speedMps, mode, customSpm)
        if (spm <= 0) return 0.0
        return (spm.toDouble() / 60.0) * dtSeconds
    }
}
