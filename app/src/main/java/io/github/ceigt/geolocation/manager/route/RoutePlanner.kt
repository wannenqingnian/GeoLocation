package io.github.ceigt.geolocation.manager.route

import io.github.ceigt.geolocation.data.model.route.RouteTravelMode
import io.github.ceigt.geolocation.data.model.route.RouteWaypoint
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import kotlin.math.*

object RoutePlanner {

    /**
     * Compute great-circle distance between two coordinates in meters (Haversine)
     */
    fun computeDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
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

    /**
     * Compute total length of a sequence of waypoints in meters
     */
    fun computeTotalDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += computeDistanceMeters(points[i - 1], points[i])
        }
        return total
    }

    /**
     * Estimate total steps based on distance and travel mode
     */
    fun estimateSteps(distanceMeters: Double, speedMps: Double): Long {
        val spm = io.github.ceigt.geolocation.data.model.cadence.CadenceCalculator.computeSpm(speedMps)
        val strideLength = io.github.ceigt.geolocation.data.model.cadence.CadenceCalculator.computeStrideLengthMeters(speedMps, spm)
        return if (strideLength > 0.1) (distanceMeters / strideLength).toLong() else 0L
    }

    /**
     * Automatically plan/generate a route between origin and destination with realistic road-like turns.
     * Uses Bezier/Hermite intermediate curvature to avoid mechanical straight lines.
     */
    fun autoGenerateRoute(
        start: GeoPoint,
        end: GeoPoint,
        intermediateTurns: Int = 3
    ): List<RouteWaypoint> {
        val totalDist = computeDistanceMeters(start, end)
        if (totalDist < 5.0) {
            return listOf(
                RouteWaypoint("起点", start.latitude, start.longitude),
                RouteWaypoint("终点", end.latitude, end.longitude)
            )
        }

        val result = mutableListOf<RouteWaypoint>()
        result.add(RouteWaypoint("起点", start.latitude, start.longitude))

        val segments = (intermediateTurns + 1).coerceIn(2, 8)
        val dLat = end.latitude - start.latitude
        val dLon = end.longitude - start.longitude

        // Normal vector perpendicular to start->end line for realistic road detours
        val len = sqrt(dLat * dLat + dLon * dLon)
        val normLat = -dLon / len
        val normLon = dLat / len

        // Max perpendicular displacement ~ 12% of total length
        val maxOffsetMeters = min(totalDist * 0.12, 350.0)
        val degOffsetLat = maxOffsetMeters / 111320.0
        val degOffsetLon = maxOffsetMeters / (111320.0 * cos(Math.toRadians(start.latitude)).coerceAtLeast(0.1))

        for (i in 1 until segments) {
            val t = i.toDouble() / segments.toDouble()
            // Sinusoidal offset creating natural S-curve or road bend
            val sideWobble = sin(t * PI * 2.0) * 0.75
            val lat = start.latitude + t * dLat + sideWobble * degOffsetLat
            val lon = start.longitude + t * dLon + sideWobble * degOffsetLon
            result.add(RouteWaypoint("途经点 $i", lat, lon))
        }

        result.add(RouteWaypoint("终点", end.latitude, end.longitude))
        return result
    }

    /**
     * Simplify and downsample hand-drawn touch points to remove jitter and dense duplicates.
     * Keeps a point only if it is at least [minDistanceMeters] from the previous one.
     */
    fun simplifyHandDrawnTrack(rawPoints: List<GeoPoint>, minDistanceMeters: Double = 8.0): List<RouteWaypoint> {
        if (rawPoints.isEmpty()) return emptyList()
        val waypoints = mutableListOf<RouteWaypoint>()
        waypoints.add(RouteWaypoint("起点", rawPoints.first().latitude, rawPoints.first().longitude))

        var lastKept = rawPoints.first()
        for (i in 1 until rawPoints.size - 1) {
            val pt = rawPoints[i]
            val dist = computeDistanceMeters(lastKept, pt)
            if (dist >= minDistanceMeters) {
                waypoints.add(RouteWaypoint("航点 ${waypoints.size}", pt.latitude, pt.longitude))
                lastKept = pt
            }
        }

        if (rawPoints.size > 1) {
            val last = rawPoints.last()
            if (computeDistanceMeters(lastKept, last) >= 2.0) {
                waypoints.add(RouteWaypoint("终点", last.latitude, last.longitude))
            }
        }
        return waypoints
    }
}
