package io.github.ceigt.geolocation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProviderTest {
    @Test
    fun `new and invalid preferences default to osmdroid`() {
        assertEquals(MapProvider.OSMDROID, MapProvider.fromStored(DEFAULT_MAP_PROVIDER))
        assertEquals(MapProvider.OSMDROID, MapProvider.fromStored(null))
        assertEquals(MapProvider.OSMDROID, MapProvider.fromStored("unknown"))
    }

    @Test
    fun `all provider selections survive storage`() {
        MapProvider.entries.forEach { provider ->
            assertEquals(provider, MapProvider.fromStored(provider.storedValue))
        }
    }
}
