package io.github.ceigt.geolocation.data.model.route

import io.github.ceigt.geolocation.manager.ui.map.GeoPoint

enum class RouteTravelMode(val defaultSpeedMps: Double) {
    WALKING(1.4),   // ~ 5 km/h
    CYCLING(4.2),   // ~ 15 km/h
    DRIVING(11.1);  // ~ 40 km/h

    companion object {
        fun fromName(name: String?): RouteTravelMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: WALKING
    }
}

enum class RouteRunMode {
    MANUAL,      // Joystick manual direction
    AUTOMATIC;   // Auto route waypoint cruising

    companion object {
        fun fromName(name: String?): RouteRunMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTOMATIC
    }
}

enum class RouteLoopMode {
    BOUNCE,  // Origin -> Dest -> Origin (ping-pong)
    CYCLE;   // Closed circle cycle

    companion object {
        fun fromName(name: String?): RouteLoopMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CYCLE
    }
}

enum class RouteMotionPhase {
    IDLE,
    ACCELERATING,
    CRUISING,
    DECELERATING,
    PAUSED
}

enum class RouteCornerDecelerationMode {
    COMFORT, // Moderate deceleration at sharp corners
    DEEP;    // Strong braking at corners

    companion object {
        fun fromName(name: String?): RouteCornerDecelerationMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: COMFORT
    }
}

data class RouteWaypoint(
    val name: String = "",
    val latitude: Double,
    val longitude: Double,
    val stopDurationSeconds: Int = 0
)

data class RouteSimulationState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val points: List<RouteWaypoint> = emptyList(),
    val plannedPath: List<GeoPoint> = emptyList(),
    val traveledMeters: Double = 0.0,
    val speedMetersPerSecond: Double = 1.4,
    val direction: Int = 1, // 1 for forward, -1 for reverse
    val closedLoop: Boolean = false,
    val loopMode: RouteLoopMode = RouteLoopMode.CYCLE,
    val travelMode: RouteTravelMode = RouteTravelMode.WALKING,
    val runMode: RouteRunMode = RouteRunMode.AUTOMATIC,
    val smoothStartStopEnabled: Boolean = true,
    val cornerDecelerationMode: RouteCornerDecelerationMode = RouteCornerDecelerationMode.COMFORT,
    val manualBearingDegrees: Float = 0f,
    val manualIntensity: Float = 0f
)

data class RouteFavorite(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val waypoints: List<RouteWaypoint>,
    val loopMode: RouteLoopMode = RouteLoopMode.CYCLE,
    val travelMode: RouteTravelMode = RouteTravelMode.WALKING,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
