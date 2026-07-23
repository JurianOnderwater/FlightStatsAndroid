package com.example.flightstats.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flightstats.AirportMarkerIcon
import com.example.flightstats.GeodesicHelper
import com.example.flightstats.MapViewModel
import com.example.flightstats.R
import com.example.flightstats.SelectedAirportInfo
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val CARTO_VOYAGER = org.osmdroid.tileprovider.tilesource.XYTileSource(
    "CartoDB_Voyager",
    1, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    )
)

private val CARTO_DARK = org.osmdroid.tileprovider.tilesource.XYTileSource(
    "CartoDB_Dark",
    1, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    )
)

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onAddFlightClick: () -> Unit,
    onScanFlightClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    val animatorRef = remember { java.util.concurrent.atomic.AtomicReference<ValueAnimator?>(null) }
    var lastDrawnKey by remember { mutableStateOf("") }

    // Trigger preference reload when returning to screen (onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPrefs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            animatorRef.getAndSet(null)?.cancel()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // OsmDroid Map wrapper
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 3.2
                    maxZoomLevel = 18.0
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    setScrollableAreaLimitLatitude(85.0, -85.0, 0)
                }
            },
            update = { mapView ->
                mapView.setBackgroundColor(if (uiState.mapStyle == "dark") 0xFF1E1E1E.toInt() else 0xFFF2EFE9.toInt())

                val currentKey = "${uiState.mapStyle}_${uiState.selectedYear}_${uiState.routes.size}"
                if (currentKey != lastDrawnKey) {
                    lastDrawnKey = currentKey

                    mapView.overlays.clear()

                    // Style
                    if (uiState.mapStyle == "satellite") {
                        mapView.setTileSource(TileSourceFactory.USGS_SAT)
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    } else if (uiState.mapStyle == "dark") {
                        mapView.setTileSource(CARTO_DARK)
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    } else {
                        mapView.setTileSource(CARTO_VOYAGER)
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }

                    // Initial position
                    mapView.controller.setZoom(uiState.defaultZoom.toDouble())
                    mapView.controller.setCenter(uiState.centerPoint)

                    val routePolylines = mutableListOf<Polyline>()
                    val allRoutePoints = mutableListOf<List<GeoPoint>>()
                    val airportMarkers = mutableListOf<Marker>()

                    val primaryColorInt = primaryColor.toArgb()
                    val routeColor = (0xCC000000.toInt() and 0xFF000000.toInt()) or (primaryColorInt and 0x00FFFFFF)

                    // Add routes
                    for (route in uiState.routes) {
                        val points = if (uiState.isRouteCurved) {
                            GeodesicHelper.greatCircleArc(route.fromPoint, route.toPoint, 64)
                        } else {
                            listOf(route.fromPoint, route.toPoint)
                        }

                        val line = Polyline(mapView).apply {
                            color = routeColor
                            width = 2.5f
                            title = "${route.fromIata} → ${route.toIata}"
                            setPoints(emptyList()) // Start empty for animation
                        }
                        mapView.overlays.add(line)
                        routePolylines.add(line)
                        allRoutePoints.add(points)
                    }

                    // Add markers (deduplicated)
                    val drawnAirports = mutableSetOf<String>()
                    val markerPrimaryColor = primaryColor.toArgb()
                    val markerOnPrimaryColor = onPrimaryColor.toArgb()

                    for (route in uiState.routes) {
                        listOf(
                            Pair(route.fromPoint, route.fromIata),
                            Pair(route.toPoint, route.toIata)
                        ).forEach { (point, iata) ->
                            if (!drawnAirports.contains(iata)) {
                                drawnAirports.add(iata)
                                val marker = Marker(mapView).apply {
                                    position = point
                                    title = iata
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = AirportMarkerIcon.create(context, iata, markerPrimaryColor, markerOnPrimaryColor)
                                    alpha = 0f // Start transparent for fade-in animation
                                    infoWindow = null
                                    setOnMarkerClickListener { _, _ ->
                                        viewModel.selectAirport(iata)
                                        mapView.controller.animateTo(point)
                                        true
                                    }
                                }
                                mapView.overlays.add(marker)
                                airportMarkers.add(marker)
                            }
                        }
                    }

                    // Route draw animation
                    animatorRef.getAndSet(null)?.cancel()
                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 1800L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { animation ->
                            val fraction = animation.animatedValue as Float

                            if (animatorRef.get() == null) return@addUpdateListener

                            // Draw lines progressively
                            for (i in routePolylines.indices) {
                                if (i >= allRoutePoints.size || i >= routePolylines.size) continue
                                val line = routePolylines[i]
                                val fullPoints = allRoutePoints[i]
                                val count = Math.max(2, (fullPoints.size * fraction).toInt())
                                if (count <= fullPoints.size) {
                                    line.setPoints(fullPoints.subList(0, count))
                                } else {
                                    line.setPoints(fullPoints)
                                }
                            }

                            // Fade in markers
                            for (marker in airportMarkers) {
                                marker.alpha = fraction
                            }

                            mapView.invalidate()
                        }
                        start()
                    }
                    animatorRef.set(animator)

                    mapView.invalidate()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar Row (Year Filter Pills + Profile Badge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 48.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.availableYears) { year ->
                    val selected = year == uiState.selectedYear
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectYear(year) },
                        label = {
                            Text(
                                text = year,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { showProfileDialog = true }
            ) {
                Text(
                    text = uiState.userInitial,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Material 3 Expressive Selected Airport Info Sheet
        val selectedInfo = uiState.selectedAirportInfo
        AnimatedVisibility(
            visible = selectedInfo != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            selectedInfo?.let { info ->
                SelectedAirportSheet(
                    info = info,
                    onDismiss = { viewModel.clearSelectedAirport() }
                )
            }
        }
    }

    // Profile Card dialog
    if (showProfileDialog) {
        Dialog(onDismissRequest = { showProfileDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Badge Initial
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = uiState.userInitial,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.userName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Home Airport: ${uiState.hometownIata}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = {
                            showProfileDialog = false
                            onSettingsClick()
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Divider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Profile Stats Summary row
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProfileStatItem(value = uiState.totalFlights.toString(), label = "Flights")
                        ProfileStatItem(value = uiState.totalCountries.toString(), label = "Countries")
                        ProfileStatItem(value = uiState.totalAirports.toString(), label = "Airports")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showProfileDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SelectedAirportSheet(
    info: SelectedAirportInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = info.airport.iata,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = info.airport.name.orEmpty().ifEmpty { info.airport.iata },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = listOfNotNull(info.airport.city, info.airport.country).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-bar Stats
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.title_flight_pairs_history),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = stringResource(R.string.label_flights_total, info.totalFlights),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Flight Pairs List
            if (info.recentChronologicalFlights.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_airport_flights),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(info.recentChronologicalFlights) { flight ->
                        val origin = flight.origin.orEmpty().lowercase()
                        val dest = flight.destination.orEmpty().lowercase()
                        val pairLabel = "$origin-$dest"
                        val formattedRoute = "${flight.origin.orEmpty()} → ${flight.destination.orEmpty()}"

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.width(140.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = pairLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = formattedRoute,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                flight.date?.let { dateStr ->
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
