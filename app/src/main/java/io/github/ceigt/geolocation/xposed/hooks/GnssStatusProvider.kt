package io.github.ceigt.geolocation.xposed.hooks

import android.location.GnssStatus
import android.os.Build
import io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset
import io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset
import io.github.ceigt.geolocation.data.model.gnss.SatelliteConstellation
import io.github.ceigt.geolocation.data.model.gnss.SyntheticGnssSatellite
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.Random

object GnssStatusConstructionStrategy {

    /**
     * Constructs android.location.GnssStatus on Android 11~16
     */
    fun createGnssStatus(satellites: List<SyntheticGnssSatellite>): GnssStatus? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) standard GnssStatus.Builder
            val status = runCatching {
                val builder = GnssStatus.Builder()
                for (sat in satellites) {
                    builder.addSatellite(
                        sat.constellationType,
                        sat.svid,
                        sat.cn0DbHz,
                        sat.elevationDegrees,
                        sat.azimuthDegrees,
                        true, // hasEphemerisData
                        true, // hasAlmanacData
                        sat.usedInFix,
                        true, // hasCarrierFrequencyHz
                        sat.carrierFrequencyHz,
                        true, // hasBasebandCn0DbHz
                        sat.basebandCn0DbHz
                    )
                }
                builder.build()
            }.getOrNull()

            if (status != null) return status
        }

        // Fallback to hidden reflection
        return runCatching {
            val count = satellites.size
            val svidWithFlags = IntArray(count)
            val cn0s = FloatArray(count)
            val elevations = FloatArray(count)
            val azimuths = FloatArray(count)
            val carrierFrequencies = FloatArray(count)
            val basebandCn0s = FloatArray(count)

            for (i in 0 until count) {
                val sat = satellites[i]
                // Construct svidWithFlags bitfield:
                // [svid (8-bit) | constellation (4-bit) | ephemeris | almanac | usedInFix | carrierFreq ...]
                val constellationShift = (sat.constellationType and 0xF) shl 4
                val usedShift = if (sat.usedInFix) (1 shl 2) else 0
                svidWithFlags[i] = (sat.svid and 0xFF) or constellationShift or usedShift
                cn0s[i] = sat.cn0DbHz
                elevations[i] = sat.elevationDegrees
                azimuths[i] = sat.azimuthDegrees
                carrierFrequencies[i] = sat.carrierFrequencyHz
                basebandCn0s[i] = sat.basebandCn0DbHz
            }

            // Look for GnssStatus constructor or wrap method
            val wrapMethod = runCatching {
                GnssStatus::class.java.getDeclaredMethod(
                    "wrap",
                    Int::class.javaPrimitiveType,
                    IntArray::class.java,
                    FloatArray::class.java,
                    FloatArray::class.java,
                    FloatArray::class.java,
                    FloatArray::class.java,
                    FloatArray::class.java
                ).apply { isAccessible = true }
            }.getOrNull()

            if (wrapMethod != null) {
                wrapMethod.invoke(null, count, svidWithFlags, cn0s, elevations, azimuths, carrierFrequencies, basebandCn0s) as? GnssStatus
            } else {
                null
            }
        }.getOrNull()
    }
}

class GnssStatusProvider {
    private val random = Random()

    fun generateSatellites(
        visibleCount: Int = 24,
        usedCount: Int = 18,
        preset: GnssConstellationPreset = GnssConstellationPreset.AUTO_MIXED,
        signalPreset: GnssSignalStrengthPreset = GnssSignalStrengthPreset.NORMAL
    ): List<SyntheticGnssSatellite> {
        val count = visibleCount.coerceIn(8, 40)
        val used = usedCount.coerceIn(4, count)
        val satellites = ArrayList<SyntheticGnssSatellite>(count)

        val constellations = when (preset) {
            GnssConstellationPreset.GPS_ONLY -> listOf(SatelliteConstellation.GPS)
            GnssConstellationPreset.BEIDOU_ONLY -> listOf(SatelliteConstellation.BEIDOU)
            GnssConstellationPreset.AUTO_MIXED -> listOf(
                SatelliteConstellation.GPS,
                SatelliteConstellation.BEIDOU,
                SatelliteConstellation.GALILEO,
                SatelliteConstellation.GLONASS
            )
        }

        var svidCounter = 1
        for (i in 0 until count) {
            val constType = constellations[i % constellations.size].constellationType
            val isUsed = i < used

            // Generate realistic elevation and azimuth
            val elevation = (15.0f + (i * 70.0f / count) + (random.nextFloat() * 5.0f - 2.5f)).coerceIn(5.0f, 88.0f)
            val azimuth = ((i * 360.0f / count) + (random.nextFloat() * 10.0f - 5.0f)) % 360.0f

            // Signal strength CN0
            val baseCn0 = signalPreset.minDbHz + (signalPreset.maxDbHz - signalPreset.minDbHz) * (elevation / 90.0f)
            val cn0 = (baseCn0 + (random.nextFloat() * 2.0f - 1.0f)).coerceIn(12.0f, 52.0f)

            val svid = when (constType) {
                GnssStatus.CONSTELLATION_GPS -> ((svidCounter++) % 32) + 1
                GnssStatus.CONSTELLATION_BEIDOU -> ((svidCounter++) % 35) + 1
                GnssStatus.CONSTELLATION_GALILEO -> ((svidCounter++) % 36) + 1
                else -> ((svidCounter++) % 24) + 1
            }

            satellites.add(
                SyntheticGnssSatellite(
                    svid = svid,
                    constellationType = constType,
                    cn0DbHz = cn0,
                    elevationDegrees = elevation,
                    azimuthDegrees = azimuth,
                    usedInFix = isUsed
                )
            )
        }

        return satellites
    }
}
