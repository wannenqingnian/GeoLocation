package io.github.ceigt.geolocation.simulation.frame

import android.location.Location
import io.github.ceigt.geolocation.data.model.gnss.GnssAccuracyCustomSettings
import io.github.ceigt.geolocation.data.model.gnss.GnssJitterAlgorithm
import io.github.ceigt.geolocation.data.model.gnss.GnssJitterCustomSettings
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MeterOffset(
    val eastMeters: Double,
    val northMeters: Double
)

class LocationFrameAssembler(
    private var jitterSettings: GnssJitterCustomSettings = GnssJitterCustomSettings(),
    private var accuracySettings: GnssAccuracyCustomSettings = GnssAccuracyCustomSettings()
) {
    private val random = Random()
    private var lastTimestampMs: Long = 0L

    // Jitter state
    private var currentEastOffset: Double = 0.0
    private var currentNorthOffset: Double = 0.0
    private var prevEastVelocity: Double = 0.0
    private var prevNorthVelocity: Double = 0.0

    fun updateSettings(jitter: GnssJitterCustomSettings, accuracy: GnssAccuracyCustomSettings) {
        this.jitterSettings = jitter
        this.accuracySettings = accuracy
    }

    /**
     * Compute meter-level jitter offset based on the configured algorithm
     */
    fun computeOffset(timestampMs: Long): MeterOffset {
        if (!jitterSettings.enabled || jitterSettings.radiusMeters <= 0.0) {
            return MeterOffset(0.0, 0.0)
        }

        val dt = if (lastTimestampMs > 0L) {
            ((timestampMs - lastTimestampMs).toDouble() / 1000.0).coerceIn(0.05, 5.0)
        } else {
            1.0 / jitterSettings.updateRateHz.coerceAtLeast(0.1)
        }
        lastTimestampMs = timestampMs

        val sigma = jitterSettings.radiusMeters
        val alpha = jitterSettings.smoothingRatio.coerceIn(0.01, 0.99)

        when (jitterSettings.algorithm) {
            GnssJitterAlgorithm.FILTERED_GAUSSIAN -> {
                // Low-pass filtered Gaussian noise: x_t = alpha * x_{t-1} + (1 - alpha) * w_t
                val rawEast = random.nextGaussian() * sigma
                val rawNorth = random.nextGaussian() * sigma
                currentEastOffset = alpha * currentEastOffset + (1.0 - alpha) * rawEast
                currentNorthOffset = alpha * currentNorthOffset + (1.0 - alpha) * rawNorth
            }
            GnssJitterAlgorithm.FIRST_ORDER_MARKOV -> {
                // First-order Gauss-Markov: x_t = alpha * x_{t-1} + sqrt(1 - alpha^2) * sigma * epsilon_t
                val innovationScale = sqrt((1.0 - alpha * alpha).coerceAtLeast(0.001)) * sigma
                val epsEast = random.nextGaussian()
                val epsNorth = random.nextGaussian()
                currentEastOffset = alpha * currentEastOffset + innovationScale * epsEast
                currentNorthOffset = alpha * currentNorthOffset + innovationScale * epsNorth
            }
            GnssJitterAlgorithm.SECOND_ORDER_MARKOV -> {
                // Second-order Markov with inertial drift & directional persistence
                val dirInertia = jitterSettings.directionInertiaRatio.coerceIn(0.0, 0.99)
                val continuity = jitterSettings.continuityRatio.coerceIn(0.0, 0.99)
                val maxDriftSpeed = jitterSettings.driftSpeedMetersPerSecond

                val accEast = random.nextGaussian() * (1.0 - dirInertia) * maxDriftSpeed
                val accNorth = random.nextGaussian() * (1.0 - dirInertia) * maxDriftSpeed

                prevEastVelocity = dirInertia * prevEastVelocity + accEast * dt
                prevNorthVelocity = dirInertia * prevNorthVelocity + accNorth * dt

                // Clamp drift velocity
                val currentSpeed = sqrt(prevEastVelocity * prevEastVelocity + prevNorthVelocity * prevNorthVelocity)
                if (currentSpeed > maxDriftSpeed && maxDriftSpeed > 0) {
                    val scale = maxDriftSpeed / currentSpeed
                    prevEastVelocity *= scale
                    prevNorthVelocity *= scale
                }

                currentEastOffset = continuity * currentEastOffset + prevEastVelocity * dt
                currentNorthOffset = continuity * currentNorthOffset + prevNorthVelocity * dt
            }
        }

        // Clamp to radius
        val dist = sqrt(currentEastOffset * currentEastOffset + currentNorthOffset * currentNorthOffset)
        if (dist > sigma && sigma > 0) {
            val scale = sigma / dist
            currentEastOffset *= scale
            currentNorthOffset *= scale
        }

        return MeterOffset(currentEastOffset, currentNorthOffset)
    }

    /**
     * Compute dynamic accuracy with periodic / stochastic variation
     */
    fun computeAccuracy(timestampMs: Long): Pair<Float, Float> {
        val baseH = accuracySettings.horizontalMeters.coerceAtLeast(0.5f)
        val hAccuracy = if (accuracySettings.variationEnabled) {
            val phase = (timestampMs / 1000.0) * 2.0 * Math.PI * accuracySettings.variationRateHz
            val osc = sin(phase).toFloat() * accuracySettings.variationRatio
            val noise = (random.nextGaussian().toFloat() * 0.05f)
            (baseH * (1.0f + osc + noise)).coerceAtLeast(0.5f)
        } else {
            baseH
        }
        val vAccuracy = hAccuracy * accuracySettings.verticalMultiplier
        return Pair(hAccuracy, vAccuracy)
    }

    /**
     * Assemble final location coordinates and precision
     */
    fun applyJitter(
        baseLatitude: Double,
        baseLongitude: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): Triple<Double, Double, Pair<Float, Float>> {
        val offset = computeOffset(timestampMs)
        val latRad = Math.toRadians(baseLatitude)

        // Delta lat: 1 deg lat ~ 111320 meters
        val deltaLat = offset.northMeters / 111320.0
        // Delta lon: 1 deg lon ~ 111320 * cos(lat) meters
        val cosLat = cos(latRad).let { if (Math.abs(it) < 1e-6) 1e-6 else it }
        val deltaLon = offset.eastMeters / (111320.0 * cosLat)

        val newLat = (baseLatitude + deltaLat).coerceIn(-90.0, 90.0)
        val newLon = ((baseLongitude + deltaLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

        val accuracyPair = computeAccuracy(timestampMs)
        return Triple(newLat, newLon, accuracyPair)
    }
}
