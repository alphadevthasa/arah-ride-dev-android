package id.arahride.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun actionBody(
    role: String, name: String, pickup: Point?, dropoff: Point?, location: Point?,
    pickupName: String?, dropoffName: String?
): JSONObject {
    val json = JSONObject().put("role", role).put("action", name)
    pickup?.let { json.put("pickup", it.json()) }
    dropoff?.let { json.put("dropoff", it.json()) }
    location?.let { json.put("location", it.json()) }
    pickupName?.let { json.put("pickupName", it) }
    dropoffName?.let { json.put("dropoffName", it) }
    return json
}

class RideClient(private val baseUrl: String, private val role: String) {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    @Volatile private var closed = false

    fun connect(onState: (Snapshot) -> Unit, onError: (String) -> Unit) {
        if (closed) return
        val url = baseUrl.replaceFirst("http", "ws") + "/v1/events?role=$role"
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try { onState(Snapshot.from(text)) } catch (e: Exception) { onError(e.message ?: "Data server tidak valid") }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed) onError("Koneksi realtime terputus: ${t.message ?: "coba lagi"}")
            }
        })
    }

    fun fetch(onState: (Snapshot) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url("$baseUrl/v1/state").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError("Server tidak tersedia: ${e.message}")
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try { onState(Snapshot.from(it.body?.string().orEmpty())) }
                    catch (e: Exception) { onError(e.message ?: "Data server tidak valid") }
                }
            }
        })
    }

    fun action(name: String, pickup: Point? = null, dropoff: Point? = null, location: Point? = null,
               pickupName: String? = null, dropoffName: String? = null,
               onState: (Snapshot) -> Unit, onError: (String) -> Unit) {
        val json = actionBody(role, name, pickup, dropoff, location, pickupName, dropoffName)
        val request = Request.Builder().url("$baseUrl/v1/actions")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError("Gagal mengirim $name: ${e.message}")
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onError(runCatching { JSONObject(text).getString("error") }.getOrDefault("Aksi gagal (${it.code})"))
                    } else {
                        try { onState(Snapshot.from(text)) }
                        catch (e: Exception) { onError(e.message ?: "Data server tidak valid") }
                    }
                }
            }
        })
    }

    /** Throws [IOException] with a user-readable message on any non-2xx response. */
    suspend fun searchPlaces(query: String, center: Point?): List<PlaceResult> {
        val url = "$baseUrl/v1/places/search".toHttpUrl().newBuilder().addQueryParameter("q", query).apply {
            center?.let { addQueryParameter("lat", it.latitude.toString()).addQueryParameter("lon", it.longitude.toString()) }
        }.build()
        val text = executeText(Request.Builder().url(url).build(), allowNotFound = false) ?: return emptyList()
        return try {
            val results = JSONObject(text).getJSONArray("results")
            List(results.length()) { PlaceResult.from(results.getJSONObject(it)) }
        } catch (e: Exception) {
            throw IOException("Hasil pencarian tidak valid", e)
        }
    }

    /** Returns null when the server has no matching place (HTTP 404). */
    suspend fun reverseGeocode(point: Point): PlaceResult? {
        val url = "$baseUrl/v1/places/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", point.latitude.toString())
            .addQueryParameter("lon", point.longitude.toString()).build()
        val text = executeText(Request.Builder().url(url).build(), allowNotFound = true) ?: return null
        return try { PlaceResult.from(JSONObject(text)) } catch (e: Exception) { throw IOException("Hasil lokasi tidak valid", e) }
    }

    private suspend fun executeText(request: Request, allowNotFound: Boolean): String? = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use {
            val text = it.body?.string().orEmpty()
            when {
                allowNotFound && it.code == 404 -> null
                it.isSuccessful -> text
                else -> throw IOException(runCatching { JSONObject(text).getString("error") }.getOrDefault("Server gagal (${it.code})"))
            }
        }
    }

    fun close() {
        closed = true
        socket?.close(1000, "screen closed")
        client.dispatcher.executorService.shutdown()
    }
}
