package id.arahride.customer

import id.arahride.core.RideActivity

class MainActivity : RideActivity() {
    override val role = "customer"
    override val apiBaseUrl = BuildConfig.API_BASE_URL
    override val mapStyleUrl = BuildConfig.MAP_STYLE_URL
}
