package io.github.ceigt.geolocation.manager.route

import android.os.SystemClock
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

    // Pre-calculated corners and path cumulative distances
    private var cumulativeDistances: DoubleArray = doubleArrayOf()
    private var corners: List<PathCorner> = emptyList()
    private var stopSchedules: MutableList<RouteStopSchedule> = mutableListOf()
    private var totalPathDistanceMeters: Double = 0.0

    fun start(
        waypoints: List<RouteWaypoint>,
        travelMode: RouteTravelMode = RouteTravelMode.WALKING,
        loopMode: RouteLoopMode = RouteLoopMode.CYCLE,
        cornerDecel: RouteCornerDecelerationMode = RouteCornerDecelerationMode.COMFORT,
        runMode: RouteRunMode = RouteRunMode.AUTOMATIC
    ) {
        if (waypoints.isEmpty()) return
        val path = waypoints.map { GeoPoint(it.latitude, it.longitude) }
        precomputePath(path, waypoints, travelMode.defaultSpeedMps, cornerDecel)

        state = RouteSimulationState(
            isActive = true,
            isPaused = false,
            points = waypoints,
            plannedPath = path,
            traveledMeters = 0.0,
            speedMetersPerSecond = travelMode.defaultSpeedMps,
            travelMode = travelMode,
            loopMode = loopMode,
            runMode = runMode,
            cornerDecelerationMode = cornerDecel
        )
        lastUpdateNanos = SystemClock.elapsedRealtimeNanos()
        currentPhase = RouteMotionPhase.ACCELERATING
    }

    fun pause() {
        state = state.copy(isPaused = true)
        currentPhase = RouteMotionPhase.PAUSED
    }

    fun resume() {
        state = state.copy(isPaused = false)
        lastUpdateNanos = SystemClock.elapsedRealtimeNanos()
        currentPhase = RouteMotionPhase.CRUISING
    }

    fun stop() {
        state = state.copy(isActive = false, isPaused = false)
        currentPhase = RouteMotionPhase.IDLE
        stopUntilNanos = 0L
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

        if (state.runMode == RouteRunMode.MANUAL) {
            return stepManual(dt)
        } else {
            return stepAutomatic(now, dt)
        }
    }

    private fun stepManual(dt: Double): RouteStepResult {
        val currentPoint = state.plannedPath.firstOrNull() ?: GeoPoint(0.0, 0.0)
        val maxSpeed = state.travelMode.defaultSpeedMps
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

        state = state.copy(
            plannedPath = listOf(newPoint),
            traveledMeters = state.traveledMeters + distanceDelta,
            speedMetersPerSecond = currentSpeed
        )

        return RouteStepResult(
            latitude = newPoint.latitude,
            longitude = newPoint.longitude,
            speedMps = currentSpeed.toFloat(),
            bearingDegrees = state.manualBearingDegrees
        )
    }

    private fun stepAutomatic(now: Long, dt: Double): RouteStepResult? {
        if (totalPathDistanceMeters <= 0.0 || state.plannedPath.size < 2) return null

        // 1. Check if currently dwelling at a stop/waypoint
        if (now < stopUntilNanos) {
            currentPhase = RouteMotionPhase.DECELERATING
            val cur = interpolatePosition(state.traveledMeters)
            return RouteStepResult(cur.first.latitude, cur.first.longitude, 0f, cur.second.toFloat())
        }

        var speed = state.travelMode.defaultSpeedMps
        val currentDist = state.traveledMeters

        // 2. Check upcoming waypoint stop schedules
        for (schedule in stopSchedules) {
            if (!schedule.executed) {
                val distToStop = schedule.distanceMeters - currentDist
                if (distToStop in 0.0..15.0) {
                    // Start smooth braking
                    val decelRatio = (distToStop / 15.0).coerceIn(0.1, 1.0)
                    speed *= decelRatio
                }
                if (distToStop in -1.5..1.5) {
                    // Dwell at waypoint
                    schedule.executed = true
                    stopUntilNanos = now + (schedule.durationMillis * 1_000_000L)
                    val cur = interpolatePosition(schedule.distanceMeters)
                    return RouteStepResult(cur.first.latitude, cur.first.longitude, 0f, cur.second.toFloat())
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

        var newDist = state.traveledMeters + speed * dt * state.direction

        // 4. Loop handling
        if (newDist >= totalPathDistanceMeters) {
            when (state.loopMode) {
                RouteLoopMode.CYCLE -> {
                    newDist %= totalPathDistanceMeters
                    // Reset stop schedules for next cycle
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

        state = state.copy(traveledMeters = newDist, speedMetersPerSecond = speed)
        val (point, bearing) = interpolatePosition(newDist)
        return RouteStepResult(point.latitude, point.longitude, speed.toFloat(), bearing.toFloat())
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
            if (wp.stopDurationSeconds > 0) {
                stopSchedules.add(
                    RouteStopSchedule(
                        waypointIndex = i,
                        distanceMeters = cumulativeDistances[i],
                        durationMillis = wp.stopDurationSeconds * 1000L
                    )
                )
            }
        }

        val cornerList = mutableListOf<PathCorner>()
        val maxLatAcc = if (cornerMode == RouteCornerDecelerationMode.DEEP) 1.5 else 2.5 // m/s^2

        for (i in 1 until count - 1) {
            val b1 = bearingDegrees(path[i - 1], path[i])
            val b2 = bearingDegrees(path[i], path[i + 1])
            var turnAngle = abs(b2 - b1)
            if (turnAngle > 180.0) turnAngle = 360.0 - turnAngle

            if (turnAngle > 20.0) {
                // Equivalent corner radius R ~ d / tan(theta/2)
                val leg = min(cumulativeDistances[i] - cumulativeDistances[i - 1], cumulativeDistances[i + 1] - cumulativeDistances[i])
                val radius = max(5.0, leg / max(0.2, tan(Math.toRadians(turnAngle / 2.0))))
                val targetSpeed = min(cruiseSpeed, sqrt(maxLatAcc * radius))
                val transitionDist = max(10.0, (cruiseSpeed * cruiseSpeed - targetSpeed * targetSpeed) / (2.0 * 2.0))

                cornerList.add(
                    PathCorner(
                        pointIndex = i,
                        turnAngleDegrees = turnAngle,
                        cornerRadiusMeters = radius,
                        targetSpeedMetersPerSecond = targetSpeed,
                        transitionDistanceMeters = transitionDist,
                        distanceAlongPathMeters = cumulativeDistances[i]
                    )
                )
            }
        }
        corners = cornerList
    }

    private fun interpolatePosition(distanceMeters: Double): Pair<GeoPoint, Double> {
        val count = cumulativeDistances.size
        if (count < 2) return (state.plannedPath.firstOrNull() ?: GeoPoint(0.0, 0.0)) to 0.0

        var seg = 0
        for (i in 0 until count - 1) {
            if (distanceMeters >= cumulativeDistances[i] && distanceMeters <= cumulativeDistances[i + 1]) {
                seg = i
                break
            }
        }

        val d1 = cumulativeDistances[seg]
        val d2 = cumulativeDistances[seg + 1]
        val p1 = state.plannedPath[seg]
        val p2 = state.plannedPath[seg + 1]

        val fraction = if (d2 > d1) ((distanceMeters - d1) / (d2 - d1)).coerceIn(0.0, 1.0) else 0.0
        val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
        val lon = p1.longitude + (p2.longitude - p1.longitude) * fraction
        val bearing = bearingDegrees(p1, p2)
        return GeoPoint(lat, lon) to bearing
    }

    private fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
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

data class RouteStepResult(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float,
    val bearingDegrees: Float
)
