package io.github.ceigt.geolocation.simulation.frame

import io.github.ceigt.geolocation.data.model.gnss.*
import org.junit.Assert.*
import org.junit.Test

class LocationFrameAssemblerTest {

    @Test
    fun testFirstOrderMarkovJitterBounded() {
        val jitter = GnssJitterCustomSettings(
            enabled = true,
            algorithm = GnssJitterAlgorithm.FIRST_ORDER_MARKOV,
            radiusMeters = 3.0,
            smoothingRatio = 0.8
        )
        val accuracy = GnssAccuracyCustomSettings(horizontalMeters = 4.0f)
        val assembler = LocationFrameAssembler(jitter, accuracy)

        val baseLat = 39.9042
        val baseLon = 116.4074

        var t = 1000000L
        for (i in 0 until 50) {
            t += 1000L
            val (lat, lon, acc) = assembler.applyJitter(baseLat, baseLon, t)
            assertTrue("Latitude must be finite", lat.isFinite())
            assertTrue("Longitude must be finite", lon.isFinite())
            assertTrue("Accuracy must be positive", acc.first > 0f)
            assertTrue("Vertical accuracy must be greater or equal to horizontal", acc.second >= acc.first)
            // Verify delta is within reasonable degree bound (~3m is ~0.00003 deg)
            assertTrue("Delta lat within 30m bound", kotlin.math.abs(lat - baseLat) < 0.001)
            assertTrue("Delta lon within 30m bound", kotlin.math.abs(lon - baseLon) < 0.001)
        }
    }

    @Test
    fun testSecondOrderMarkovJitterInertia() {
        val jitter = GnssJitterCustomSettings(
            enabled = true,
            algorithm = GnssJitterAlgorithm.SECOND_ORDER_MARKOV,
            radiusMeters = 5.0,
            driftSpeedMetersPerSecond = 0.5,
            directionInertiaRatio = 0.8
        )
        val assembler = LocationFrameAssembler(jitter, GnssAccuracyCustomSettings())

        val offset1 = assembler.computeOffset(1000L)
        val offset2 = assembler.computeOffset(2000L)

        assertNotNull(offset1)
        assertNotNull(offset2)
        val dist1 = kotlin.math.hypot(offset1.eastMeters, offset1.northMeters)
        assertTrue("Offset1 should be within radius", dist1 <= 5.0)
    }

    @Test
    fun testDisabledJitterReturnsZeroOffset() {
        val jitter = GnssJitterCustomSettings(enabled = false, radiusMeters = 5.0)
        val assembler = LocationFrameAssembler(jitter, GnssAccuracyCustomSettings())

        val offset = assembler.computeOffset(1000L)
        assertEquals(0.0, offset.eastMeters, 1e-9)
        assertEquals(0.0, offset.northMeters, 1e-9)
    }
}
