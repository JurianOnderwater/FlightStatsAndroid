package com.example.flightstats.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flightstats.R
import com.example.flightstats.FlightListItem
import com.example.flightstats.FlightsUiState
import com.example.flightstats.FlightsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightsScreen(
    viewModel: FlightsViewModel,
    onAddFlightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.showUpcoming) stringResource(R.string.title_upcoming) else stringResource(R.string.title_past),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    Button(
                        onClick = { viewModel.toggleShowUpcoming() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (uiState.showUpcoming) stringResource(R.string.title_past) else stringResource(R.string.title_upcoming))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddFlightClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.flights.isEmpty()) {
                Text(
                    text = if (uiState.showUpcoming) stringResource(R.string.empty_upcoming) else stringResource(R.string.empty_past),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            } else {
                var expandedCardId by remember { mutableStateOf<Int?>(null) }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.flights, key = { it.id }) { item ->
                        FlightCard(
                            item = item,
                            isExpanded = expandedCardId == item.id,
                            onCardClick = {
                                expandedCardId = if (expandedCardId == item.id) null else item.id
                            },
                            onSaveClick = { airline, flightNumber, seat, seatClass, notes, depTime, arrTime ->
                                viewModel.updateFlight(
                                    item.id,
                                    airline,
                                    flightNumber,
                                    seat,
                                    seatClass,
                                    notes,
                                    depTime,
                                    arrTime
                                )
                                expandedCardId = null
                            },
                            onDeleteClick = {
                                viewModel.deleteFlight(item.id)
                                expandedCardId = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightCard(
    item: FlightListItem,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onSaveClick: (String, String, String, String, String, String, String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotate")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Collapsed Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Origin Flag & Code
                Text(
                    text = FlightListItem.countryToFlag(item.originCountry),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = item.origin,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Airplane/Arrow indicator
                Text(
                    text = " → ",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Destination Flag & Code
                Text(
                    text = FlightListItem.countryToFlag(item.destCountry),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = item.destination,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // Distance
                if (item.distance > 0) {
                    Text(
                        text = "${Math.round(item.distance)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // Expand Indicator
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotationState)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle: Date & Times
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = getRelativeDateString(item.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${item.departureTime ?: "—:—"}  ·  ${item.arrivalTime ?: "—:—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    val originCity = item.originCity ?: item.origin
                    val destCity = item.destCity ?: item.destination
                    Text(
                        text = "$originCity to $destCity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var airline by remember { mutableStateOf(item.airline ?: "") }
                    var flightNum by remember { mutableStateOf(item.flightNumber ?: "") }
                    var seat by remember { mutableStateOf(item.seat ?: "") }
                    var seatClass by remember { mutableStateOf(item.seatClass ?: "") }
                    var notes by remember { mutableStateOf(item.notes ?: "") }
                    var depTime by remember { mutableStateOf(item.departureTime ?: "") }
                    var arrTime by remember { mutableStateOf(item.arrivalTime ?: "") }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = airline,
                            onValueChange = { airline = it },
                            label = { Text(stringResource(R.string.label_airline)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = flightNum,
                            onValueChange = { flightNum = it },
                            label = { Text(stringResource(R.string.label_flight_no)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = seat,
                            onValueChange = { seat = it },
                            label = { Text(stringResource(R.string.label_seat)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f)
                        )

                        // Dropdown for Seat Class
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = seatClass,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.label_class)) },
                                trailingIcon = {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, "Class Dropdown")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.4f)
                            ) {
                                listOf("Economy", "Premium Economy", "Business", "First").forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text(cls) },
                                        onClick = {
                                            seatClass = cls
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = depTime,
                            onValueChange = { depTime = it },
                            label = { Text(stringResource(R.string.label_dep_time)) },
                            placeholder = { Text("HH:MM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = arrTime,
                            onValueChange = { arrTime = it },
                            label = { Text(stringResource(R.string.label_arr_time)) },
                            placeholder = { Text("HH:MM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.label_notes)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                onSaveClick(airline, flightNum, seat, seatClass, notes, depTime, arrTime)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_save))
                        }

                        OutlinedButton(
                            onClick = onDeleteClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}

fun getRelativeDateString(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val flightDate = sdf.parse(dateStr) ?: return dateStr

        val formatOut = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val absoluteDate = formatOut.format(flightDate)

        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calFlight = Calendar.getInstance().apply {
            time = flightDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = calFlight.timeInMillis - calToday.timeInMillis
        val diffDays = diffMs / (24 * 60 * 60 * 1000)

        val relative = when {
            diffDays == 0L -> "Today"
            diffDays == 1L -> "Tomorrow"
            diffDays == -1L -> "Yesterday"
            diffDays > 1L -> {
                when {
                    diffDays < 7 -> "In $diffDays days"
                    diffDays < 30 -> {
                        val weeks = diffDays / 7
                        "In $weeks " + (if (weeks == 1L) "week" else "weeks")
                    }
                    diffDays < 365 -> {
                        val months = diffDays / 30
                        "In $months " + (if (months == 1L) "month" else "months")
                    }
                    else -> {
                        val years = diffDays / 365
                        "In $years " + (if (years == 1L) "year" else "years")
                    }
                }
            }
            else -> {
                val absDays = Math.abs(diffDays)
                when {
                    absDays < 7 -> "$absDays days ago"
                    absDays < 30 -> {
                        val weeks = absDays / 7
                        "$weeks " + (if (weeks == 1L) "week" else "weeks") + " ago"
                    }
                    absDays < 365 -> {
                        val months = absDays / 30
                        "$months " + (if (months == 1L) "month" else "months") + " ago"
                    }
                    else -> {
                        val years = absDays / 365
                        "$years " + (if (years == 1L) "year" else "years") + " ago"
                    }
                }
            }
        }

        "$relative · $absoluteDate"
    } catch (e: Exception) {
        dateStr
    }
}
