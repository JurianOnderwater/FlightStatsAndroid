package com.example.flightstats

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flightstats.data.Airport
import com.example.flightstats.data.Flight
import com.example.flightstats.data.FlightRepository
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.prompt.*
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CountrySlice(
    val code: String,
    val value: Float,
    val percentage: String
)

data class CumulativePoint(
    val label: String,
    val count: Int,
    val flightInfo: String,
    val timestamp: Long
)

data class StatsUiState(
    val selectedYear: String = "All Time",
    val availableYears: List<String> = emptyList(),
    val totalFlights: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val uniqueAirports: Int = 0,
    val uniqueRoutes: Int = 0,
    val uniqueCountries: Int = 0,
    val longestFlight: Flight? = null,
    val longestFlightOriginAirport: Airport? = null,
    val longestFlightDestAirport: Airport? = null,
    val topAirports: List<Triple<String, String, Int>> = emptyList(), // IATA, city, count
    val topRoutes: List<Pair<String, Int>> = emptyList(), // "Origin-Dest", count
    val byMonth: IntArray = IntArray(12),
    val byDay: IntArray = IntArray(7),
    val byYear: List<Pair<String, Int>> = emptyList(),
    val springFlights: Int = 0,
    val summerFlights: Int = 0,
    val autumnFlights: Int = 0,
    val winterFlights: Int = 0,
    val countryPercentages: List<CountrySlice> = emptyList(),
    val cumulativePoints: List<CumulativePoint> = emptyList(),
    val aiStory: String? = null,
    val isStoryGenerating: Boolean = false,
    val storyError: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: FlightRepository,
    private val application: Application
) : ViewModel() {

    private val _selectedYear = MutableStateFlow("All Time")
    private val _isGenerating = MutableStateFlow(false)
    private val _storyError = MutableStateFlow<String?>(null)

    private val sharedPrefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(application)
    }

    private val baseStatsFlow = combine(
        repository.getAllFlightsFlow(),
        _selectedYear
    ) { allFlights, selectedYear ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Past flights only for stats
        val pastFlights = allFlights.filter { it.date != null && it.date < todayStr }

        // Extract available years
        val yearsSet = mutableSetOf<String>()
        for (f in pastFlights) {
            f.date?.let {
                if (it.length >= 4) {
                    yearsSet.add(it.substring(0, 4))
                }
            }
        }
        val currentYearStr = Calendar.getInstance().get(Calendar.YEAR).toString()
        yearsSet.add(currentYearStr)
        val sortedYears = yearsSet.sortedDescending()
        val availableYears = listOf("All Time") + sortedYears

        // Filter flights by selected year
        val filteredFlights = if (selectedYear == "All Time") {
            pastFlights
        } else {
            pastFlights.filter { it.date != null && it.date.startsWith(selectedYear) }
        }

        // Batch query all required airports in a single SQL call to eliminate N+1 queries
        val allIatas = filteredFlights.flatMap { listOfNotNull(it.origin, it.destination) }.toSet()
        val airportMap = repository.getAirportsByIatas(allIatas)

        // Compute base stats
        var totalDistanceKm = 0.0
        val airportSet = mutableSetOf<String>()
        val routeSet = mutableSetOf<String>()
        val countrySet = mutableSetOf<String>()

        val airportCounts = mutableMapOf<String, Int>()
        val routeCounts = mutableMapOf<String, Int>()
        val countryCounts = mutableMapOf<String, Int>()

        var longestFlight: Flight? = null

        val byMonth = IntArray(12)
        val byDay = IntArray(7)
        val byYearMap = mutableMapOf<Int, Int>()

        var springFlights = 0
        var summerFlights = 0
        var autumnFlights = 0
        var winterFlights = 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (f in filteredFlights) {
            totalDistanceKm += f.distance
            val o = f.origin.orEmpty()
            val d = f.destination.orEmpty()

            airportSet.add(o)
            airportSet.add(d)

            airportCounts[o] = (airportCounts[o] ?: 0) + 1
            airportCounts[d] = (airportCounts[d] ?: 0) + 1

            val pair = arrayOf(o, d)
            pair.sort()
            val routeKey = "${pair[0]}-${pair[1]}"
            routeSet.add(routeKey)
            routeCounts[routeKey] = (routeCounts[routeKey] ?: 0) + 1

            if (longestFlight == null || f.distance > longestFlight.distance) {
                longestFlight = f
            }

            // High performance O(1) in-memory airport lookup
            val originAirport = airportMap[o]
            val destAirport = airportMap[d]

            originAirport?.country?.let {
                countrySet.add(it)
                countryCounts[it] = (countryCounts[it] ?: 0) + 1
            }
            destAirport?.country?.let {
                countrySet.add(it)
                countryCounts[it] = (countryCounts[it] ?: 0) + 1
            }

            // Month breakdown
            f.date?.let { dStr ->
                if (dStr.length >= 7) {
                    val month = dStr.substring(5, 7).toIntOrNull()?.minus(1)
                    if (month != null && month in 0..11) {
                        byMonth[month]++
                    }
                }
                if (dStr.length >= 10) {
                    try {
                        val parsed = sdf.parse(dStr)
                        parsed?.let {
                            val cal = Calendar.getInstance().apply { time = it }
                            val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun...7=Sat
                            val idx = (dow + 5) % 7 // 0=Mon...6=Sun
                            byDay[idx]++
                        }
                    } catch (_: Exception) {}
                }
                if (dStr.length >= 4) {
                    dStr.substring(0, 4).toIntOrNull()?.let { year ->
                        byYearMap[year] = (byYearMap[year] ?: 0) + 1
                    }
                }

                // Seasons breakdown
                if (dStr.length >= 7) {
                    val month = dStr.substring(5, 7).toIntOrNull()?.minus(1)
                    if (month != null) {
                        when (month) {
                            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> springFlights++
                            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> summerFlights++
                            Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> autumnFlights++
                            else -> winterFlights++
                        }
                    }
                }
            }
        }

        // Longest flight airport information
        val longestFlightOrigin = longestFlight?.let { airportMap[it.origin.orEmpty()] }
        val longestFlightDest = longestFlight?.let { airportMap[it.destination.orEmpty()] }

        // Top airports
        val topAirports = airportCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { entry ->
                val airport = airportMap[entry.key]
                val city = airport?.city ?: entry.key
                Triple(entry.key, city, entry.value)
            }

        // Top routes
        val topRoutes = routeCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { Pair(it.key, it.value) }

        // By Year
        val byYear = byYearMap.entries
            .sortedBy { it.key }
            .map { Pair(it.key.toString(), it.value) }

        // Country percentages (Pie slices)
        val pieTotal = countryCounts.values.sum().toFloat()
        val sortedCountries = countryCounts.entries.sortedByDescending { it.value }
        val countrySlices = mutableListOf<CountrySlice>()
        var otherCount = 0f
        for (i in sortedCountries.indices) {
            if (i < 5) {
                val code = sortedCountries[i].key
                val valFloat = sortedCountries[i].value.toFloat()
                val pct = if (pieTotal > 0f) String.format(Locale.US, "%.1f%%", (valFloat / pieTotal) * 100f) else "0%"
                countrySlices.add(CountrySlice(code, valFloat, pct))
            } else {
                otherCount += sortedCountries[i].value
            }
        }
        if (otherCount > 0f) {
            val pct = if (pieTotal > 0f) String.format(Locale.US, "%.1f%%", (otherCount / pieTotal) * 100f) else "0%"
            countrySlices.add(CountrySlice("Other", otherCount, pct))
        }

        // Cumulative flight points calculation with real calendar timestamps
        val sortedFlightsForCumulative = filteredFlights
            .filter { !it.date.isNullOrEmpty() }
            .sortedBy { it.date }

        val sdfDateParse = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val cumulativePoints = sortedFlightsForCumulative.mapIndexed { idx, f ->
            val routeStr = "${f.origin.orEmpty()} → ${f.destination.orEmpty()}"
            val dStr = f.date.orEmpty()
            val timeMillis = try {
                sdfDateParse.parse(dStr)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }

            CumulativePoint(
                label = dStr,
                count = idx + 1,
                flightInfo = "Flight #${idx + 1}: $routeStr ($dStr)",
                timestamp = timeMillis
            )
        }

        // Load AI Story
        val aiStory = sharedPrefs.getString("saved_story_$selectedYear", null)

        StatsUiState(
            selectedYear = selectedYear,
            availableYears = availableYears,
            totalFlights = filteredFlights.size,
            totalDistanceKm = totalDistanceKm,
            uniqueAirports = airportSet.size,
            uniqueRoutes = routeSet.size,
            uniqueCountries = countrySet.size,
            longestFlight = longestFlight,
            longestFlightOriginAirport = longestFlightOrigin,
            longestFlightDestAirport = longestFlightDest,
            topAirports = topAirports,
            topRoutes = topRoutes,
            byMonth = byMonth,
            byDay = byDay,
            byYear = byYear,
            springFlights = springFlights,
            summerFlights = summerFlights,
            autumnFlights = autumnFlights,
            winterFlights = winterFlights,
            countryPercentages = countrySlices,
            cumulativePoints = cumulativePoints,
            aiStory = aiStory
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<StatsUiState> = combine(
        baseStatsFlow,
        _isGenerating,
        _storyError
    ) { baseState, isGenerating, storyError ->
        baseState.copy(
            isStoryGenerating = isGenerating,
            storyError = storyError
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatsUiState()
        )

    fun selectYear(year: String) {
        _selectedYear.value = year
        _storyError.value = null
    }

    fun generateAiStory() {
        val currentState = uiState.value
        val year = currentState.selectedYear

        _isGenerating.value = true
        _storyError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Setup prompt instructions based on interactive slot preferences
                val toneKey = sharedPrefs.getString("ai_prompt_tone", "analytical")
                val formatKey = sharedPrefs.getString("ai_prompt_format", "bulleted")
                val perspectiveKey = sharedPrefs.getString("ai_prompt_perspective", "second_person")
                val highlightKey = sharedPrefs.getString("ai_prompt_highlight", "places_numbers")
                val emojiKey = sharedPrefs.getString("ai_prompt_emoji", "none")

                val toneDesc = when (toneKey) {
                    "narrative" -> "warm, narrative, and conversational travel log style"
                    "poetic" -> "poetic, evocative, and atmospheric travelogue style"
                    "humorous" -> "witty, lighthearted, and playful style"
                    else -> "analytical, concise, and structured style"
                }

                val formatDesc = when (formatKey) {
                    "paragraph" -> "flowing prose in a single cohesive paragraph"
                    "stanzas" -> "short distinct stanzas"
                    else -> "a bulleted list of 3-5 key highlights starting with * "
                }

                val perspectiveDesc = when (perspectiveKey) {
                    "first_person" -> "written in the first person ('I')"
                    "third_person" -> "referring to the traveler in the third person ('the traveler')"
                    else -> "addressing the reader as 'you'"
                }

                val highlightDesc = when (highlightKey) {
                    "airlines_routes" -> "Bold airlines and route pairs using **bold** syntax."
                    "dates_distances" -> "Bold flight dates and total distances using **bold** syntax."
                    else -> "Bold place names and key numbers using **bold** syntax."
                }

                val emojiDesc = when (emojiKey) {
                    "subtle" -> "Include a few tasteful travel emojis."
                    "frequent" -> "Include expressive travel emojis throughout."
                    else -> "No emojis."
                }

                val unitPrefVal = sharedPrefs.getString("preferred_unit", "km")
                val distanceVal = if (unitPrefVal == "mi") {
                    currentState.totalDistanceKm * 0.6213711922
                } else {
                    currentState.totalDistanceKm
                }
                val unitName = if (unitPrefVal == "mi") "miles" else "km"

                val isAllTime = year == "All Time" || year.equals("All", ignoreCase = true)

                // Create flight detail description for AI
                val flightsListDesc = repository.getAllFlights()
                    .filter { it.date != null && (isAllTime || it.date.startsWith(year)) }
                    .joinToString("; ") { "${it.origin} to ${it.destination} on ${it.date}" }

                val timeSpanLabel = if (isAllTime) "all-time overall flight history" else "the year $year"

                val useCustomPrompt = sharedPrefs.getBoolean("use_custom_prompt_text", false)
                val customPromptTemplate = sharedPrefs.getString("custom_prompt_text", "")

                val topAirportStr = currentState.topAirports.firstOrNull()?.let { "${it.first} (${it.third} flights)" } ?: "N/A"
                val topRouteStr = currentState.topRoutes.firstOrNull()?.let { "${it.first} (${it.second} flights)" } ?: "N/A"
                val longestFlightStr = currentState.longestFlight?.let { "${it.origin} to ${it.destination}" } ?: "N/A"

                val prompt = if (useCustomPrompt && !customPromptTemplate.isNullOrBlank()) {
                    customPromptTemplate
                        .replace("{timespan}", timeSpanLabel)
                        .replace("{count}", currentState.totalFlights.toString())
                        .replace("{distance}", "${distanceVal.toInt()} $unitName")
                        .replace("{flights}", flightsListDesc)
                        .replace("{year}", year)
                        .replace("{airports}", currentState.uniqueAirports.toString())
                        .replace("{countries}", currentState.uniqueCountries.toString())
                        .replace("{routes}", currentState.uniqueRoutes.toString())
                        .replace("{top_airport}", topAirportStr)
                        .replace("{top_route}", topRouteStr)
                        .replace("{longest_flight}", longestFlightStr)
                } else {
                    "Write a short travel summary for $timeSpanLabel, $perspectiveDesc. " +
                            "Use a $toneDesc with $formatDesc. Limit to a maximum of 8 lines of text total (under 500 characters). " +
                            "Do NOT include any title or header (do not start with # or ##). Start directly with the content. " +
                            "Stick strictly to these flight facts: ${currentState.totalFlights} flights, ${distanceVal.toInt()} $unitName total. " +
                            "Flights details: $flightsListDesc. " +
                            "$highlightDesc $emojiDesc"
                }

                val mConfig = ModelConfig.Builder().apply {
                    preference = ModelPreference.FULL
                }.build()
                val generationConfig = GenerationConfig.Builder().apply {
                    modelConfig = mConfig
                }.build()
                val generativeModel = Generation.getClient(generationConfig)
                val futures = GenerativeModelFutures.from(generativeModel)
                val responseFuture = futures.generateContent(prompt)
                val response = responseFuture.await()
                val generatedStory = response.candidates.firstOrNull()?.text ?: "No summary generated."
                
                sharedPrefs.edit().putString("saved_story_$year", generatedStory).apply()
                _isGenerating.value = false
            } catch (e: Exception) {
                _storyError.value = e.message ?: e.toString()
                _isGenerating.value = false
                val currentVersion = sharedPrefs.getInt("story_regen_version_$year", 0)
                sharedPrefs.edit()
                    .putInt("story_regen_version_$year", currentVersion + 1)
                    .remove("saved_story_$year")
                    .apply()
            }
        }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        Futures.addCallback(this, object : FutureCallback<T> {
            override fun onSuccess(result: T?) {
                if (result != null) {
                    cont.resume(result)
                } else {
                    cont.resumeWithException(NullPointerException("Result was null"))
                }
            }

            override fun onFailure(t: Throwable) {
                cont.resumeWithException(t)
            }
        }, Runnable::run)
        cont.invokeOnCancellation {
            this.cancel(true)
        }
    }
}
