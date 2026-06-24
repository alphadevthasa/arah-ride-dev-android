package id.arahride.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RideClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RideClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = RideClient(server.url("/").toString().trimEnd('/'), "customer")
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private val snapshotWithoutNames = """{"sequence":3,"driverOnline":true,"driverLocation":{"latitude":-6.2,"longitude":106.8},
        "ride":{"id":"r1","status":"requested","pickup":{"latitude":-6.2,"longitude":106.8},"dropoff":{"latitude":-6.1,"longitude":106.9},
        "route":[],"driverName":"Dimas","driverPlate":"B 1234 AR"}}"""

    @Test fun decodesPlaceResultsWithUnicodeNames() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[{"name":"Pasar Mangga Dua — Pademangan","displayName":"Pasar Mangga Dua — Pademangan, Jakarta Utara","point":{"latitude":-6.137,"longitude":106.83}}]}"""))
        val results = client.searchPlaces("pasar", null)
        assertEquals(1, results.size)
        assertEquals("Pasar Mangga Dua — Pademangan", results[0].name)
        assertEquals("Pasar Mangga Dua — Pademangan, Jakarta Utara", results[0].displayName)
        assertEquals(Point(-6.137, 106.83), results[0].point)
    }

    @Test fun searchEncodesQueryAndOptionalCenter() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        client.searchPlaces("Jl. Kebon Sirih & Café", Point(-6.2, 106.8))
        client.searchPlaces("monas", null)
        val withCenter = server.takeRequest().requestUrl!!
        assertEquals("/v1/places/search", withCenter.encodedPath)
        assertEquals("Jl. Kebon Sirih & Café", withCenter.queryParameter("q"))
        assertEquals("-6.2", withCenter.queryParameter("lat"))
        assertEquals("106.8", withCenter.queryParameter("lon"))
        assertTrue(withCenter.encodedQuery!!.startsWith("q=Jl.%20Kebon%20Sirih%20%26%20Caf%C3%A9"))
        val withoutCenter = server.takeRequest().requestUrl!!
        assertNull(withoutCenter.queryParameter("lat"))
        assertNull(withoutCenter.queryParameter("lon"))
    }

    @Test fun reverseMapsPlaceAndSendsPoint() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"name":"Monas","displayName":"Monas, Jakarta","point":{"latitude":-6.17,"longitude":106.82}}"""))
        val place = client.reverseGeocode(Point(-6.17, 106.82))
        assertEquals("Monas, Jakarta", place?.displayName)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/places/reverse", url.encodedPath)
        assertEquals("-6.17", url.queryParameter("lat"))
    }

    @Test fun reverseNotFoundMapsToNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Lokasi tidak ditemukan"}"""))
        assertNull(client.reverseGeocode(Point(0.0, 0.0)))
    }

    @Test fun serverErrorsAreRetryableErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Kata kunci harus 2 sampai 160 karakter"}"""))
        try { client.searchPlaces("monas", null); fail("expected IOException") } catch (_: IOException) {}
        try { client.reverseGeocode(Point(1.0, 1.0)); fail("expected IOException") } catch (_: IOException) {}
        try { client.searchPlaces("monas", null); fail("expected IOException") } catch (e: IOException) {
            assertEquals("Kata kunci harus 2 sampai 160 karakter", e.message)
        }
    }

    @Test fun decodesRideSnapshotWithUnicodeNames() {
        val text = snapshotWithoutNames.replace("\"driverName\"", "\"pickupName\":\"Stasiun Gambir\",\"dropoffName\":\"Pasar Mangga Dua — Pademangan\",\"driverName\"")
        val ride = Snapshot.from(text).ride!!
        assertEquals("Stasiun Gambir", ride.pickupName)
        assertEquals("Pasar Mangga Dua — Pademangan", ride.dropoffName)
    }

    @Test fun decodesOlderRideSnapshotWithoutNamesUsingGenericLabels() {
        val ride = Snapshot.from(snapshotWithoutNames).ride!!
        assertEquals("Lokasi jemput", ride.pickupName)
        assertEquals("Lokasi tujuan", ride.dropoffName)
    }

    @Test fun createActionIncludesSelectedLabels() {
        val body = actionBody("customer", "create", Point(1.0, 2.0), Point(3.0, 4.0), null, "Stasiun Gambir", "Kota Tua")
        assertEquals("Stasiun Gambir", body.getString("pickupName"))
        assertEquals("Kota Tua", body.getString("dropoffName"))
        assertTrue(!actionBody("driver", "accept", null, null, null, null, null).has("pickupName"))
    }
}
