package io.github.ceigt.geolocation.manager.ui.settings

import io.github.ceigt.geolocation.data.model.mode.LocationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationModeTest {
    private fun resolveLocationMode(mockProviderEnabled: Boolean, systemHooksEnabled: Boolean): LocationMode = when {
        mockProviderEnabled -> LocationMode.MOCK_PROVIDER
        systemHooksEnabled -> LocationMode.SYSTEM_HOOK
        else -> LocationMode.APPLICATION_HOOK
    }

    @Test
    fun `no compatibility provider selects application hook`() {
        assertEquals(
            LocationMode.APPLICATION_HOOK,
            resolveLocationMode(mockProviderEnabled = false, systemHooksEnabled = false)
        )
    }

    @Test
    fun `system hook is selected when enabled`() {
        assertEquals(
            LocationMode.SYSTEM_HOOK,
            resolveLocationMode(mockProviderEnabled = false, systemHooksEnabled = true)
        )
    }

    @Test
    fun `mock provider takes precedence`() {
        assertEquals(
            LocationMode.MOCK_PROVIDER,
            resolveLocationMode(mockProviderEnabled = true, systemHooksEnabled = true)
        )
    }
}
