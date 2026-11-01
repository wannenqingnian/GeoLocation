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
                    val wgs = tileServer.toWgs84(GeoPoint(point.latitude, point.longitude))
                    val curState = model.uiState.value
                    if (curState.trackPreviewState.isPreviewing) {
                        model.updateTrackPreview(center = wgs)
                    } else if (curState.isRoutePlanningMode) {
                        if (curState.routeDrawMode == io.github.ceigt.geolocation.manager.ui.map.RouteDrawMode.POINT_TAP) {
                            model.addRouteWaypoint(wgs)
                        }
                    } else if (!playing) {
                        model.updateClickedLocation(wgs)
                    }
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
    val routePolyline = remember(map) {
        org.osmdroid.views.overlay.Polyline(map).apply {
            outlinePaint.color = android.graphics.Color.parseColor("#006C5F")
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }
    val trackPreviewPolyline = remember(map) {
        org.osmdroid.views.overlay.Polyline(map).apply {
            outlinePaint.color = android.graphics.Color.parseColor("#E65100") // High-contrast orange
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }
    val freehandOverlay = remember(map) {
        object : org.osmdroid.views.overlay.Overlay() {
            private val screenPoints = mutableListOf<android.graphics.PointF>()
            private val strokePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#00897B")
                strokeWidth = 10f
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }

            override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
                if (shadow || screenPoints.size < 2) return
                val path = android.graphics.Path()
                path.moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size) {
                    path.lineTo(screenPoints[i].x, screenPoints[i].y)
                }
                canvas.drawPath(path, strokePaint)
            }

            override fun onTouchEvent(event: android.view.MotionEvent, mapView: MapView): Boolean {
                val s = model.uiState.value
                val isFreehandActive = s.isRoutePlanningMode &&
                        s.routeDrawMode == io.github.ceigt.geolocation.manager.ui.map.RouteDrawMode.FREEHAND &&
                        !s.trackPreviewState.isPreviewing
                if (!isFreehandActive) return false

                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        screenPoints.clear()
                        screenPoints.add(android.graphics.PointF(event.x, event.y))
                        mapView.invalidate()
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        screenPoints.add(android.graphics.PointF(event.x, event.y))
                        mapView.invalidate()
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (screenPoints.size >= 2) {
                            val geoPoints = screenPoints.map { pt ->
                                val p = mapView.projection.fromPixels(pt.x.toInt(), pt.y.toInt()) as OsmPoint
                                tileServer.toWgs84(GeoPoint(p.latitude, p.longitude))
                            }
                            model.applyHandDrawnPoints(geoPoints)
                        }
                        screenPoints.clear()
                        mapView.invalidate()
                        return true
                    }
                }
                return false
            }
        }.also {
            map.overlays.add(it)
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
        // Only pan/center the map to the real device location, do NOT move the simulation/clicked location!
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
    LaunchedEffect(map, state.plannedWaypoints, state.routeLoopMode) {
        val wps = state.plannedWaypoints
        if (wps.size < 2) {
            map.overlays.remove(routePolyline)
        } else {
            val osmPoints = wps.map {
                val disp = tileServer.fromWgs84(GeoPoint(it.latitude, it.longitude))
                OsmPoint(disp.latitude, disp.longitude)
            }.toMutableList()
            if (state.routeLoopMode == io.github.ceigt.geolocation.data.model.route.RouteLoopMode.CYCLE && osmPoints.size > 2) {
                osmPoints.add(osmPoints.first())
            }
            routePolyline.setPoints(osmPoints)
            if (!map.overlays.contains(routePolyline)) {
                map.overlays.add(routePolyline)
            }
        }
        map.invalidate()
    }
    LaunchedEffect(map, state.previewWaypoints) {
        val pwps = state.previewWaypoints
        if (pwps.isEmpty()) {
            map.overlays.remove(trackPreviewPolyline)
        } else {
            val osmPoints = pwps.map {
                val disp = tileServer.fromWgs84(GeoPoint(it.latitude, it.longitude))
                OsmPoint(disp.latitude, disp.longitude)
            }
            trackPreviewPolyline.setPoints(osmPoints)
            if (!map.overlays.contains(trackPreviewPolyline)) {
                map.overlays.add(trackPreviewPolyline)
            }
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
