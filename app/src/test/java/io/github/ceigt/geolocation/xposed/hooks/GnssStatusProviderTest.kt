package io.github.ceigt.geolocation.xposed.hooks

import io.github.ceigt.geolocation.data.model.gnss.GnssConstellationPreset
import io.github.ceigt.geolocation.data.model.gnss.GnssSignalStrengthPreset
import io.github.ceigt.geolocation.data.model.gnss.SatelliteConstellation
import org.junit.Assert.*
import org.junit.Test

class GnssStatusProviderTest {

    private val provider = GnssStatusProvider()

    @Test
    fun testGenerateSatellitesCounts() {
        val visible = 28
        val used = 20
        val sats = provider.generateSatellites(
            visibleCount = visible,
            usedCount = used,
            preset = GnssConstellationPreset.AUTO_MIXED,
            signalPreset = GnssSignalStrengthPreset.NORMAL
        )

        assertEquals("Visible count should match requested", visible, sats.size)
        val actualUsed = sats.count { it.usedInFix }
        assertEquals("Used in fix count should match requested", used, actualUsed)
    }

    @Test
    fun testConstellationPresets() {
        val bdsSats = provider.generateSatellites(
            visibleCount = 15,
            usedCount = 10,
            preset = GnssConstellationPreset.BEIDOU_ONLY
        )
        assertTrue("All should be Beidou", bdsSats.all { it.constellationType == SatelliteConstellation.BEIDOU.constellationType })

        val gpsSats = provider.generateSatellites(
            visibleCount = 15,
            usedCount = 10,
            preset = GnssConstellationPreset.GPS_ONLY
        )
        assertTrue("All should be GPS", gpsSats.all { it.constellationType == SatelliteConstellation.GPS.constellationType })
    }

    @Test
    fun testSignalStrengthRanges() {
        val weakSats = provider.generateSatellites(
            visibleCount = 12,
            signalPreset = GnssSignalStrengthPreset.WEAK
        )
        // All should be within reasonable weak range
        for (sat in weakSats) {
            assertTrue("Weak signal CN0 >= 10: ${sat.cn0DbHz}", sat.cn0DbHz >= 10f)
            assertTrue("Weak signal CN0 <= 35: ${sat.cn0DbHz}", sat.cn0DbHz <= 35f)
        }
    }
}
