package id.arahride.core

import org.json.JSONObject

data class Point(val latitude: Double, val longitude: Double) {
    fun json() = JSONObject().put("latitude", latitude).put("longitude", longitude)
    companion object {
        fun from(json: JSONObject) = Point(json.getDouble("latitude"), json.getDouble("longitude"))
    }
}

const val DEFAULT_PICKUP_NAME = "Lokasi jemput"
const val DEFAULT_DROPOFF_NAME = "Lokasi tujuan"

data class PlaceResult(val name: String, val displayName: String, val point: Point) {
    companion object {
        fun from(json: JSONObject) = PlaceResult(
            json.getString("name"), json.getString("displayName"), Point.from(json.getJSONObject("point"))
        )
    }
}

data class Ride(
    val id: String,
    val status: String,
    val pickup: Point,
    val dropoff: Point,
    val route: List<Point>,
    val driverName: String,
    val driverPlate: String,
    val pickupName: String = DEFAULT_PICKUP_NAME,
    val dropoffName: String = DEFAULT_DROPOFF_NAME
) {
    companion object {
        fun from(json: JSONObject) = Ride(
            json.getString("id"), json.getString("status"),
            Point.from(json.getJSONObject("pickup")), Point.from(json.getJSONObject("dropoff")),
            json.optJSONArray("route")?.let { points ->
                List(points.length()) { index -> Point.from(points.getJSONObject(index)) }
            } ?: emptyList(),
            json.getString("driverName"), json.getString("driverPlate"),
            json.optString("pickupName").ifBlank { DEFAULT_PICKUP_NAME },
            json.optString("dropoffName").ifBlank { DEFAULT_DROPOFF_NAME }
        )
    }
}

data class Snapshot(val sequence: Long, val driverOnline: Boolean, val driverLocation: Point, val ride: Ride?) {
    companion object {
        fun from(text: String): Snapshot {
            val json = JSONObject(text)
            return Snapshot(
                json.getLong("sequence"), json.getBoolean("driverOnline"),
                Point.from(json.getJSONObject("driverLocation")),
                json.optJSONObject("ride")?.let(Ride::from)
            )
        }
    }
}
