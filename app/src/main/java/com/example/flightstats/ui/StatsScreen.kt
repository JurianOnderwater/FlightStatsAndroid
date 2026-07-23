package com.example.flightstats.ui

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.flightstats.CumulativePoint
import com.example.flightstats.EdgeToEdgePieView
import com.example.flightstats.FlightListItem
import com.example.flightstats.StatsUiState
import com.example.flightstats.StatsViewModel
import kotlin.math.roundToInt
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.*

data class ColorVibe(
    val primaryColor: Color,
    val cardBgColor: Color,
    val strokeColor: Color,
    val subcardBgColor: Color,
    val subcardStrokeColor: Color,
    val textColor: Color,
    val textSecondaryColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Determine dominant season
    val dominantSeason = remember(uiState) {
        val seasons = mapOf(
            "spring" to uiState.springFlights,
            "summer" to uiState.summerFlights,
            "autumn" to uiState.autumnFlights,
            "winter" to uiState.winterFlights
        )
        seasons.entries.maxByOrNull { it.value }?.key ?: "spring"
    }

    // Top country code
    val topCountry = remember(uiState) {
        uiState.topAirports.firstOrNull()?.first?.take(2)
    }

    // Dynamic color vibe
    val vibe = remember(topCountry, dominantSeason, isDarkTheme) {
        getThemeVibe(topCountry, dominantSeason, isDarkTheme)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Global Statistics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            // Year Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.availableYears.forEach { year ->
                    FilterChip(
                        selected = uiState.selectedYear == year,
                        onClick = { viewModel.selectYear(year) },
                        label = { Text(year) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (uiState.totalFlights == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No past flights for stats calculations yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Scaffold
            }

            // Stats Carousel
            StatsCarousel(uiState = uiState, vibe = vibe)

            Spacer(modifier = Modifier.height(16.dp))

            // Charts Section
            ChartsSection(uiState = uiState)

            Spacer(modifier = Modifier.height(16.dp))

            // Cumulative Flights Interactive Section
            CumulativeFlightsSection(uiState = uiState)

            Spacer(modifier = Modifier.height(16.dp))

            // Global Footprint Section (Pie chart hidden from UI)
            // GlobalFootprintSection(uiState = uiState)
            // Spacer(modifier = Modifier.height(16.dp))

            // Gemini Nano AI Travel Log Story Card
            AiStorySection(
                uiState = uiState,
                onGenerateClick = { viewModel.generateAiStory() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatsCarousel(uiState: StatsUiState, vibe: ColorVibe) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(5) { index ->
            val cardModifier = Modifier
                .width(230.dp)
                .height(170.dp)

        when (index) {
            0 -> {
                // 1. Distance Card
                CarouselCard(vibe = vibe, modifier = cardModifier) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${Math.round(uiState.totalDistanceKm)} km",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = vibe.primaryColor
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Moon Trips", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor)
                                Text(String.format(Locale.US, "%.2f×", uiState.totalDistanceKm / 384400.0), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = vibe.textColor)
                            }
                            Column {
                                Text("Circumnavigations", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor)
                                Text(String.format(Locale.US, "%.1f", uiState.totalDistanceKm / 40075.0), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = vibe.textColor)
                            }
                        }
                    }
                }
            }
            1 -> {
                // 2. Longest Flight Card
                CarouselCard(vibe = vibe, modifier = cardModifier) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("LONGEST FLIGHT", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor, fontWeight = FontWeight.Bold)
                        if (uiState.longestFlight != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(uiState.longestFlight.origin.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = vibe.primaryColor)
                                Text("→", fontSize = 18.sp, color = vibe.textSecondaryColor)
                                Text(uiState.longestFlight.destination.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = vibe.primaryColor)
                            }
                            val details = "${Math.round(uiState.longestFlight.distance)} km  ·  ${uiState.longestFlight.date ?: ""}"
                            Text(details, style = MaterialTheme.typography.bodySmall, color = vibe.textColor)
                        } else {
                            Text("No flights", style = MaterialTheme.typography.bodyMedium, color = vibe.textColor)
                        }
                    }
                }
            }
            2 -> {
                // 3. Time Aloft Card
                CarouselCard(vibe = vibe, modifier = cardModifier) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("TIME ALOFT", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor, fontWeight = FontWeight.Bold)
                        val hours = (uiState.totalDistanceKm / 800.0).toInt()
                        Text("$hours hours", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = vibe.primaryColor)
                        Text(
                            text = "${hours / 24} days in the air",
                            style = MaterialTheme.typography.bodyMedium,
                            color = vibe.textColor
                        )
                    }
                }
            }
            3 -> {
                // 4. Top Airports Card
                CarouselCard(vibe = vibe, modifier = cardModifier) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("TOP AIRPORTS", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.topAirports.take(3).forEachIndexed { index, triple ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(vibe.subcardBgColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = vibe.primaryColor, modifier = Modifier.width(16.dp))
                                    Text("${triple.first} · ${triple.second}", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = vibe.textColor, modifier = Modifier.weight(1f))
                                    Text("${triple.third}", fontWeight = FontWeight.Bold, color = vibe.textColor)
                                }
                            }
                        }
                    }
                }
            }
            4 -> {
                // 5. Top Routes Card
                CarouselCard(vibe = vibe, modifier = cardModifier) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("TOP ROUTES", style = MaterialTheme.typography.labelSmall, color = vibe.textSecondaryColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.topRoutes.take(3).forEachIndexed { index, pair ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(vibe.subcardBgColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = vibe.primaryColor, modifier = Modifier.width(16.dp))
                                    Text(pair.first, style = MaterialTheme.typography.bodySmall, color = vibe.textColor, modifier = Modifier.weight(1f))
                                    Text("${pair.second}", fontWeight = FontWeight.Bold, color = vibe.textColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun CarouselCard(
    vibe: ColorVibe,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = vibe.cardBgColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun ChartsSection(uiState: StatsUiState) {
    var selectedChartTab by remember { mutableStateOf(0) }
    val showYearlyTab = uiState.selectedYear == "All Time" || uiState.selectedYear.equals("All", ignoreCase = true)

    LaunchedEffect(showYearlyTab) {
        if (!showYearlyTab && selectedChartTab == 2) {
            selectedChartTab = 0
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Flight Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Tab bar
            TabRow(
                selectedTabIndex = selectedChartTab.coerceAtMost(if (showYearlyTab) 2 else 1),
                containerColor = Color.Transparent,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Tab(selected = selectedChartTab == 0, onClick = { selectedChartTab = 0 }) {
                    Text("Monthly", modifier = Modifier.padding(vertical = 8.dp))
                }
                Tab(selected = selectedChartTab == 1, onClick = { selectedChartTab = 1 }) {
                    Text("Day of Week", modifier = Modifier.padding(vertical = 8.dp))
                }
                if (showYearlyTab) {
                    Tab(selected = selectedChartTab == 2, onClick = { selectedChartTab = 2 }) {
                        Text("Yearly", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            val dataPoints = remember(uiState, selectedChartTab) {
                when (selectedChartTab) {
                    0 -> uiState.byMonth.mapIndexed { index, count -> Pair(getMonthLabel(index), count.toFloat()) }
                    1 -> uiState.byDay.mapIndexed { index, count -> Pair(getDayLabel(index), count.toFloat()) }
                    else -> uiState.byYear.map { Pair(it.first, it.second.toFloat()) }
                }
            }

            val primaryColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        BarChart(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            description.isEnabled = false
                            legend.isEnabled = false
                            setDrawGridBackground(false)
                            setTouchEnabled(false)

                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                setDrawGridLines(false)
                                granularity = 1f
                                textSize = 10f
                            }
                            axisLeft.apply {
                                setDrawGridLines(true)
                                axisMinimum = 0f
                            }
                            axisRight.isEnabled = false
                        }
                    },
                    update = { barChart ->
                        val entries = dataPoints.mapIndexed { idx, pair -> BarEntry(idx.toFloat(), pair.second) }
                        val dataSet = BarDataSet(entries, "Flights").apply {
                            color = primaryColor.toArgb()
                            setDrawValues(false)
                        }

                        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(dataPoints.map { it.first })
                        barChart.renderer = com.example.flightstats.RoundedBarChartRenderer(
                            barChart,
                            barChart.animator,
                            barChart.viewPortHandler,
                            6f * barChart.resources.displayMetrics.density
                        )
                        barChart.data = BarData(dataSet)
                        barChart.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CumulativeFlightsSection(uiState: StatsUiState) {
    val points = uiState.cumulativePoints
    if (points.isEmpty()) return

    var selectedIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val selectedPoint = points.getOrNull(selectedIndex) ?: points.last()

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cumulative Flight Growth",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    Text(
                        text = selectedPoint.flightInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor,
                        maxLines = 1
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryContainerColor,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${selectedPoint.count} flights",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val minTime = remember(points) { points.minOf { it.timestamp } }
                val maxTime = remember(points) { points.maxOf { it.timestamp } }
                val timeRange = remember(minTime, maxTime) { (maxTime - minTime).coerceAtLeast(1L).toFloat() }

                fun getIndexFromX(touchX: Float, width: Float): Int {
                    if (width <= 0 || points.isEmpty()) return 0
                    if (points.size == 1) return 0
                    val targetTime = minTime + ((touchX / width).coerceIn(0f, 1f) * timeRange).toLong()
                    return points.minByOrNull { Math.abs(it.timestamp - targetTime) }
                        ?.let { points.indexOf(it) } ?: 0
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                selectedIndex = getIndexFromX(offset.x, size.width.toFloat())
                            }
                        }
                        .pointerInput(points) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                selectedIndex = getIndexFromX(change.position.x, size.width.toFloat())
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    if (width <= 0 || height <= 0 || points.isEmpty()) return@Canvas

                    val maxCount = points.maxOf { it.count }.toFloat().coerceAtLeast(1f)
                    val minCount = 0f

                    val topPadding = 16f
                    val bottomPadding = 24f
                    val availableHeight = height - topPadding - bottomPadding

                    fun getX(pt: CumulativePoint): Float {
                        if (points.size == 1 || timeRange <= 0f) return width / 2f
                        val fraction = (pt.timestamp - minTime).toFloat() / timeRange
                        return fraction * width
                    }

                    fun getY(count: Int): Float {
                        val norm = (count - minCount) / (maxCount - minCount)
                        return height - bottomPadding - (norm * availableHeight)
                    }

                    // Build Timeline Path
                    val strokePath = Path()
                    val fillPath = Path()

                    val startX = getX(points[0])
                    val startY = getY(points[0].count)

                    strokePath.moveTo(startX, startY)
                    fillPath.moveTo(startX, height - bottomPadding)
                    fillPath.lineTo(startX, startY)

                    for (i in 0 until points.size - 1) {
                        val x1 = getX(points[i])
                        val y1 = getY(points[i].count)
                        val x2 = getX(points[i + 1])
                        val y2 = getY(points[i + 1].count)

                        val cx = (x1 + x2) / 2f
                        strokePath.cubicTo(cx, y1, cx, y2, x2, y2)
                        fillPath.cubicTo(cx, y1, cx, y2, x2, y2)
                    }

                    val lastX = getX(points.last())
                    fillPath.lineTo(lastX, height - bottomPadding)
                    fillPath.close()

                    // Draw Gradient Area Fill under timeline
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.35f),
                                primaryColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = topPadding,
                            endY = height - bottomPadding
                        )
                    )

                    // Draw Curved Timeline Line Stroke
                    drawPath(
                        path = strokePath,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw Active Scrubber Line & Dot
                    val selX = getX(selectedPoint)
                    val selY = getY(selectedPoint.count)

                    // Scrubber Vertical Guideline
                    drawLine(
                        color = primaryColor.copy(alpha = 0.4f),
                        start = Offset(selX, topPadding),
                        end = Offset(selX, height - bottomPadding),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Glowing Outer Dot
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = 12.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    // Inner Dot
                    drawCircle(
                        color = primaryColor,
                        radius = 6.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    // Core Center
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                }
            }
        }
    }
}

@Composable
fun GlobalFootprintSection(uiState: StatsUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Global Footprint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        EdgeToEdgePieView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { pieView ->
                        val slices = uiState.countryPercentages.map { slice ->
                            EdgeToEdgePieView.Slice(
                                slice.value,
                                getColorForCountry(slice.code),
                                slice.code,
                                slice.percentage
                            )
                        }
                        pieView.setSlices(slices)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun AiStorySection(
    uiState: StatsUiState,
    onGenerateClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${uiState.selectedYear} Gemini Travel Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isStoryGenerating) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Generating summary with Gemini Nano...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val story = uiState.aiStory
                if (story != null) {
                    MarkdownText(
                        markdown = story,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { isExpanded = !isExpanded }
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isExpanded) "Collapse" else "Expand")
                        }

                        OutlinedButton(
                            onClick = onGenerateClick
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate")
                        }
                    }
                } else {
                    Text(
                        text = "Generate an on-device AI travel log highlight using Gemini Nano.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onGenerateClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Generate Travel Log")
                    }
                }

                uiState.storyError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(markdown, primaryColor) {
        buildAnnotatedString {
            val lines = markdown.split("\n")
            lines.forEachIndexed { index, rawLine ->
                var line = rawLine.trim()
                if (line.startsWith("###")) {
                    line = line.removePrefix("###").trim()
                } else if (line.startsWith("##")) {
                    line = line.removePrefix("##").trim()
                } else if (line.startsWith("#")) {
                    line = line.removePrefix("#").trim()
                }

                val isBullet = line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ")
                if (isBullet) {
                    val content = line.substring(2).trim()
                    append("•  ")
                    line = content
                }

                // Parse **bold** formatting inside line
                val parts = line.split("**")
                parts.forEachIndexed { pIdx, part ->
                    if (pIdx % 2 == 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                            append(part)
                        }
                    } else {
                        // Parse *italic* inside non-bold part
                        val subParts = part.split("*")
                        subParts.forEachIndexed { sIdx, subPart ->
                            if (sIdx % 2 == 1) {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(subPart)
                                }
                            } else {
                                append(subPart)
                            }
                        }
                    }
                }

                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines
    )
}

private fun getMonthLabel(monthIndex: Int): String {
    return listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[monthIndex]
}

private fun getDayLabel(dayIndex: Int): String {
    return listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[dayIndex]
}

private fun getColorForCountry(code: String): Int {
    val hash = code.hashCode()
    return Color(
        red = ((hash and 0xFF0000) shr 16) / 255f * 0.4f + 0.3f,
        green = ((hash and 0x00FF00) shr 8) / 255f * 0.4f + 0.3f,
        blue = (hash and 0x0000FF) / 255f * 0.4f + 0.3f
    ).toArgb()
}

fun getThemeVibe(topCountry: String?, dominantSeason: String, isDark: Boolean): ColorVibe {
    val country = topCountry?.uppercase() ?: ""

    if (isDark) {
        return when {
            country == "NL" -> ColorVibe(
                primaryColor = Color(0xFF85B0FF),
                cardBgColor = Color(0xFF14223D),
                strokeColor = Color(0xFF2A4270),
                subcardBgColor = Color(0xFF2A1C10),
                subcardStrokeColor = Color(0xFF4F331A),
                textColor = Color(0xFFE2ECFE),
                textSecondaryColor = Color(0xFFAEC4EC)
            )
            country == "US" || country == "GB" -> ColorVibe(
                primaryColor = Color(0xFFA5B4FC),
                cardBgColor = Color(0xFF1C1B24),
                strokeColor = Color(0xFF353444),
                subcardBgColor = Color(0xFF2D1F23),
                subcardStrokeColor = Color(0xFF4C2F36),
                textColor = Color(0xFFE2E8F0),
                textSecondaryColor = Color(0xFF94A3B8)
            )
            country == "JP" -> ColorVibe(
                primaryColor = Color(0xFFFF85A1),
                cardBgColor = Color(0xFF24151B),
                strokeColor = Color(0xFF442632),
                subcardBgColor = Color(0xFF34171A),
                subcardStrokeColor = Color(0xFF582329),
                textColor = Color(0xFFFFF0F3),
                textSecondaryColor = Color(0xFFDDA6B2)
            )
            country == "FR" -> ColorVibe(
                primaryColor = Color(0xFFC7B3FF),
                cardBgColor = Color(0xFF191629),
                strokeColor = Color(0xFF322A4E),
                subcardBgColor = Color(0xFF152332),
                subcardStrokeColor = Color(0xFF253E5A),
                textColor = Color(0xFFECE7FA),
                textSecondaryColor = Color(0xFFAAA0CC)
            )
            else -> when (dominantSeason) {
                "spring" -> ColorVibe(
                    primaryColor = Color(0xFF6CDE8A),
                    cardBgColor = Color(0xFF132717),
                    strokeColor = Color(0xFF26492E),
                    subcardBgColor = Color(0xFF292113),
                    subcardStrokeColor = Color(0xFF4E3E23),
                    textColor = Color(0xFFEBF7ED),
                    textSecondaryColor = Color(0xFFA4C7AC)
                )
                "summer" -> ColorVibe(
                    primaryColor = Color(0xFFFFC760),
                    cardBgColor = Color(0xFF242211),
                    strokeColor = Color(0xFF453F1D),
                    subcardBgColor = Color(0xFF11253C),
                    subcardStrokeColor = Color(0xFF213F63),
                    textColor = Color(0xFFFFF9ED),
                    textSecondaryColor = Color(0xFFC6BEB2)
                )
                "autumn" -> ColorVibe(
                    primaryColor = Color(0xFFFF8F9D),
                    cardBgColor = Color(0xFF28181B),
                    strokeColor = Color(0xFF482B2E),
                    subcardBgColor = Color(0xFF2D1F15),
                    subcardStrokeColor = Color(0xFF513824),
                    textColor = Color(0xFFFFF1F2),
                    textSecondaryColor = Color(0xFFCAB3B5)
                )
                else -> ColorVibe( // winter
                    primaryColor = Color(0xFF82C8E5),
                    cardBgColor = Color(0xFF111E26),
                    strokeColor = Color(0xFF223642),
                    subcardBgColor = Color(0xFF1A261D),
                    subcardStrokeColor = Color(0xFF2C4232),
                    textColor = Color(0xFFEFF8FB),
                    textSecondaryColor = Color(0xFFB3C5CB)
                )
            }
        }
    } else {
        return when {
            country == "NL" -> ColorVibe(
                primaryColor = Color(0xFF003399),
                cardBgColor = Color(0xFFF0F4FC),
                strokeColor = Color(0xFFADC2EB),
                subcardBgColor = Color(0xFFFFEDE0),
                subcardStrokeColor = Color(0xFFFFC299),
                textColor = Color(0xFF001A4D),
                textSecondaryColor = Color(0xFF4D6699)
            )
            country == "US" || country == "GB" -> ColorVibe(
                primaryColor = Color(0xFF1A365D),
                cardBgColor = Color(0xFFF2F1F8),
                strokeColor = Color(0xFFCCD1E4),
                subcardBgColor = Color(0xFFFDE8E8),
                subcardStrokeColor = Color(0xFFF8B4B4),
                textColor = Color(0xFF0F172A),
                textSecondaryColor = Color(0xFF475569)
            )
            country == "JP" -> ColorVibe(
                primaryColor = Color(0xFF990033),
                cardBgColor = Color(0xFFFFF5F5),
                strokeColor = Color(0xFFFFD1D1),
                subcardBgColor = Color(0xFFFDF2F4),
                subcardStrokeColor = Color(0xFFECC4C9),
                textColor = Color(0xFF3D0012),
                textSecondaryColor = Color(0xFF7D5A61)
            )
            country == "FR" -> ColorVibe(
                primaryColor = Color(0xFF4A3E9C),
                cardBgColor = Color(0xFFF7F5FC),
                strokeColor = Color(0xFFE2DCF3),
                subcardBgColor = Color(0xFFEBF3FC),
                subcardStrokeColor = Color(0xFFBCD4F4),
                textColor = Color(0xFF1C1340),
                textSecondaryColor = Color(0xFF5D518C)
            )
            else -> when (dominantSeason) {
                "spring" -> ColorVibe(
                    primaryColor = Color(0xFF1E6F33),
                    cardBgColor = Color(0xFFF3FBF4),
                    strokeColor = Color(0xFFC6ECD0),
                    subcardBgColor = Color(0xFFFFF7EB),
                    subcardStrokeColor = Color(0xFFFFE5BF),
                    textColor = Color(0xFF0D3216),
                    textSecondaryColor = Color(0xFF4B6E53)
                )
                "summer" -> ColorVibe(
                    primaryColor = Color(0xFFC26E00),
                    cardBgColor = Color(0xFFFFFBF0),
                    strokeColor = Color(0xFFFFEBAA),
                    subcardBgColor = Color(0xFFE3F2FD),
                    subcardStrokeColor = Color(0xFFBBDEFB),
                    textColor = Color(0xFF422200),
                    textSecondaryColor = Color(0xFF6D5F4D)
                )
                "autumn" -> ColorVibe(
                    primaryColor = Color(0xFF8A2E3B),
                    cardBgColor = Color(0xFFFCF4F4),
                    strokeColor = Color(0xFFF3D5D5),
                    subcardBgColor = Color(0xFFFEF5EC),
                    subcardStrokeColor = Color(0xFFEED5BF),
                    textColor = Color(0xFF3E1117),
                    textSecondaryColor = Color(0xFF6E5659)
                )
                else -> ColorVibe( // winter
                    primaryColor = Color(0xFF1E5164),
                    cardBgColor = Color(0xFFF1F7F9),
                    strokeColor = Color(0xFFD2E3E8),
                    subcardBgColor = Color(0xFFF3F6F2),
                    subcardStrokeColor = Color(0xFFD3E0D1),
                    textColor = Color(0xFF081C24),
                    textSecondaryColor = Color(0xFF4E5E64)
                )
            }
        }
    }
}
