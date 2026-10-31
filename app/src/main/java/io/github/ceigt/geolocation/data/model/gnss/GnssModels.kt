package io.github.ceigt.geolocation.data.model.gnss

import android.location.GnssStatus

enum class SatelliteConstellation(val constellationType: Int) {
    GPS(GnssStatus.CONSTELLATION_GPS),
    BEIDOU(GnssStatus.CONSTELLATION_BEIDOU),
    GALILEO(GnssStatus.CONSTELLATION_GALILEO),
    GLONASS(GnssStatus.CONSTELLATION_GLONASS);

    companion object {
        fun fromType(type: Int): SatelliteConstellation =
            entries.firstOrNull { it.constellationType == type } ?: GPS
    }
}

enum class GnssConstellationPreset(val storedValue: String) {
    AUTO_MIXED("auto_mixed"),
    BEIDOU_ONLY("beidou_only"),
    GPS_ONLY("gps_only");

    companion object {
        fun fromStored(value: String?): GnssConstellationPreset =
            entries.firstOrNull { it.storedValue == value } ?: AUTO_MIXED
    }
}

enum class GnssSignalStrengthPreset(val storedValue: String, val minDbHz: Float, val maxDbHz: Float) {
    WEAK("weak", 18f, 26f),
    NORMAL("normal", 28f, 38f),
    GOOD("good", 38f, 48f),
    CUSTOM("custom", 25f, 42f);

    companion object {
        fun fromStored(value: String?): GnssSignalStrengthPreset =
            entries.firstOrNull { it.storedValue == value } ?: NORMAL
    }
}

data class SyntheticGnssSatellite(
    val svid: Int,
    val constellationType: Int,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Float = 1575420000.0f, // L1 / E1 / B1 default
    val basebandCn0DbHz: Float = cn0DbHz - 3.0f
)

enum class GnssJitterAlgorithm(val storedValue: String) {
    FILTERED_GAUSSIAN("filtered_gaussian"),
    FIRST_ORDER_MARKOV("first_order_markov"),
    SECOND_ORDER_MARKOV("second_order_markov");

    companion object {
        fun fromStored(value: String?): GnssJitterAlgorithm =
            entries.firstOrNull { it.storedValue == value } ?: FIRST_ORDER_MARKOV
    }
}

data class GnssJitterCustomSettings(
    val enabled: Boolean = false,
    val algorithm: GnssJitterAlgorithm = GnssJitterAlgorithm.FIRST_ORDER_MARKOV,
    val radiusMeters: Double = 1.5,
    val updateRateHz: Double = 1.0,
    val smoothingRatio: Double = 0.85,
    val driftSpeedMetersPerSecond: Double = 0.3,
    val continuityRatio: Double = 0.9,
    val directionInertiaRatio: Double = 0.7
)

data class GnssAccuracyCustomSettings(
    val horizontalMeters: Float = 3.0f,
    val verticalMultiplier: Float = 1.5f,
    val variationEnabled: Boolean = true,
    val variationRatio: Float = 0.2f,
    val variationRateHz: Float = 0.5f
)
