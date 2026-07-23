package com.example.flightstats.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.flightstats.BarcodeScannerActivity
import com.example.flightstats.FlightsViewModel
import com.example.flightstats.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlightSheet(
    viewModel: FlightsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var flightNumber by remember { mutableStateOf("") }
    var airline by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf("") }
    var seatClass by remember { mutableStateOf("Economy") }
    var notes by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("") }
    var arrivalTime by remember { mutableStateOf("") }

    var originError by remember { mutableStateOf<String?>(null) }
    var destError by remember { mutableStateOf<String?>(null) }

    // Date Picker Dialog setup
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Barcode scanner launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            origin = data.getStringExtra(BarcodeScannerActivity.EXTRA_ORIGIN) ?: origin
            destination = data.getStringExtra(BarcodeScannerActivity.EXTRA_DESTINATION) ?: destination
            airline = data.getStringExtra(BarcodeScannerActivity.EXTRA_AIRLINE) ?: airline
            flightNumber = data.getStringExtra(BarcodeScannerActivity.EXTRA_FLIGHT_NUM) ?: flightNumber
            seat = data.getStringExtra(BarcodeScannerActivity.EXTRA_SEAT) ?: seat

            data.getStringExtra(BarcodeScannerActivity.EXTRA_DATE)?.let { d ->
                if (d.isNotEmpty()) {
                    date = d
                }
            }

            data.getStringExtra(BarcodeScannerActivity.EXTRA_CLASS)?.let { cls ->
                if (cls.isNotEmpty()) {
                    seatClass = cls
                }
            }

            val scannedFlightNum = data.getStringExtra(BarcodeScannerActivity.EXTRA_FLIGHT_NUM) ?: ""
            Toast.makeText(context, "Scanned flight $scannedFlightNum", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding() // Avoid keyboard covering fields
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.action_add),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Scan Boarding Pass Button
            Button(
                onClick = {
                    scannerLauncher.launch(Intent(context, BarcodeScannerActivity::class.java))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(stringResource(R.string.action_scan))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date Field
        OutlinedTextField(
            value = date,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_date)) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            enabled = false, // Prevents focus, enables click to bubble up to parent box/modifier
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = origin,
                onValueChange = {
                    origin = it
                    originError = null
                },
                label = { Text("Origin (IATA)") },
                isError = originError != null,
                supportingText = originError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = destination,
                onValueChange = {
                    destination = it
                    destError = null
                },
                label = { Text("Destination (IATA)") },
                isError = destError != null,
                supportingText = destError?.let { { Text(it) } },
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
                value = airline,
                onValueChange = { airline = it },
                label = { Text(stringResource(R.string.label_airline)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = flightNumber,
                onValueChange = { flightNumber = it },
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
                    modifier = Modifier.fillMaxWidth(0.43f)
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
                value = departureTime,
                onValueChange = { departureTime = it },
                label = { Text(stringResource(R.string.label_dep_time)) },
                placeholder = { Text("HH:MM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = arrivalTime,
                onValueChange = { arrivalTime = it },
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

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (origin.trim().length < 3) {
                        originError = "Required (IATA)"
                        return@Button
                    }
                    if (destination.trim().length < 3) {
                        destError = "Required (IATA)"
                        return@Button
                    }

                    viewModel.insertFlight(
                        origin = origin,
                        destination = destination,
                        date = date,
                        flightNumber = flightNumber,
                        airline = airline,
                        seat = seat,
                        seatClass = seatClass,
                        notes = notes,
                        departureTime = departureTime,
                        arrivalTime = arrivalTime,
                        onSuccess = {
                            onDismiss()
                        },
                        onFailure = { error ->
                            if (error.contains("origin", ignoreCase = true)) {
                                originError = error
                            } else {
                                destError = error
                            }
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_save))
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
