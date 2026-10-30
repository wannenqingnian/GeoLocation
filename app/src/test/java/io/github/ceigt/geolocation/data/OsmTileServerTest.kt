package io.github.ceigt.geolocation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OsmTileServerTest {
    @Test fun `missing or invalid settings keep the current OSM server`() {
        for (stored in listOf(null, "unknown", DEFAULT_OSM_TILE_SERVER)) {
            assertEquals(OsmTileServer.OPENSTREETMAP, OsmTileServer.fromStored(stored))
        }
    }

    @Test fun `both server selections survive storage`() {
        OsmTileServer.entries.forEach { server ->
            assertEquals(server, OsmTileServer.fromStored(server.storedValue))
        }
    }
}
