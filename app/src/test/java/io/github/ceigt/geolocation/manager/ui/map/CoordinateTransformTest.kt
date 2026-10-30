package io.github.ceigt.geolocation.manager.ui.map

import io.github.ceigt.geolocation.data.OsmTileServer

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformTest {
    @Test fun `international tiles preserve WGS84 and domestic tiles round trip Chinese cities`() {
        for (point in listOf(GeoPoint(39.908823, 116.397470), GeoPoint(31.2304, 121.4737),
            GeoPoint(23.1291, 113.2644))) {
            assertEquals(point, OsmTileServer.OPENSTREETMAP.fromWgs84(point))
            assertEquals(point, OsmTileServer.OPENSTREETMAP.toWgs84(point))
            val display = OsmTileServer.AUTONAVI.fromWgs84(point)
            assertEquals(CoordinateTransform.wgs84ToGcj02(point.latitude, point.longitude), display)
            val selected = OsmTileServer.AUTONAVI.toWgs84(display)
            assertEquals(point.latitude, selected.latitude, 0.00002)
            assertEquals(point.longitude, selected.longitude, 0.00002)
        }
    }

    @Test fun `domestic tiles do not offset points outside China`() {
        val point = GeoPoint(51.5074, -0.1278)
        assertEquals(point, OsmTileServer.AUTONAVI.fromWgs84(point))
        assertEquals(point, OsmTileServer.AUTONAVI.toWgs84(point))
    }

    @Test
    fun `wgs84 converts to gcj02 and bd09 near Beijing`() {
        val wgs = GeoPoint(latitude = 39.908823, longitude = 116.397470)
        val gcj = CoordinateTransform.wgs84ToGcj02(wgs.latitude, wgs.longitude)
        val bd = CoordinateTransform.wgs84ToBd09(wgs)

        assertEquals(39.9102, gcj.latitude, 0.001)
        assertEquals(116.4037, gcj.longitude, 0.001)
        assertEquals(39.9166, bd.latitude, 0.001)
        assertEquals(116.4101, bd.longitude, 0.001)
    }

    @Test
    fun `gcj02 conversion leaves locations outside China unchanged`() {
        val london = GeoPoint(latitude = 51.5074, longitude = -0.1278)
        val result = CoordinateTransform.wgs84ToGcj02(london.latitude, london.longitude)

        assertEquals(london.latitude, result.latitude, 0.0)
        assertEquals(london.longitude, result.longitude, 0.0)
    }

    @Test
    fun `amap gcj02 display coordinate converts back to stored wgs84`() {
        val wgs = GeoPoint(latitude = 31.2304, longitude = 121.4737)
        val gcj = CoordinateTransform.wgs84ToGcj02(wgs.latitude, wgs.longitude)
        val restored = CoordinateTransform.gcj02ToWgs84(gcj)

        assertEquals(wgs.latitude, restored.latitude, 0.00002)
        assertEquals(wgs.longitude, restored.longitude, 0.00002)
    }
}
