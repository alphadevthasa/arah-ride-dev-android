package id.arahride.driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import id.arahride.core.Point
import id.arahride.core.RideClient

class DriverTrackingService : Service(), LocationListener {
    companion object { @Volatile var running = false }
    private val client by lazy { RideClient(BuildConfig.API_BASE_URL, "driver") }
    private val locations by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel("tracking", "Lokasi perjalanan", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, "tracking")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Arah Driver aktif")
            .setContentText("Lokasi dibagikan selama Anda online")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(10, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(10, notification)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) { stopSelf(); return START_NOT_STICKY }
        try {
            val provider = if (locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locations.removeUpdates(this)
            locations.requestLocationUpdates(provider, 3000L, 5f, this, Looper.getMainLooper())
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        client.action("location", location = Point(location.latitude, location.longitude), onState = {}, onError = {})
    }

    override fun onDestroy() {
        running = false
        locations.removeUpdates(this)
        client.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
