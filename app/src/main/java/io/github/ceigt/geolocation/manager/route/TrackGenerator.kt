package io.github.ceigt.geolocation.manager.route

import io.github.ceigt.geolocation.data.model.route.RouteWaypoint
import io.github.ceigt.geolocation.data.model.route.TrackEditorConfig
import kotlin.math.*

object TrackGenerator {

    /**
     * Generates a realistic stadium running track waypoint sequence.
     * Supports arbitrary custom circumference (e.g. 200m, 300m, 400m, 800m).
     * If [config.startAtSelectedPoint] is true, the track originates exactly from the selected coordinate!
     */
    fun generateStadiumTrack(
        config: TrackEditorConfig,
        numPointsPerTurn: Int = 18
    ): List<RouteWaypoint> {
        val totalCircumference = (config.circumferenceMeters * config.scale).coerceIn(50.0, 5000.0)
        // Standard IAAF ratio: straightaway is ~21.1% of perimeter, two semicircles are ~57.8%
        val straightMeters = totalCircumference * 0.210975
        val rMeters = (totalCircumference - 2.0 * straightMeters) / (2.0 * PI)

        val waypoints = mutableListOf<RouteWaypoint>()
        val rotRad = Math.toRadians(config.rotationDegrees.toDouble())

        fun addPointMeters(x: Double, y: Double, name: String = "") {
            // Rotate (x, y) by rotationDegrees
            val rx = x * cos(rotRad) - y * sin(rotRad)
            val ry = x * sin(rotRad) + y * cos(rotRad)

            // Convert meter offsets to lat/lon around selected point
            val dLat = ry / 111320.0
            val cosLat = cos(Math.toRadians(config.centerLat)).let { if (abs(it) < 1e-6) 1e-6 else it }
            val dLon = rx / (111320.0 * cosLat)

            val lat = (config.centerLat + dLat).coerceIn(-90.0, 90.0)
            val lon = ((config.centerLon + dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            waypoints.add(RouteWaypoint(name = name, latitude = lat, longitude = lon))
        }

        val straightSteps = 8

        // If starting at selected point:
        // (0, 0) is the start line.
        // 1. Home straightaway: from (0, 0) straight forward to (0, straightMeters)
        for (i in 0..straightSteps) {
            val y = (straightMeters * i) / straightSteps
            val name = if (i == 0) "起跑点" else "直道"
            addPointMeters(0.0, y, name)
        }

        // 2. First turn (semi-circle): center is (-rMeters, straightMeters)
        // Starts at (0, straightMeters) [angle 0] to (-2*rMeters, straightMeters) [angle PI]
        for (i in 1..numPointsPerTurn) {
            val angle = PI * i / numPointsPerTurn
            val x = -rMeters + rMeters * cos(angle)
            val y = straightMeters + rMeters * sin(angle)
            addPointMeters(x, y, "一弯道")
        }

        // 3. Backstretch straightaway: from (-2*rMeters, straightMeters) down to (-2*rMeters, 0)
        for (i in 1..straightSteps) {
            val y = straightMeters - (straightMeters * i) / straightSteps
            addPointMeters(-2.0 * rMeters, y, "后直道")
        }

        // 4. Second turn (semi-circle): center is (-rMeters, 0)
        // Starts at (-2*rMeters, 0) [angle PI] back to (0, 0) [angle 2*PI]
        for (i in 1 until numPointsPerTurn) {
            val angle = PI + (PI * i / numPointsPerTurn)
            val x = -rMeters + rMeters * cos(angle)
            val y = rMeters * sin(angle)
            addPointMeters(x, y, "二弯道")
        }

        // Connect back to the exact start
        addPointMeters(0.0, 0.0, "终点/闭环点")

        if (!config.clockwise) {
            val start = waypoints.first()
            val reversed = waypoints.reversed().toMutableList()
            if (reversed.isNotEmpty()) {
                reversed.removeAt(0)
                reversed.add(0, start)
            }
            return reversed
        }

        return waypoints
    }
}
