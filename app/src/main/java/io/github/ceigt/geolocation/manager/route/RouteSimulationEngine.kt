package io.github.ceigt.geolocation.manager.route

import android.os.SystemClock
import io.github.ceigt.geolocation.data.model.cadence.CadenceCalculator
import io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode
import io.github.ceigt.geolocation.data.model.route.*
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import kotlin.math.*

data class PathCorner(
    val pointIndex: Int,
    val turnAngleDegrees: Double,
    val cornerRadiusMeters: Double,
    val targetSpeedMetersPerSecond: Double,
    val transitionDistanceMeters: Double,
    val distanceAlongPathMeters: Double
)

data class RouteStopSchedule(
    val waypointIndex: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    var executed: Boolean = false
)

class RouteSimulationEngine {

    @Volatile
    var state: RouteSimulationState = RouteSimulationState()
        private set

    private var lastUpdateNanos: Long = 0L
    private var currentPhase: RouteMotionPhase = RouteMotionPhase.IDLE
    private var stopUntilNanos: Long = 0L

    // High-precision fractional step accumulator to prevent fractional step losses
    private var accumulatedStepsDouble: Double = 0.0

    // Pre-calculated corners and path cumulative distances
    private var cumulativeDistances: DoubleArray = doubleArrayOf()
    private var corners: List<PathCorner> = emptyList()
    private var stopSchedules: MutableList<RouteStopSchedule> = mutableListOf()
    private var totalPathDistanceMeters: Double = 0.0

    fun start(
        waypoints: List<RouteWaypoint>,
        travelMode: RouteTravelMode = RouteTravelMode.WALKING,
        loopMode: RouteLoopMode = RouteLoopMode.CYCLE,
        speedMps: Double = travelMode.defaultSpeedMps,
        cornerDecel: RouteCornerDecelerationMode = RouteCornerDecelerationMode.COMFORT,
        runMode: RouteRunMode = RouteRunMode.AUTOMATIC,
        cadenceMode: TargetCadenceMode = TargetCadenceMode.AUTO,
        customSpm: Int = 165
    ) {
        if (waypoints.isEmpty()) return
        val path = waypoints.map { GeoPoint(it.latitude, it.longitude) }
        precomputePath(path, waypoints, speedMps, cornerDecel)

        accumulatedStepsDouble = 0.0
        stopUntilNanos = 0L

        state = RouteSimulationState(
            isActive = true,
            isPaused = false,
            points = waypoints,
            plannedPath = path,
            traveledMeters = 0.0,
            speedMetersPerSecond = speedMps,
            travelMode = travelMode,
            loopMode = loopMode,
            runMode = runMode,
            cornerDecelerationMode = cornerDecel,
            cadenceMode = cadenceMode,
            customSpm = customSpm,
            currentStepCount = 0L,
            currentSpm = 0
        )
        lastUpdateNanos = SystemClock.elapsedRealtimeNanos()
        currentPhase = RouteMotionPhase.ACCELERATING
    }

    fun pause() {
        state = state.copy(
            isPaused = true,
            currentSpm = 0,
            speedMetersPerSecond = 0.0
        )
        currentPhase = RouteMotionPhase.PAUSED
    }

    fun resume() {
        state = state.copy(isPaused = false)
        lastUpdateNanos = SystemClock.elapsedRealtimeNanos()
        currentPhase = RouteMotionPhase.CRUISING
    }

    fun stop() {
        state = state.copy(
            isActive = false,
            isPaused = false,
            currentSpm = 0,
            speedMetersPerSecond = 0.0
        )
        currentPhase = RouteMotionPhase.IDLE
        stopUntilNanos = 0L
        accumulatedStepsDouble = 0.0
    }

    fun updateManualInput(bearingDegrees: Float, intensity: Float) {
        state = state.copy(
            manualBearingDegrees = bearingDegrees,
            manualIntensity = intensity.coerceIn(0f, 1f)
        )
    }

    /**
     * Advance simulation and return current computed location fix (latitude, longitude, speed, bearing)
     */
    fun tick(): RouteStepResult? {
        if (!state.isActive || state.isPaused) return null
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastUpdateNanos > 0L) ((now - lastUpdateNanos).toDouble() / 1_000_000_000.0).coerceIn(0.01, 1.0) else 0.1
        lastUpdateNanos = now

