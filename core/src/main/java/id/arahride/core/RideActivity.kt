package id.arahride.core

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

abstract class RideActivity : ComponentActivity(), LocationListener {
    abstract val role: String
    abstract val apiBaseUrl: String
    abstract val mapStyleUrl: String
    protected open val keepBackgroundTracking = false
    private lateinit var mapView: MapView
    private lateinit var client: RideClient
    private var map: MapLibreMap? = null
    private var styleReady = false
    private val fixedMarkers = mutableListOf<Marker>()
    private var fixedMarkerKey = ""
    private var routePolyline: Polyline? = null
    private var routeKey = ""
    private var driverMarker: Marker? = null
    private var driverTarget: Point? = null
    private var driverAnimation: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alive = true
    private var snapshot by mutableStateOf<Snapshot?>(null)
    private var error by mutableStateOf<String?>(null)
    private var pickup by mutableStateOf(Point(-6.2088, 106.8456))
    private var dropoff by mutableStateOf(Point(-6.1754, 106.8272))
    private var selecting by mutableStateOf("pickup")
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val driverIcon by lazy {
        val size = (48 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.scale(size / 48f, size / 48f)
        paint.color = Color.WHITE
        canvas.drawCircle(24f, 24f, 24f, paint)
        paint.color = Color.rgb(20, 101, 211)
        canvas.drawCircle(24f, 24f, 20f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(9f, 24f, 42f, 34f), 3f, 3f, paint)
        val roof = Path().apply {
            moveTo(17f, 25f)
            lineTo(21f, 17f)
            lineTo(32f, 17f)
            lineTo(38f, 25f)
            close()
        }
        canvas.drawPath(roof, paint)
        paint.color = Color.rgb(20, 101, 211)
        canvas.drawPath(Path().apply {
            moveTo(22f, 19f); lineTo(27f, 19f); lineTo(27f, 24f); lineTo(19f, 24f); close()
        }, paint)
        canvas.drawPath(Path().apply {
            moveTo(29f, 19f); lineTo(31f, 19f); lineTo(35f, 24f); lineTo(29f, 24f); close()
        }, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(17f, 34f, 4f, paint)
        canvas.drawCircle(35f, 34f, 4f, paint)
        paint.color = Color.rgb(20, 101, 211)
        canvas.drawCircle(17f, 34f, 2f, paint)
        canvas.drawCircle(35f, 34f, 2f, paint)
        IconFactory.getInstance(this).fromBitmap(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_ride)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.setStyle(mapStyleUrl) {
                styleReady = true
                readyMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-6.2088, 106.8456), 11.0))
                refreshMarkers()
            }
            readyMap.addOnMapClickListener { coordinate ->
                if (role == "customer" && snapshot?.ride == null) {
                    if (selecting == "pickup") pickup = Point(coordinate.latitude, coordinate.longitude)
                    else dropoff = Point(coordinate.latitude, coordinate.longitude)
                    refreshMarkers()
                }
                false
            }
        }
        findViewById<ComposeView>(R.id.header).setContent { RideHeader(role, snapshot) }
        findViewById<ComposeView>(R.id.panel).setContent {
            RidePanel(role, snapshot, pickup, dropoff, selecting, error,
                onSelect = { selecting = it }, onAction = ::perform)
        }
        client = RideClient(apiBaseUrl, role)
        client.fetch(::receive, ::fail)
        connectRealtime()
    }

    private fun connectRealtime() {
        client.connect(::receive, ::reconnect)
    }

    private fun reconnect(message: String) {
        fail(message)
        handler.postDelayed({ if (alive) { client.fetch(::receive, ::fail); connectRealtime() } }, 3000)
    }

    private fun receive(state: Snapshot) = runOnUiThread {
        if (!alive || state.sequence < (snapshot?.sequence ?: -1)) return@runOnUiThread
        snapshot = state
        error = null
        refreshMarkers()
        if (role == "driver" && state.driverOnline && state.ride?.status != "in_progress") startLocation()
        else if (role == "driver") stopLocation()
    }

    private fun fail(message: String) = runOnUiThread { if (alive) error = message }

    private fun perform(action: String) {
        error = null
        if (action == "focus_driver") {
            snapshot?.takeIf { it.driverOnline }?.driverLocation?.let {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15.0))
            }
            return
        }
        if (action == "online" && role == "driver" && !hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        client.action(action, if (action == "create") pickup else null,
            if (action == "create") dropoff else null, onState = ::receive, onError = ::fail)
    }

    private fun refreshMarkers() {
        val readyMap = map ?: return
        if (!styleReady) return
        val ride = snapshot?.ride
        val points = if (ride != null) listOf("Jemput" to ride.pickup, "Tujuan" to ride.dropoff)
            else if (role == "customer") listOf("Jemput" to pickup, "Tujuan" to dropoff) else emptyList()
        val key = points.joinToString("|") { "${it.first}:${it.second.latitude}:${it.second.longitude}" }
        if (key != fixedMarkerKey) {
            fixedMarkers.forEach { readyMap.removeMarker(it) }
            fixedMarkers.clear()
            fixedMarkerKey = key
            for ((title, point) in points) fixedMarkers += readyMap.addMarker(
                MarkerOptions().position(LatLng(point.latitude, point.longitude)).title(title))
        }
        val route = ride?.route.orEmpty()
        val newRouteKey = if (route.size >= 2) "${ride?.id}:${route.size}" else ""
        if (newRouteKey != routeKey) {
            routePolyline?.let(readyMap::removePolyline)
            routePolyline = null
            routeKey = newRouteKey
            if (route.size >= 2) {
                routePolyline = readyMap.addPolyline(PolylineOptions()
                    .addAll(route.map { LatLng(it.latitude, it.longitude) })
                    .color(Color.rgb(20, 101, 211))
                    .width(5f))
            }
        }
        val state = snapshot
        if (state?.driverOnline != true) {
            driverAnimation?.cancel()
            driverAnimation = null
            driverMarker?.let { readyMap.removeMarker(it) }
            driverMarker = null
            driverTarget = null
            return
        }
        val target = state.driverLocation
        val marker = driverMarker
        if (marker == null) {
            driverMarker = readyMap.addMarker(MarkerOptions()
                .position(LatLng(target.latitude, target.longitude)).title("Driver").icon(driverIcon))
            driverTarget = target
        } else if (target != driverTarget) {
            driverTarget = target
            driverAnimation?.cancel()
            val start = marker.position
            val end = LatLng(target.latitude, target.longitude)
            driverAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 100L
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val fraction = animation.animatedFraction.toDouble()
                    marker.position = LatLng(
                        start.latitude + (end.latitude - start.latitude) * fraction,
                        start.longitude + (end.longitude - start.longitude) * fraction)
                }
                start()
            }
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        if (role != "driver") return
        if (!hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        onLocationPermissionReady()
    }

    protected open fun onLocationPermissionReady() {
        try {
            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locationManager.removeUpdates(this)
            locationManager.requestLocationUpdates(provider, 3000L, 5f, this, Looper.getMainLooper())
        } catch (e: Exception) { fail("Lokasi tidak tersedia. Aktifkan GPS dan izin lokasi.") }
    }

    protected open fun stopLocation() { locationManager.removeUpdates(this) }

    override fun onLocationChanged(location: android.location.Location) {
        if (snapshot?.driverOnline == true)
            client.action("location", location = Point(location.latitude, location.longitude), onState = ::receive, onError = ::fail)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                if (snapshot?.driverOnline == true) startLocation() else perform("online")
            }
            else fail("Izin lokasi diperlukan agar Customer dapat melihat posisi Driver.")
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart(); if (snapshot?.driverOnline == true && snapshot?.ride?.status != "in_progress") startLocation() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { if (role == "driver" && !keepBackgroundTracking) stopLocation(); mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { alive = false; driverAnimation?.cancel(); handler.removeCallbacksAndMessages(null); client.close(); mapView.onDestroy(); super.onDestroy() }
}
