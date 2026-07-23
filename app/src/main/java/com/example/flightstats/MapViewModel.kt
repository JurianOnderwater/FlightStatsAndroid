package com.example.flightstats

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flightstats.data.Airport
import com.example.flightstats.data.FlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

import com.example.flightstats.data.Flight

data class MapRoute(
    val fromIata: String,
    val toIata: String,
    val fromPoint: GeoPoint,
    val toPoint: GeoPoint
)

data class SelectedAirportInfo(
    val airport: Airport,
    val totalFlights: Int,
    val recentChronologicalFlights: List<Flight>
)

data class MapUiState(
    val selectedYear: String = "All Time",
    val availableYears: List<String> = emptyList(),
    val hometownIata: String = "AMS",
    val userName: String = "User",
    val userInitial: String = "U",
    val mapStyle: String = "light",
    val preferredUnit: String = "km",
    val isRouteCurved: Boolean = true,
    val defaultZoom: Float = 4.5f,
    val totalFlights: Int = 0,
    val totalCountries: Int = 0,
    val totalAirports: Int = 0,
    val totalRoutes: Int = 0,
    val routes: List<MapRoute> = emptyList(),
    val centerPoint: GeoPoint = GeoPoint(52.3105, 4.7683), // default AMS
    val selectedAirportInfo: SelectedAirportInfo? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: FlightRepository,
    private val application: Application
) : ViewModel() {

    private val sharedPrefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(application)
    }

    // Force updates by emitting a trigger flow
    private val _prefsTrigger = MutableStateFlow(0)
    private val _selectedAirportIata = MutableStateFlow<String?>(null)
    private val _selectedYear = MutableStateFlow("All Time")

    private data class IntermediateMapState(
        val selectedYear: String,
        val availableYears: List<String>,
        val hometown: String,
        val userName: String,
        val userInitial: String,
        val mapStyle: String,
        val preferredUnit: String,
        val isRouteCurved: Boolean,
        val defaultZoom: Float,
        val flightsForMap: List<Flight>,
        val airportSet: Set<String>,
        val countrySet: Set<String>,
        val routesList: List<MapRoute>,
        val centerPoint: GeoPoint,
        val airportMap: Map<String, Airport>
    )

    private val baseMapStateFlow = combine(
        repository.getAllFlightsFlow(),
        _prefsTrigger,
        _selectedYear
    ) { allFlights, _, selectedYear ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val pastFlights = allFlights.filter { it.date != null && it.date < todayStr }

        // Extract available years
        val yearsSet = mutableSetOf<String>()
        for (f in pastFlights) {
            f.date?.let { d ->
                if (d.length >= 4) {
                    yearsSet.add(d.substring(0, 4))
                }
            }
        }
        val currentYearStr = Calendar.getInstance().get(Calendar.YEAR).toString()
        yearsSet.add(currentYearStr)
        val sortedYears = yearsSet.sortedDescending()
        val availableYears = listOf("All Time") + sortedYears

        val flightsForMap = if (selectedYear == "All Time") {
            pastFlights
        } else {
            pastFlights.filter { it.date != null && it.date.startsWith(selectedYear) }
        }

        val hometown = sharedPrefs.getString("hometown", "AMS")?.trim()?.uppercase() ?: "AMS"
        val userName = sharedPrefs.getString("user_name", "User")?.trim() ?: "User"
        val mapStyle = sharedPrefs.getString("map_style", "light") ?: "light"
        val preferredUnit = sharedPrefs.getString("preferred_unit", "km") ?: "km"
        val isRouteCurved = sharedPrefs.getBoolean("map_route_curved", true)
        val defaultZoom = sharedPrefs.getFloat("default_zoom", 4.5f)

        val userInitial = if (userName.isNotEmpty()) userName.substring(0, 1).uppercase() else "U"

        // Batch query all map airports in a single SQL query
        val allIatas = flightsForMap.flatMap { listOfNotNull(it.origin, it.destination) }.toSet() + setOf(hometown, "AMS")
        val airportMap = repository.getAirportsByIatas(allIatas)

        val airportSet = mutableSetOf<String>()
        val countrySet = mutableSetOf<String>()
        val routesList = mutableListOf<MapRoute>()
        val routeCache = mutableSetOf<String>()

        for (f in flightsForMap) {
            val o = f.origin.orEmpty()
            val d = f.destination.orEmpty()
            airportSet.add(o)
            airportSet.add(d)

            val originAirport = airportMap[o]
            val destAirport = airportMap[d]

            originAirport?.country?.let { countrySet.add(it) }
            destAirport?.country?.let { countrySet.add(it) }

            if (originAirport != null && destAirport != null) {
                val pair = arrayOf(o, d).apply { sort() }
                val routeKey = "${pair[0]}-${pair[1]}"
                if (!routeCache.contains(routeKey)) {
                    routeCache.add(routeKey)
                    routesList.add(
                        MapRoute(
                            fromIata = o,
                            toIata = d,
                            fromPoint = GeoPoint(originAirport.lat, originAirport.lng),
                            toPoint = GeoPoint(destAirport.lat, destAirport.lng)
                        )
                    )
                }
            }
        }

        val hometownAirport = airportMap[hometown]
        val centerPoint = if (hometownAirport != null) {
            GeoPoint(hometownAirport.lat, hometownAirport.lng)
        } else {
            airportMap["AMS"]?.let {
                GeoPoint(it.lat, it.lng)
            } ?: GeoPoint(52.3105, 4.7683)
        }

        IntermediateMapState(
            selectedYear = selectedYear,
            availableYears = availableYears,
            hometown = hometown,
            userName = userName,
            userInitial = userInitial,
            mapStyle = mapStyle,
            preferredUnit = preferredUnit,
            isRouteCurved = isRouteCurved,
            defaultZoom = defaultZoom,
            flightsForMap = flightsForMap,
            airportSet = airportSet,
            countrySet = countrySet,
            routesList = routesList,
            centerPoint = centerPoint,
            airportMap = airportMap
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<MapUiState> = combine(
        baseMapStateFlow,
        _selectedAirportIata
    ) { baseState, selectedIata ->
        val selectedAirportInfo = if (!selectedIata.isNullOrBlank()) {
            val airport = baseState.airportMap[selectedIata] ?: repository.getAirportByIata(selectedIata)
            if (airport != null) {
                val airportFlights = baseState.flightsForMap.filter {
                    it.origin.equals(selectedIata, ignoreCase = true) ||
                    it.destination.equals(selectedIata, ignoreCase = true)
                }
                val recent5Chronological = airportFlights
                    .filter { !it.date.isNullOrEmpty() }
                    .sortedByDescending { it.date }
                    .take(5)
                    .sortedBy { it.date }

                SelectedAirportInfo(
                    airport = airport,
                    totalFlights = airportFlights.size,
                    recentChronologicalFlights = recent5Chronological
                )
            } else null
        } else null

        MapUiState(
            selectedYear = baseState.selectedYear,
            availableYears = baseState.availableYears,
            hometownIata = baseState.hometown,
            userName = baseState.userName,
            userInitial = baseState.userInitial,
            mapStyle = baseState.mapStyle,
            preferredUnit = baseState.preferredUnit,
            isRouteCurved = baseState.isRouteCurved,
            defaultZoom = baseState.defaultZoom,
            totalFlights = baseState.flightsForMap.size,
            totalCountries = baseState.countrySet.size,
            totalAirports = baseState.airportSet.size,
            totalRoutes = baseState.routesList.size,
            routes = baseState.routesList,
            centerPoint = baseState.centerPoint,
            selectedAirportInfo = selectedAirportInfo
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MapUiState()
        )

    fun refreshPrefs() {
        _prefsTrigger.value = _prefsTrigger.value + 1
    }

    fun selectYear(year: String) {
        _selectedYear.value = year
        _selectedAirportIata.value = null
    }

    fun selectAirport(iata: String?) {
        _selectedAirportIata.value = iata
    }

    fun clearSelectedAirport() {
        _selectedAirportIata.value = null
    }
}
