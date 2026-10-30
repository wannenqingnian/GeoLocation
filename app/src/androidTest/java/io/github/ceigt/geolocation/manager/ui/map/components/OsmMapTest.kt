package io.github.ceigt.geolocation.manager.ui.map.components

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.ceigt.geolocation.data.MapProvider
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import io.github.ceigt.geolocation.manager.ui.map.MapViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class OsmMapTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectedWgs84PointIsDisplayedAndProviderSwitchRecreatesNativeMap() {
        val provider = mutableStateOf(MapProvider.OSMDROID)
        val model = MapViewModel(rule.activity.application)
        rule.setContent { MapViewContainer(model, provider.value, "", "", "", "") }
        rule.waitForIdle()
        lateinit var original: MapView
        rule.runOnIdle {
            original = findMap(rule.activity.window.decorView)!!
            model.updateClickedLocation(GeoPoint(39.908823, 116.397470))
        }
        rule.waitUntil(10_000) {
            model.uiState.value.lastClickedLocation == GeoPoint(39.908823, 116.397470)
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue(original.overlays.filterIsInstance<Marker>().any {
                it.position.latitude == 39.908823 && it.position.longitude == 116.397470
            })
            assertEquals(39.908823, original.mapCenter.latitude, 0.0001)
            assertEquals(116.397470, original.mapCenter.longitude, 0.0001)
            provider.value = MapProvider.GOOGLE
        }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(findMap(rule.activity.window.decorView)); provider.value = MapProvider.OSMDROID }
        rule.waitForIdle()
        rule.runOnIdle { assertNotSame(original, findMap(rule.activity.window.decorView)) }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> findMap(view.getChildAt(index))?.let { return it } }
        }
        return null
    }
}
