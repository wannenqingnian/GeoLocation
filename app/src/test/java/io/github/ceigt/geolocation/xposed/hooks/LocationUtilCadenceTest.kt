package io.github.ceigt.geolocation.xposed.hooks

import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import org.junit.Assert.*
import org.junit.Test

class LocationUtilCadenceTest {

    @Test
    fun testComputeCadenceZeroWhenDisabledOrStopped() {
        assertEquals(0, LocationUtil.computeCadenceStepsPerMinute(0f, enabled = true, customSpm = null))
        assertEquals(0, LocationUtil.computeCadenceStepsPerMinute(0.1f, enabled = true, customSpm = null))
        assertEquals(0, LocationUtil.computeCadenceStepsPerMinute(2.5f, enabled = false, customSpm = null))
    }

    @Test
    fun testComputeCadenceCustomOverride() {
        assertEquals(180, LocationUtil.computeCadenceStepsPerMinute(1.0f, enabled = true, customSpm = 180))
        assertEquals(165, LocationUtil.computeCadenceStepsPerMinute(3.5f, enabled = true, customSpm = 165))
    }

    @Test
    fun testComputeCadenceWalkingRange() {
        // Walking speeds: 0.5 ~ 2.0 m/s
        val slowWalk = LocationUtil.computeCadenceStepsPerMinute(0.8f, enabled = true, customSpm = null)
        assertTrue("Slow walk SPM between 90 and 150: $slowWalk", slowWalk in 90..150)

        val briskWalk = LocationUtil.computeCadenceStepsPerMinute(1.8f, enabled = true, customSpm = null)
        assertTrue("Brisk walk SPM between 90 and 150: $briskWalk", briskWalk in 90..150)
        assertTrue("Faster walking should have higher cadence", briskWalk >= slowWalk)
    }

    @Test
    fun testComputeCadenceRunningRange() {
        // Running speeds >= 2.2 m/s
        val jog = LocationUtil.computeCadenceStepsPerMinute(2.5f, enabled = true, customSpm = null)
        assertTrue("Jogging SPM between 150 and 200: $jog", jog in 150..200)

        val run = LocationUtil.computeCadenceStepsPerMinute(5.0f, enabled = true, customSpm = null)
        assertTrue("Running SPM between 150 and 200: $run", run in 150..200)
        assertTrue("Faster running should have higher cadence", run >= jog)
    }
}
