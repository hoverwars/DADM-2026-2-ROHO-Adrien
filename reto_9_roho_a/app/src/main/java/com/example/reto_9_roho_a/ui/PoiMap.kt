package com.example.reto_9_roho_a.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.reto_9_roho_a.data.Poi
import com.example.reto_9_roho_a.data.toDistanceLabel
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.infowindow.InfoWindow
import kotlin.math.cos

/**
 * Tuiles Esri World Street Map (sans clé API). tile.openstreetmap.org rejette (403) les connexions
 * de certains réseaux mobiles et CARTO exige désormais une clé : on n'utilise donc aucun des deux.
 * Esri sert les tuiles dans l'ordre z/y/x, d'où le [getTileURLString] redéfini.
 */
private val TILE_SOURCE = object : OnlineTileSourceBase(
    "EsriWorldStreetMap", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
    "Tiles © Esri — Source: Esri, HERE, Garmin, OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "$baseUrl${MapTileIndex.getZoom(pMapTileIndex)}/" +
            "${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
}

/** Estado mutable que el mapa recuerda entre recomposiciones para animar solo cuando algo cambia. */
private class MapMemory {
    var center: Pair<Double, Double>? = null
    var radiusKm: Int = 0
    var selectedPoiId: Long? = null
}

/**
 * Mapa OpenStreetMap (osmdroid) dentro de Compose. Dibuja el círculo del radio, la posición del
 * usuario y un marcador por punto de interés cuyo título es el nombre del lugar.
 */
@Composable
fun PoiMap(
    center: Pair<Double, Double>?,
    radiusKm: Int,
    pois: List<Poi>,
    selectedPoiId: Long?,
    onPoiSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memory = remember { MapMemory() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TILE_SOURCE)
            setMultiTouchControls(true)
            controller.setZoom(5.0)
            // Vista inicial neutra (centro de Colombia) hasta que haya una posición.
            controller.setCenter(GeoPoint(4.6, -74.1))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            map.overlays.add(CopyrightOverlay(map.context))
            if (center != null) {
                val centerPoint = GeoPoint(center.first, center.second)

                val circle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(centerPoint, radiusKm * 1000.0)
                    fillPaint.color = Color.argb(30, 63, 81, 181)
                    outlinePaint.color = Color.argb(180, 63, 81, 181)
                    outlinePaint.strokeWidth = 3f
                }
                map.overlays.add(circle)

                map.overlays.add(Marker(map).apply {
                    position = centerPoint
                    title = "Tu posición"
                    icon = dot(map, Color.rgb(25, 118, 210), 18)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })

                pois.forEach { poi ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(poi.lat, poi.lon)
                        title = poi.name
                        subDescription = "${poi.category.label} · ${poi.distanceMeters.toDistanceLabel()}"
                        icon = dot(map, poi.category.colorArgb, if (poi.id == selectedPoiId) 26 else 18)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { marker, mapView ->
                            InfoWindow.closeAllInfoWindowsOn(mapView)
                            marker.showInfoWindow()
                            onPoiSelected(poi.id)
                            true
                        }
                        if (poi.id == selectedPoiId) showInfoWindow()
                    })
                }

                val selected = pois.firstOrNull { it.id == selectedPoiId }
                if (selected != null && selectedPoiId != memory.selectedPoiId) {
                    map.controller.animateTo(GeoPoint(selected.lat, selected.lon))
                } else if (center != memory.center || radiusKm != memory.radiusKm) {
                    map.post { map.zoomToBoundingBox(boundsOf(center, radiusKm), true, 48) }
                }
            }
            memory.center = center
            memory.radiusKm = radiusKm
            memory.selectedPoiId = selectedPoiId
            map.invalidate()
        }
    )
}

/** Caja que contiene el círculo de búsqueda (1° de latitud ≈ 111,32 km). */
private fun boundsOf(center: Pair<Double, Double>, radiusKm: Int): BoundingBox {
    val dLat = radiusKm / 111.32
    val dLon = radiusKm / (111.32 * cos(Math.toRadians(center.first)).coerceAtLeast(0.01))
    return BoundingBox(center.first + dLat, center.second + dLon, center.first - dLat, center.second - dLon)
}

/** Marcador circular de color con borde blanco. */
private fun dot(view: View, color: Int, sizeDp: Int): GradientDrawable {
    val px = (sizeDp * view.resources.displayMetrics.density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((2 * view.resources.displayMetrics.density).toInt(), Color.WHITE)
        setSize(px, px)
    }
}
