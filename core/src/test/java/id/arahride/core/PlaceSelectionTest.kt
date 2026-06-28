package id.arahride.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSelectionTest {
    private val gambir = PlaceResult("Stasiun Gambir", "Stasiun Gambir, Jakarta Pusat", Point(-6.1766, 106.8306))
    private val mangga = PlaceResult("Pasar Mangga Dua — Pademangan", "Pasar Mangga Dua — Pademangan, Jakarta Utara", Point(-6.137, 106.83))
    private val coordinate = Regex("""-?\d+[.,]\d{3,}""")

    @Test fun searchRequiresExplicitSubmit() {
        val typed = PlaceUiState().withQuery("gambir")
        assertEquals(SearchState.Idle, typed.search)
        assertEquals(0, typed.searchToken)
        val submitted = typed.beginSearch()
        assertEquals(SearchState.Loading, submitted.search)
        assertEquals(1, submitted.searchToken)
    }

    @Test fun tooShortQueryShowsErrorWithoutRequest() {
        val state = PlaceUiState().withQuery(" a ").beginSearch()
        assertTrue(state.search is SearchState.Error)
        assertEquals(0, state.searchToken)
    }

    @Test fun resultsEmptyAndErrorStatesAreReported() {
        val loading = PlaceUiState().withQuery("gambir").beginSearch()
        assertEquals(SearchState.Results(listOf(gambir)), loading.searchSucceeded(loading.searchToken, "pickup", listOf(gambir)).search)
        assertEquals(SearchState.Empty, loading.searchSucceeded(loading.searchToken, "pickup", emptyList()).search)
        val failed = loading.searchFailed(loading.searchToken, "pickup").search
        assertTrue(failed is SearchState.Error && failed.message.isNotBlank())
        // retry after error issues a new request
        assertEquals(SearchState.Loading, loading.searchFailed(loading.searchToken, "pickup").beginSearch().search)
    }

    @Test fun switchingFieldInvalidatesPendingAndShownResults() {
        val loading = PlaceUiState().withQuery("gambir").beginSearch()
        val switched = loading.select("dropoff")
        assertEquals(SearchState.Idle, switched.search)
        val late = switched.searchSucceeded(loading.searchToken, "pickup", listOf(gambir))
        assertEquals(SearchState.Idle, late.search)
        val shown = loading.searchSucceeded(loading.searchToken, "pickup", listOf(gambir))
        assertEquals(SearchState.Idle, shown.select("dropoff").search)
    }

    @Test fun staleResponseCannotReplaceNewerQuery() {
        val first = PlaceUiState().withQuery("gambir").beginSearch()
        val second = first.withQuery("mangga dua").beginSearch()
        val afterOld = second.searchSucceeded(first.searchToken, "pickup", listOf(gambir))
        assertEquals(SearchState.Loading, afterOld.search)
        assertEquals(SearchState.Loading, afterOld.searchFailed(first.searchToken, "pickup").search)
        val afterNew = afterOld.searchSucceeded(second.searchToken, "pickup", listOf(mangga))
        assertEquals(SearchState.Results(listOf(mangga)), afterNew.search)
    }

    @Test fun choosingResultUpdatesActivePointAndNameAndClosesResults() {
        val loading = PlaceUiState().select("dropoff").withQuery("mangga").beginSearch()
        val chosen = loading.searchSucceeded(loading.searchToken, "dropoff", listOf(mangga)).choose(mangga)
        assertEquals(mangga.point, chosen.dropoff)
        assertEquals(mangga.displayName, chosen.dropoffName)
        assertEquals(DEFAULT_PICKUP_NAME, chosen.pickupName)
        assertEquals(SearchState.Idle, chosen.search)
    }

    @Test fun mapTapSetsPointImmediatelyWithGenericMapLabel() {
        val tapped = PlaceUiState().mapTapped(Point(-6.2, 106.8))
        assertEquals(Point(-6.2, 106.8), tapped.pickup)
        assertEquals(MAP_PICK_NAME, tapped.pickupName)
    }

    @Test fun reverseResultReplacesLabelOnlyForSamePoint() {
        val tapped = PlaceUiState().mapTapped(Point(-6.2, 106.8))
        assertEquals(gambir.displayName, tapped.reverseResolved("pickup", Point(-6.2, 106.8), gambir).pickupName)
        val retapped = tapped.mapTapped(Point(-6.3, 106.9))
        assertEquals(MAP_PICK_NAME, retapped.reverseResolved("pickup", Point(-6.2, 106.8), gambir).pickupName)
    }

    @Test fun reverseFailureKeepsPointAndGenericLabel() {
        val tapped = PlaceUiState().select("dropoff").mapTapped(Point(-6.2, 106.8))
        val missed = tapped.reverseResolved("dropoff", Point(-6.2, 106.8), null)
        assertEquals(Point(-6.2, 106.8), missed.dropoff)
        assertEquals(MAP_PICK_NAME, missed.dropoffName)
    }

    @Test fun lateReverseForOtherFieldStillLabelsItsOwnUnchangedPoint() {
        val tapped = PlaceUiState().mapTapped(Point(-6.2, 106.8))
        val switched = tapped.select("dropoff")
        assertEquals(gambir.displayName, switched.reverseResolved("pickup", Point(-6.2, 106.8), gambir).pickupName)
    }

    @Test fun locationTextUsesLabelsNeverCoordinates() {
        val lines = locationLines("Stasiun Gambir", "Pasar Mangga Dua — Pademangan")
        assertEquals(listOf("Jemput: Stasiun Gambir", "Tujuan: Pasar Mangga Dua — Pademangan"), lines)
        val fromOldClient = Snapshot.from(
            """{"sequence":1,"driverOnline":true,"driverLocation":{"latitude":-6.2,"longitude":106.8},"ride":{"id":"r","status":"requested",
            "pickup":{"latitude":-6.2088,"longitude":106.8456},"dropoff":{"latitude":-6.1754,"longitude":106.8272},"route":[],"driverName":"D","driverPlate":"P"}}"""
        ).ride!!
        for (line in locationLines(fromOldClient.pickupName, fromOldClient.dropoffName)) assertFalse(line, coordinate.containsMatchIn(line))
        assertFalse(coordinate.containsMatchIn(PlaceUiState().pickupName + PlaceUiState().dropoffName + MAP_PICK_NAME))
    }
}
