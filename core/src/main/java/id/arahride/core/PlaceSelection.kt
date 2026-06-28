package id.arahride.core

const val MAP_PICK_NAME = "Lokasi dipilih di peta"
const val SEARCH_ERROR_MESSAGE = "Pencarian gagal. Coba lagi."
const val SEARCH_TOO_SHORT_MESSAGE = "Ketik minimal 2 karakter"
const val SEARCH_EMPTY_MESSAGE = "Lokasi tidak ditemukan"

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data object Empty : SearchState
    data class Results(val places: List<PlaceResult>) : SearchState
    data class Error(val message: String) : SearchState
}

/**
 * Pickup/dropoff selection state. Every asynchronous completion carries the token and field it
 * was issued for, and is dropped when the state has moved on, so stale responses cannot win.
 */
data class PlaceUiState(
    val pickup: Point = Point(-6.2088, 106.8456),
    val dropoff: Point = Point(-6.1754, 106.8272),
    val pickupName: String = DEFAULT_PICKUP_NAME,
    val dropoffName: String = DEFAULT_DROPOFF_NAME,
    val selecting: String = "pickup",
    val query: String = "",
    val search: SearchState = SearchState.Idle,
    val searchToken: Int = 0
) {
    val activePoint: Point get() = point(selecting)

    fun point(field: String) = if (field == "pickup") pickup else dropoff

    fun withQuery(value: String) = copy(query = value)

    fun select(field: String) =
        if (field == selecting) this else copy(selecting = field, search = SearchState.Idle, searchToken = searchToken + 1)

    /** Moves to Loading and issues a new token only for a valid query; the caller sends the request then. */
    fun beginSearch(): PlaceUiState {
        if (query.trim().length < 2) return copy(search = SearchState.Error(SEARCH_TOO_SHORT_MESSAGE))
        return copy(search = SearchState.Loading, searchToken = searchToken + 1)
    }

    fun searchSucceeded(token: Int, field: String, places: List<PlaceResult>) =
        if (isCurrentSearch(token, field)) copy(search = if (places.isEmpty()) SearchState.Empty else SearchState.Results(places)) else this

    fun searchFailed(token: Int, field: String) =
        if (isCurrentSearch(token, field)) copy(search = SearchState.Error(SEARCH_ERROR_MESSAGE)) else this

    private fun isCurrentSearch(token: Int, field: String) =
        token == searchToken && field == selecting && search == SearchState.Loading

    fun choose(place: PlaceResult) = setActive(place.point, place.displayName).copy(search = SearchState.Idle, searchToken = searchToken + 1)

    fun mapTapped(point: Point) = setActive(point, MAP_PICK_NAME)

    /** Applies a reverse-geocode result only if [field] still holds the [point] it was requested for. */
    fun reverseResolved(field: String, point: Point, place: PlaceResult?): PlaceUiState {
        if (place == null || point(field) != point) return this
        return if (field == "pickup") copy(pickupName = place.displayName) else copy(dropoffName = place.displayName)
    }

    private fun setActive(point: Point, name: String) =
        if (selecting == "pickup") copy(pickup = point, pickupName = name) else copy(dropoff = point, dropoffName = name)
}

fun locationLines(pickupName: String, dropoffName: String) = listOf("Jemput: $pickupName", "Tujuan: $dropoffName")