        return if (state.runMode == RouteRunMode.MANUAL) {
            stepManual(dt)
        } else {
            stepAutomatic(now, dt)
        }
    }

    private fun stepManual(dt: Double): RouteStepResult {
        val currentPoint = state.plannedPath.firstOrNull() ?: GeoPoint(0.0, 0.0)
        val maxSpeed = state.speedMetersPerSecond
        val currentSpeed = maxSpeed * state.manualIntensity
        val distanceDelta = currentSpeed * dt

        val bearingRad = Math.toRadians(state.manualBearingDegrees.toDouble())
        val northMeters = distanceDelta * cos(bearingRad)
        val eastMeters = distanceDelta * sin(bearingRad)

        val deltaLat = northMeters / 111320.0
        val cosLat = cos(Math.toRadians(currentPoint.latitude)).let { if (abs(it) < 1e-6) 1e-6 else it }
        val deltaLon = eastMeters / (111320.0 * cosLat)

        val newPoint = GeoPoint(
            (currentPoint.latitude + deltaLat).coerceIn(-90.0, 90.0),
            ((currentPoint.longitude + deltaLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        )

        val currentSpm = CadenceCalculator.computeSpm(currentSpeed, state.cadenceMode, state.customSpm)
        val deltaSteps = CadenceCalculator.computeDeltaSteps(currentSpeed, dt, state.cadenceMode, state.customSpm)
        accumulatedStepsDouble += deltaSteps
        val newStepCount = accumulatedStepsDouble.toLong()

        state = state.copy(
            plannedPath = listOf(newPoint),
            traveledMeters = state.traveledMeters + distanceDelta,
            speedMetersPerSecond = currentSpeed,
            currentStepCount = newStepCount,
            currentSpm = currentSpm
        )

        return RouteStepResult(
            latitude = newPoint.latitude,
            longitude = newPoint.longitude,
            speedMps = currentSpeed.toFloat(),
            bearingDegrees = state.manualBearingDegrees,
            stepCount = newStepCount,
            currentSpm = currentSpm
        )
    }

    private fun stepAutomatic(now: Long, dt: Double): RouteStepResult? {
        if (totalPathDistanceMeters <= 0.0 || state.plannedPath.size < 2) return null

        // 1. Check if currently dwelling at a stop/waypoint
        if (now < stopUntilNanos) {
            currentPhase = RouteMotionPhase.DECELERATING
            val cur = interpolatePosition(state.traveledMeters)
            state = state.copy(
                speedMetersPerSecond = 0.0,
                currentSpm = 0
            )
            return RouteStepResult(
                latitude = cur.first.latitude,
                longitude = cur.first.longitude,
                speedMps = 0f,
                bearingDegrees = cur.second.toFloat(),
                stepCount = state.currentStepCount,
                currentSpm = 0
            )
        }

        var speed = state.speedMetersPerSecond
        val currentDist = state.traveledMeters

        // 2. Check upcoming waypoint stop schedules
        for (schedule in stopSchedules) {
            if (!schedule.executed) {
                val distToStop = schedule.distanceMeters - currentDist
                if (distToStop in 0.0..15.0) {
                    val decelRatio = (distToStop / 15.0).coerceIn(0.1, 1.0)
                    speed *= decelRatio
                }
                if (distToStop in -1.5..1.5) {
                    schedule.executed = true
                    stopUntilNanos = now + (schedule.durationMillis * 1_000_000L)
                    val cur = interpolatePosition(schedule.distanceMeters)
                    state = state.copy(
                        speedMetersPerSecond = 0.0,
                        currentSpm = 0
                    )
                    return RouteStepResult(
                        latitude = cur.first.latitude,
                        longitude = cur.first.longitude,
                        speedMps = 0f,
                        bearingDegrees = cur.second.toFloat(),
                        stepCount = state.currentStepCount,
                        currentSpm = 0
                    )
                }
            }
        }

        // 3. Corner deceleration
        for (corner in corners) {
            val distToCorner = abs(currentDist - corner.distanceAlongPathMeters)
            if (distToCorner < corner.transitionDistanceMeters) {
                val ratio = (distToCorner / corner.transitionDistanceMeters).coerceIn(0.0, 1.0)
                val cornerSpeed = corner.targetSpeedMetersPerSecond
                speed = cornerSpeed + (speed - cornerSpeed) * ratio
                break
            }
        }

        val distanceDelta = speed * dt
        var newDist = state.traveledMeters + distanceDelta * state.direction

        // 4. Loop handling
        if (newDist >= totalPathDistanceMeters) {
            when (state.loopMode) {
                RouteLoopMode.CYCLE -> {
                    newDist %= totalPathDistanceMeters
                    stopSchedules.forEach { it.executed = false }
                }
                RouteLoopMode.BOUNCE -> {
                    newDist = totalPathDistanceMeters
                    state = state.copy(direction = -1)
                    stopSchedules.forEach { it.executed = false }
                }
            }
        } else if (newDist < 0.0) {
            when (state.loopMode) {
                RouteLoopMode.CYCLE -> {
                    newDist += totalPathDistanceMeters
                    stopSchedules.forEach { it.executed = false }
                }
                RouteLoopMode.BOUNCE -> {
                    newDist = 0.0
                    state = state.copy(direction = 1)
                    stopSchedules.forEach { it.executed = false }
                }
            }
        }

        val currentSpm = CadenceCalculator.computeSpm(speed, state.cadenceMode, state.customSpm)
        val deltaSteps = CadenceCalculator.computeDeltaSteps(speed, dt, state.cadenceMode, state.customSpm)
        accumulatedStepsDouble += deltaSteps
        val newStepCount = accumulatedStepsDouble.toLong()

        state = state.copy(
            traveledMeters = newDist,
            speedMetersPerSecond = speed,
            currentStepCount = newStepCount,
            currentSpm = currentSpm
        )
        val (point, bearing) = interpolatePosition(newDist)
        return RouteStepResult(
            latitude = point.latitude,
            longitude = point.longitude,
            speedMps = speed.toFloat(),
            bearingDegrees = bearing.toFloat(),
            stepCount = newStepCount,
            currentSpm = currentSpm
        )
    }

    private fun precomputePath(
        path: List<GeoPoint>,
        waypoints: List<RouteWaypoint>,
        cruiseSpeed: Double,
        cornerMode: RouteCornerDecelerationMode
    ) {
        val count = path.size
        cumulativeDistances = DoubleArray(count)
        var total = 0.0
        cumulativeDistances[0] = 0.0

        for (i in 1 until count) {
            val d = distanceMeters(path[i - 1], path[i])
            total += d
            cumulativeDistances[i] = total
        }
        totalPathDistanceMeters = total

        // Schedule stops for waypoints with duration > 0
        stopSchedules = mutableListOf()
        for (i in waypoints.indices) {
            val wp = waypoints[i]
            if (wp.stopDurationSeconds > 0 && i < count) {
                stopSchedules.add(
                    RouteStopSchedule(
                        waypointIndex = i,
                        distanceMeters = cumulativeDistances[i],
                        durationMillis = wp.stopDurationSeconds * 1000L
                    )
                )
            }
        }

        // Identify corners and calculate cornering target speeds
        val detectedCorners = mutableListOf<PathCorner>()
        val maxLateralG = if (cornerMode == RouteCornerDecelerationMode.DEEP) 0.15 else 0.25 // in g's
        val maxLateralAcc = maxLateralG * 9.81

        for (i in 1 until count - 1) {
            val b1 = bearingDegrees(path[i - 1], path[i])
            val b2 = bearingDegrees(path[i], path[i + 1])
            var turnAngle = abs(b2 - b1)
            if (turnAngle > 180.0) turnAngle = 360.0 - turnAngle

            if (turnAngle > 20.0) {
                val d1 = distanceMeters(path[i - 1], path[i])
                val d2 = distanceMeters(path[i], path[i + 1])
                val approxRadius = (min(d1, d2) / 2.0).coerceAtLeast(3.0)

                // v_max = sqrt(a_lat * r)
                val safeSpeed = sqrt(maxLateralAcc * approxRadius).coerceIn(1.0, cruiseSpeed)
                val transitionDist = (cruiseSpeed - safeSpeed).coerceAtLeast(0.0) * 2.0

                detectedCorners.add(
                    PathCorner(
                        pointIndex = i,
                        turnAngleDegrees = turnAngle,
                        cornerRadiusMeters = approxRadius,
                        targetSpeedMetersPerSecond = safeSpeed,
                        transitionDistanceMeters = transitionDist,
                        distanceAlongPathMeters = cumulativeDistances[i]
                    )
                )
            }
        }
        corners = detectedCorners
    }

    private fun interpolatePosition(distanceAlongPath: Double): Pair<GeoPoint, Double> {
        val count = cumulativeDistances.size
        if (count == 0) return Pair(GeoPoint(0.0, 0.0), 0.0)
        if (count == 1) return Pair(state.plannedPath[0], 0.0)

        val clampedDist = distanceAlongPath.coerceIn(0.0, totalPathDistanceMeters)
        var low = 0
        var high = count - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cumulativeDistances[mid] < clampedDist) low = mid + 1
            else high = mid - 1
        }

        val idx = (low - 1).coerceIn(0, count - 2)
        val segStartDist = cumulativeDistances[idx]
        val segEndDist = cumulativeDistances[idx + 1]
        val segLength = segEndDist - segStartDist
        val fraction = if (segLength > 1e-4) (clampedDist - segStartDist) / segLength else 0.0

        val p1 = state.plannedPath[idx]
        val p2 = state.plannedPath[idx + 1]
        val lat = p1.latitude + fraction * (p2.latitude - p1.latitude)
        val lon = p1.longitude + fraction * (p2.longitude - p1.longitude)
        val bearing = bearingDegrees(p1, p2)
        return Pair(GeoPoint(lat, lon), bearing)
    }

    private fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2.0).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return 6378137.0 * c
    }

    private fun bearingDegrees(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
