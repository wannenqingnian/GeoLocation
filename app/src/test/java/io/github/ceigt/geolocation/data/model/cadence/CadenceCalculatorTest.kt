package io.github.ceigt.geolocation.data.model.cadence

import org.junit.Assert.*
import org.junit.Test

class CadenceCalculatorTest {

    @Test
    fun testModeOffYieldsZero() {
        assertEquals(0, CadenceCalculator.computeSpm(1.5, TargetCadenceMode.OFF, 180))
        assertEquals(0.0, CadenceCalculator.computeDeltaSteps(1.5, 1.0, TargetCadenceMode.OFF, 180), 0.0001)
    }

    @Test
    fun testStationaryYieldsZero() {
        assertEquals(0, CadenceCalculator.computeSpm(0.0, TargetCadenceMode.AUTO, null))
        assertEquals(0, CadenceCalculator.computeSpm(0.15, TargetCadenceMode.CUSTOM, 180))
        assertEquals(0.0, CadenceCalculator.computeDeltaSteps(0.0, 1.0, TargetCadenceMode.CUSTOM, 180), 0.0001)
    }

    @Test
    fun testCustomSpmExactAgreement() {
        val targetSpm = 180
        val spm = CadenceCalculator.computeSpm(1.5, TargetCadenceMode.CUSTOM, targetSpm)
        assertEquals(180, spm)

        // 60 seconds at 180 SPM should produce exactly 180.0 steps
        val deltaSteps60s = CadenceCalculator.computeDeltaSteps(1.5, 60.0, TargetCadenceMode.CUSTOM, targetSpm)
        assertEquals(180.0, deltaSteps60s, 0.0001)

        // 1 second at 180 SPM should produce exactly 3.0 steps
        val deltaSteps1s = CadenceCalculator.computeDeltaSteps(1.5, 1.0, TargetCadenceMode.CUSTOM, targetSpm)
        assertEquals(3.0, deltaSteps1s, 0.0001)
    }

    @Test
    fun testAutoSpmProgression() {
        val slowWalk = CadenceCalculator.computeSpm(0.8, TargetCadenceMode.AUTO, null)
        val fastWalk = CadenceCalculator.computeSpm(1.8, TargetCadenceMode.AUTO, null)
        val jog = CadenceCalculator.computeSpm(2.5, TargetCadenceMode.AUTO, null)
        val run = CadenceCalculator.computeSpm(4.5, TargetCadenceMode.AUTO, null)

        assertTrue(slowWalk in 90..150)
        assertTrue(fastWalk in 90..150)
        assertTrue(fastWalk >= slowWalk)

        assertTrue(jog in 150..205)
        assertTrue(run in 150..205)
        assertTrue(run >= jog)
    }

    @Test
    fun testStrideLengthPhysicalCoherence() {
        val stride = CadenceCalculator.computeStrideLengthMeters(1.4, 125)
        assertTrue("Stride length in realistic range: $stride", stride in 0.5..1.2)
    }
}
