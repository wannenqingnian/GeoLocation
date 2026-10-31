package io.github.ceigt.geolocation.manager.route

import io.github.ceigt.geolocation.data.model.route.RouteWaypoint
import io.github.ceigt.geolocation.data.model.route.TrackEditorConfig
import kotlin.math.*

object TrackGenerator {

    /**
     * Generates a realistic standard 400m stadium running track waypoint sequence.
     * Straight lines: 2 * 84.39m = 168.78m
     * Semi-circles: 2 * PI * 36.8m = 231.22m
     * Total = 400.00m
     */
    fun generateStadiumTrack(config: TrackEditorConfig, numPointsPerTurn: Int = 18): List<RouteWaypoint> {
        val rMeters = 36.8 * config.scale
        val straightMeters = 84.39 * config.scale

        val waypoints = mutableListOf<RouteWaypoint>()
        val rotRad = Math.toRadians(config.rotationDegrees.toDouble())

        fun addPointMeters(x: Double, y: Double) {
            // Rotate (x, y)
            val rx = x * cos(rotRad) - y * sin(rotRad)
            val ry = x * sin(rotRad) + y * cos(rotRad)

            // Convert meter offsets to lat/lon around center
            val dLat = ry / 111320.0
            val cosLat = cos(Math.toRadians(config.centerLat)).let { if (abs(it) < 1e-6) 1e-6 else it }
            val dLon = rx / (111320.0 * cosLat)

            val lat = (config.centerLat + dLat).coerceIn(-90.0, 90.0)
            val lon = ((config.centerLon + dLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            waypoints.add(RouteWaypoint(latitude = lat, longitude = lon))
        }

        // 1. Right straight (from -straight/2 to +straight/2 at x = +r)
        val straightSteps = 8
        for (i in 0..straightSteps) {
            val y = -straightMeters / 2.0 + (straightMeters * i / straightSteps)
            addPointMeters(rMeters, y)
        }

        // 2. Top semi-circle (from angle 0 to 180 deg around (0, straight/2))
        for (i in 1..numPointsPerTurn) {
            val angle = Math.PI * i / numPointsPerTurn
            val x = rMeters * cos(angle)
            val y = straightMeters / 2.0 + rMeters * sin(angle)
            addPointMeters(x, y)
        }

        // 3. Left straight (from +straight/2 to -straight/2 at x = -r)
        for (i in 0..straightSteps) {
            val y = straightMeters / 2.0 - (straightMeters * i / straightSteps)
            addPointMeters(-rMeters, y)
        }

        // 4. Bottom semi-circle (from angle 180 to 360 deg around (0, -straight/2))
        for (i in 1..numPointsPerTurn) {
            val angle = Math.PI + Math.PI * i / numPointsPerTurn
            val x = rMeters * cos(angle)
            val y = -straightMeters / 2.0 + rMeters * sin(angle)
            addPointMeters(x, y)
        }

        if (!config.clockwise) {
            waypoints.reverse()
        }

        return waypoints
    }
}
