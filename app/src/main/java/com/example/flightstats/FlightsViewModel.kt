package com.example.flightstats

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flightstats.data.AirportImporter
import com.example.flightstats.data.CsvImporter
import com.example.flightstats.data.Flight
import com.example.flightstats.data.FlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class FlightsUiState(
    val isLoading: Boolean = false,
    val flights: List<FlightListItem> = emptyList(),
    val showUpcoming: Boolean = true
)

@HiltViewModel
class FlightsViewModel @Inject constructor(
    private val repository: FlightRepository,
    private val application: Application
) : ViewModel() {

    private val _showUpcoming = MutableStateFlow(true)
    private var isInitialLoad = true

    val uiState: StateFlow<FlightsUiState> = combine(
        repository.getAllFlightsFlow(),
        _showUpcoming
    ) { allFlights, showUpcoming ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        if (isInitialLoad && allFlights.isNotEmpty()) {
            val upcomingCount = allFlights.count { it.date != null && it.date >= todayStr }
            _showUpcoming.value = upcomingCount > 0
            isInitialLoad = false
        }

        val filteredAndSorted = allFlights
            .filter { f ->
                if (f.date != null) {
                    if (showUpcoming) f.date >= todayStr else f.date < todayStr
                } else {
                    false
                }
            }
            .sortedWith { a, b ->
                val dateCompare = a.date!!.compareTo(b.date!!)
                if (dateCompare != 0) {
                    if (showUpcoming) dateCompare else -dateCompare
                } else {
                    val depA = a.departureTime ?: ""
                    val depB = b.departureTime ?: ""
                    val timeCompare = depA.compareTo(depB)
                    if (showUpcoming) timeCompare else -timeCompare
                }
            }

        val items = filteredAndSorted.map { f ->
            val originAirport = repository.getAirportByIata(f.origin.orEmpty())
            val destAirport = repository.getAirportByIata(f.destination.orEmpty())
            FlightListItem(
                id = f.id,
                origin = f.origin.orEmpty(),
                destination = f.destination.orEmpty(),
                date = f.date,
                distance = f.distance,
                flightNumber = f.flightNumber,
                airline = f.airline,
                seat = f.seat,
                seatClass = f.seatClass,
                notes = f.notes,
                departureTime = f.departureTime,
                arrivalTime = f.arrivalTime,
                originCity = originAirport?.city,
                originCountry = originAirport?.country,
                destCity = destAirport?.city,
                destCountry = destAirport?.country
            )
        }

        FlightsUiState(
            isLoading = false,
            flights = items,
            showUpcoming = showUpcoming
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlightsUiState(isLoading = true)
        )

    init {
        importInitialData()
    }

    fun toggleShowUpcoming() {
        _showUpcoming.value = !_showUpcoming.value
    }

    fun deleteFlight(flightId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getFlightById(flightId)?.let {
                repository.deleteFlight(it)
            }
        }
    }

    fun updateFlight(
        flightId: Int,
        airline: String,
        flightNumber: String,
        seat: String,
        seatClass: String,
        notes: String,
        departureTime: String,
        arrivalTime: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getFlightById(flightId)?.let { current ->
                val updated = current.copy(
                    airline = airline.trim().ifEmpty { null },
                    flightNumber = flightNumber.trim().ifEmpty { null },
                    seat = seat.trim().ifEmpty { null },
                    seatClass = seatClass.trim().ifEmpty { null },
                    notes = notes.trim().ifEmpty { null },
                    departureTime = departureTime.trim().ifEmpty { null },
                    arrivalTime = arrivalTime.trim().ifEmpty { null }
                )
                repository.updateFlight(updated)
            }
        }
    }

    fun insertFlight(
        origin: String,
        destination: String,
        date: String,
        flightNumber: String,
        airline: String,
        seat: String,
        seatClass: String,
        notes: String,
        departureTime: String,
        arrivalTime: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val originUpper = origin.trim().uppercase()
            val destUpper = destination.trim().uppercase()
            val oAirport = repository.getAirportByIata(originUpper)
            val dAirport = repository.getAirportByIata(destUpper)
            if (oAirport == null) {
                viewModelScope.launch(Dispatchers.Main) { onFailure("Airport not found: $originUpper") }
                return@launch
            }
            if (dAirport == null) {
                viewModelScope.launch(Dispatchers.Main) { onFailure("Airport not found: $destUpper") }
                return@launch
            }
            val dist = haversine(oAirport.lat, oAirport.lng, dAirport.lat, dAirport.lng)
            val flight = Flight(
                origin = originUpper,
                destination = destUpper,
                date = date.trim().ifEmpty { null },
                distance = dist,
                flightNumber = flightNumber.trim().ifEmpty { null },
                airline = airline.trim().ifEmpty { null },
                seat = seat.trim().ifEmpty { null },
                seatClass = seatClass.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
                departureTime = departureTime.trim().ifEmpty { null },
                arrivalTime = arrivalTime.trim().ifEmpty { null }
            )
            repository.insertFlight(flight)
            viewModelScope.launch(Dispatchers.Main) { onSuccess() }
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun importInitialData() {
        viewModelScope.launch {
            AirportImporter.importIfNeeded(application, repository)
            CsvImporter.importIfNeeded(application, repository)
        }
    }
}
