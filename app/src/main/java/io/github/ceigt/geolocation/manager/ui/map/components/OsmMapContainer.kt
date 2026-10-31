package io.github.ceigt.geolocation.manager.ui.map.components

import android.location.Geocoder
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.DEFAULT_MAP_ZOOM
import io.github.ceigt.geolocation.data.OsmTileServer
import io.github.ceigt.geolocation.data.WORLD_MAP_ZOOM
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import io.github.ceigt.geolocation.manager.ui.map.MapViewModel
import io.github.ceigt.geolocation.manager.ui.map.PlaceSearchResult
import io.github.ceigt.geolocation.manager.ui.map.fromWgs84
import io.github.ceigt.geolocation.manager.ui.map.toWgs84
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.util.Locale
import org.osmdroid.util.GeoPoint as OsmPoint

@Suppress("DEPRECATION")
@Composable
internal fun OsmMapContainer(model: MapViewModel, tileServer: OsmTileServer, onMapInteraction: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by model.uiState.collectAsStateWithLifecycle()
    val playing by rememberUpdatedState(state.isPlaying)
    val interaction by rememberUpdatedState(onMapInteraction)
    val map = remember(context) {
        MapView(context).apply {
            val source = when (tileServer) {
                OsmTileServer.OPENSTREETMAP -> TileSourceFactory.MAPNIK
                OsmTileServer.AUTONAVI -> autoNaviTileSource()
            }
            setTileSource(source)
            setMultiTouchControls(true)
            minZoomLevel = maxOf(WORLD_MAP_ZOOM, source.minimumZoomLevel.toDouble())
            maxZoomLevel = 22.0
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: OsmPoint): Boolean {
                    interaction()
                    if (!playing) model.updateClickedLocation(tileServer.toWgs84(GeoPoint(point.latitude, point.longitude)))
                    return true
                }

                override fun longPressHelper(point: OsmPoint) = false
            }))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent) = false
                override fun onZoom(event: ZoomEvent): Boolean {
                    model.updateMapZoom(event.zoomLevel)
                    return false
                }
            })
        }
    }
    val selectedMarker = remember(map) {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ -> true }
        }
    }
    val userMarker = remember(map) {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)
            setOnMarkerClickListener { _, _ -> true }
        }
    }
    val unavailable = stringResource(R.string.toast_user_location_not_available)
    val geocoder = remember(context) { Geocoder(context, Locale.getDefault()) }

    fun showDeviceLocation(select: Boolean) {
        val location = getLastKnownDeviceLocation(context)
        if (location == null) {
            if (select) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
            else {
                map.controller.setZoom(WORLD_MAP_ZOOM)
                map.controller.setCenter(OsmPoint(0.0, 0.0))
            }
            return
        }
        model.updateUserLocation(location.point)
        val display = tileServer.fromWgs84(location.point)
        userMarker.position = OsmPoint(display.latitude, display.longitude)
        if (!map.overlays.contains(userMarker)) map.overlays.add(userMarker)
        map.controller.setZoom(DEFAULT_MAP_ZOOM)
        map.controller.setCenter(userMarker.position)
        if (select) model.updateClickedLocation(location.point)
    }

    DisposableEffect(map, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    LaunchedEffect(map) {
        model.setLoadingFinished()
        if (state.lastClickedLocation == null) showDeviceLocation(select = false)
    }
    LaunchedEffect(map, state.lastClickedLocation) {
        val point = state.lastClickedLocation
        if (point == null) map.overlays.remove(selectedMarker)
        else {
            val display = tileServer.fromWgs84(point)
            selectedMarker.position = OsmPoint(display.latitude, display.longitude)
            if (!map.overlays.contains(selectedMarker)) map.overlays.add(selectedMarker)
            map.controller.setZoom(state.mapZoom ?: DEFAULT_MAP_ZOOM)
            map.controller.setCenter(selectedMarker.position)
        }
        map.invalidate()
    }
    LaunchedEffect(map) {
        model.centerMapEvent.collect { showDeviceLocation(select = true) }
    }
    LaunchedEffect(map) {
        model.goToPointEvent.collect { point ->
            map.controller.setZoom(DEFAULT_MAP_ZOOM)
            val display = tileServer.fromWgs84(point)
            map.controller.setCenter(OsmPoint(display.latitude, display.longitude))
            model.updateClickedLocation(point)
        }
    }
    LaunchedEffect(map) {
        model.placeSearchEvent.collectLatest { query ->
            try {
                val results = withContext(Dispatchers.IO) {
                    check(Geocoder.isPresent())
                    geocoder.getFromLocationName(query, 10).orEmpty()
                        .filter { it.hasLatitude() && it.hasLongitude() }
                        .map { address ->
                            PlaceSearchResult(
                                address.featureName ?: address.getAddressLine(0).orEmpty(),
                                address.getAddressLine(0).orEmpty(), address.latitude, address.longitude
                            )
                        }
                }
                model.onPlaceSearchCompleted(query, results)
            } catch (_: IOException) {
                model.onPlaceSearchFailed(query)
            } catch (_: IllegalStateException) {
                model.onPlaceSearchFailed(query)
            }
        }
    }
    LaunchedEffect(map) {
        model.reverseGeocodeEvent.collectLatest { point ->
            try {
                val address = withContext(Dispatchers.IO) {
                    check(Geocoder.isPresent())
                    geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                }
                model.onReverseGeocodeCompleted(point, address?.getAddressLine(0), address?.featureName)
            } catch (_: IOException) {
                model.onReverseGeocodeFailed(point)
            } catch (_: IllegalStateException) {
                model.onReverseGeocodeFailed(point)
            }
        }
    }

    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxSize().pointerInteropFilter { event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) interaction()
            false
        }
    )
}

private fun autoNaviTileSource() = object : OnlineTileSourceBase(
    "GeoLocation-AutoNavi-Vector", 3, 19, 256, ".png",
    Array(4) { "https://wprd0${it + 1}.is.autonavi.com/appmaptile?" }
) {
    override fun getTileURLString(index: Long): String =
        "${baseUrl}lang=zh_cn&size=1&scale=1&style=7" +
            "&x=${MapTileIndex.getX(index)}&y=${MapTileIndex.getY(index)}&z=${MapTileIndex.getZoom(index)}"
}
