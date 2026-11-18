package com.example.reto_10_roho_a.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.reto_10_roho_a.data.Observacion
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * Teselas Esri World Street Map (sin clave API). tile.openstreetmap.org rechaza algunas redes móviles,
 * así que se usa Esri, que sirve las teselas en orden z/y/x.
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

/** Recuerda qué conjunto de puntos ya se encuadró para no mover la cámara en cada recomposición. */
private class Memoria {
    var encuadrado: List<Observacion>? = null
}

/** Mapa OpenStreetMap (osmdroid) con las observaciones de la especie, como puntos o mapa de calor. */
@Composable
fun MapaObservaciones(
    puntos: List<Observacion>,
    mapaDeCalor: Boolean,
    nombre: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memoria = remember { Memoria() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TILE_SOURCE)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(4.6, -74.1)) // centro de Colombia
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
            if (mapaDeCalor) {
                map.overlays.add(CapaCalor(map, puntos))
            } else {
                // Los marcadores se limitan a 600 para que el mapa siga fluido
                puntos.take(600).forEach { o ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(o.lat, o.lon)
                        title = nombre
                        subDescription = listOfNotNull(
                            o.fecha?.let { "Fecha: $it" },
                            listOfNotNull(o.municipio, o.departamento).joinToString(", ").ifEmpty { null },
                            o.fuente,
                        ).joinToString("\n")
                        icon = punto(map, Color.rgb(198, 40, 40), 14)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { marker, vista ->
                            InfoWindow.closeAllInfoWindowsOn(vista)
                            marker.showInfoWindow()
                            true
                        }
                    })
                }
            }
            map.overlays.add(CopyrightOverlay(map.context))

            if (puntos.isNotEmpty() && memoria.encuadrado !== puntos) {
                memoria.encuadrado = puntos
                val caja = BoundingBox.fromGeoPoints(puntos.map { GeoPoint(it.lat, it.lon) })
                map.post {
                    // Un solo punto (o puntos casi idénticos): zoom fijo en lugar de encuadrar una caja vacía
                    if (caja.latitudeSpan < 0.01 && caja.longitudeSpan < 0.01) {
                        map.controller.setZoom(11.0)
                        map.controller.setCenter(caja.centerWithDateLine)
                    } else {
                        map.zoomToBoundingBox(caja, true, 80)
                    }
                }
            }
            map.invalidate()
        }
    )
}

/**
 * Capa de mapa de calor: acumula una mancha difusa por observación en una imagen pequeña
 * y la colorea según la densidad (azul → verde → amarillo → rojo).
 */
private class CapaCalor(private val mapView: MapView, private val puntos: List<Observacion>) : Overlay() {
    private val geoPuntos = puntos.map { GeoPoint(it.lat, it.lon) }
    private val escala = 4 // la imagen de densidad es 4 veces más pequeña que la pantalla
    private val radioDp = 22f
    private val pintura = Paint(Paint.FILTER_BITMAP_FLAG)
    private val punto = Point()
    private var mascara: Bitmap? = null
    private var imagen: Bitmap? = null
    private var pixeles = IntArray(0)

    private val paleta = IntArray(256) { i ->
        val t = i / 255f
        val alfa = (kotlin.math.min(1f, t * 2.2f) * 190).toInt()
        val color = when {
            t < 0.25f -> mezcla(Color.rgb(30, 60, 220), Color.rgb(0, 200, 255), t / 0.25f)
            t < 0.5f -> mezcla(Color.rgb(0, 200, 255), Color.rgb(60, 220, 60), (t - 0.25f) / 0.25f)
            t < 0.75f -> mezcla(Color.rgb(60, 220, 60), Color.rgb(255, 230, 0), (t - 0.5f) / 0.25f)
            else -> mezcla(Color.rgb(255, 230, 0), Color.rgb(230, 20, 20), (t - 0.75f) / 0.25f)
        }
        Color.argb(alfa, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun mezcla(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    /** Mancha circular con opacidad decreciente hacia el borde. */
    private val mancha: Bitmap by lazy {
        val radio = (radioDp * mapView.resources.displayMetrics.density / escala).coerceAtLeast(4f)
        val lado = (radio * 2).toInt()
        Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888).also { bmp ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(radio, radio, radio, Color.argb(70, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            }
            Canvas(bmp).drawCircle(radio, radio, radio, p)
        }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val ancho = mapView.width
        val alto = mapView.height
        if (ancho == 0 || alto == 0 || geoPuntos.isEmpty()) return
        val w = ancho / escala
        val h = alto / escala
        if (mascara?.width != w || mascara?.height != h) {
            mascara = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            imagen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixeles = IntArray(w * h)
        }
        val m = mascara ?: return
        m.eraseColor(Color.TRANSPARENT)
        val c = Canvas(m)
        val mitad = mancha.width / 2f
        for (g in geoPuntos) {
            projection.toPixels(g, punto)
            val x = punto.x.toFloat() / escala
            val y = punto.y.toFloat() / escala
            if (x < -mitad || y < -mitad || x > w + mitad || y > h + mitad) continue
            c.drawBitmap(mancha, x - mitad, y - mitad, null)
        }
        // Convierte la opacidad acumulada en color de la paleta
        m.getPixels(pixeles, 0, w, 0, 0, w, h)
        for (i in pixeles.indices) pixeles[i] = paleta[pixeles[i] ushr 24]
        val img = imagen ?: return
        img.setPixels(pixeles, 0, w, 0, 0, w, h)
        canvas.drawBitmap(img, null, android.graphics.Rect(0, 0, ancho, alto), pintura)
    }
}

/** Marcador circular de color con borde blanco. */
private fun punto(vista: android.view.View, color: Int, tamanoDp: Int): GradientDrawable {
    val densidad = vista.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((2 * densidad).toInt(), Color.WHITE)
        setSize((tamanoDp * densidad).toInt(), (tamanoDp * densidad).toInt())
    }
}
