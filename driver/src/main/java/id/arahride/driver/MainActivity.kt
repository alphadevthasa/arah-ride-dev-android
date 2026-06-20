package id.arahride.driver

import android.content.Intent
import id.arahride.core.RideActivity

class MainActivity : RideActivity() {
    override val role = "driver"
    override val apiBaseUrl = BuildConfig.API_BASE_URL
    override val mapStyleUrl = BuildConfig.MAP_STYLE_URL
    override val keepBackgroundTracking = true

    override fun onLocationPermissionReady() {
        if (!DriverTrackingService.running) startForegroundService(Intent(this, DriverTrackingService::class.java))
    }

    override fun stopLocation() {
        stopService(Intent(this, DriverTrackingService::class.java))
    }
}
