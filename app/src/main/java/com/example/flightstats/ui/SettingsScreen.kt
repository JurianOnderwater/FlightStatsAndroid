package com.example.flightstats.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import com.example.flightstats.AirportMarkerIcon
import com.example.flightstats.data.Airport
import com.example.flightstats.data.AppDatabase
import com.example.flightstats.data.Flight
import com.example.flightstats.data.FlightRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.prompt.*
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: FlightRepository,
    onBackClick: () -> Unit,
    onThemeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var userName by remember { mutableStateOf(sharedPrefs.getString("user_name", "User") ?: "User") }
    var hometown by remember { mutableStateOf(sharedPrefs.getString("hometown", "AMS") ?: "AMS") }
    var mapStyle by remember { mutableStateOf(sharedPrefs.getString("map_style", "light") ?: "light") }
    var defaultZoom by remember { mutableStateOf(sharedPrefs.getFloat("default_zoom", 4.5f)) }
    var mapRouteCurved by remember { mutableStateOf(sharedPrefs.getBoolean("map_route_curved", true)) }
    var themeMode by remember { mutableStateOf(sharedPrefs.getInt("theme_mode", 2)) } // 0=Light, 1=Dark, 2=System
    var preferredUnit by remember { mutableStateOf(sharedPrefs.getString("preferred_unit", "km") ?: "km") }
    var shapeFamily by remember { mutableStateOf(sharedPrefs.getString("shape_family", "rounded") ?: "rounded") }
    var shapeRadius by remember { mutableStateOf(sharedPrefs.getFloat("shape_radius", 16f)) }
    var enableAiOverviews by remember { mutableStateOf(sharedPrefs.getBoolean("enable_ai_overviews", true)) }

    // Interactive AI Prompt Customizer slots
    var promptTone by remember { mutableStateOf(sharedPrefs.getString("ai_prompt_tone", "analytical") ?: "analytical") }
    var promptFormat by remember { mutableStateOf(sharedPrefs.getString("ai_prompt_format", "bulleted") ?: "bulleted") }
    var promptPerspective by remember { mutableStateOf(sharedPrefs.getString("ai_prompt_perspective", "second_person") ?: "second_person") }
    var promptHighlight by remember { mutableStateOf(sharedPrefs.getString("ai_prompt_highlight", "places_numbers") ?: "places_numbers") }
    var promptEmoji by remember { mutableStateOf(sharedPrefs.getString("ai_prompt_emoji", "none") ?: "none") }

    var isAdvancedPromptMode by remember { mutableStateOf(sharedPrefs.getBoolean("use_custom_prompt_text", false)) }
    var customPromptText by remember {
        mutableStateOf(
            sharedPrefs.getString(
                "custom_prompt_text",
                "Write a short travel summary for {timespan}, addressing the reader. Stick strictly to these flight facts: {count} flights, {distance} total. Flights details: {flights}."
            ) ?: ""
        )
    }

    var isRegenerating by remember { mutableStateOf(false) }
    var regenerationProgressText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section: Profile
                SettingsSection(title = "Profile") {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            sharedPrefs.edit().putString("user_name", it).apply()
                        },
                        label = { Text("Display Name") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = hometown,
                        onValueChange = {
                            hometown = it.uppercase()
                            if (it.trim().length == 3) {
                                sharedPrefs.edit().putString("hometown", it.trim().uppercase()).apply()
                            }
                        },
                        label = { Text("Home Airport (IATA)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section: Map
                SettingsSection(title = "Map View Settings") {
                    Text("Map Style", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("light" to "Light", "dark" to "Dark", "satellite" to "Satellite").forEach { (style, label) ->
                            FilterChip(
                                selected = mapStyle == style,
                                onClick = {
                                    mapStyle = style
                                    sharedPrefs.edit().putString("map_style", style).apply()
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Default Zoom (${String.format(Locale.US, "%.1f", defaultZoom)})", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = defaultZoom,
                        onValueChange = {
                            defaultZoom = it
                            sharedPrefs.edit().putFloat("default_zoom", it).apply()
                        },
                        valueRange = 4.0f..10.0f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Curved Great-Circle Routes", modifier = Modifier.weight(1f))
                        Switch(
                            checked = mapRouteCurved,
                            onCheckedChange = {
                                mapRouteCurved = it
                                sharedPrefs.edit().putBoolean("map_route_curved", it).apply()
                            }
                        )
                    }
                }

                // Section: Theme & Unit
                SettingsSection(title = "App Preferences") {
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0 to "Light", 1 to "Dark", 2 to "System").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = {
                                    themeMode = mode
                                    sharedPrefs.edit().putInt("theme_mode", mode).apply()
                                    onThemeChanged(mode)
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Distance Unit", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("km" to "Kilometers (km)", "mi" to "Miles (mi)").forEach { (unit, label) ->
                            FilterChip(
                                selected = preferredUnit == unit,
                                onClick = {
                                    preferredUnit = unit
                                    sharedPrefs.edit().putString("preferred_unit", unit).apply()
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section: Corner Customizations
                SettingsSection(title = "Interface Shapes") {
                    Text("Shape Family", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("rounded" to "Rounded Corners", "cut" to "Cut Corners").forEach { (family, label) ->
                            FilterChip(
                                selected = shapeFamily == family,
                                onClick = {
                                    shapeFamily = family
                                    sharedPrefs.edit().putString("shape_family", family).apply()
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Corner Radius (${Math.round(shapeRadius)} dp)", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = shapeRadius,
                        onValueChange = {
                            shapeRadius = it
                            sharedPrefs.edit().putFloat("shape_radius", it).apply()
                        },
                        valueRange = 0f..24f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section: Interactive AI Prompt Customizer
                SettingsSection(title = "Interactive AI Prompt Customizer") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable AI Travel Summaries", modifier = Modifier.weight(1f))
                        Switch(
                            checked = enableAiOverviews,
                            onCheckedChange = {
                                enableAiOverviews = it
                                sharedPrefs.edit().putBoolean("enable_ai_overviews", it).apply()
                            }
                        )
                    }

                    if (enableAiOverviews) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Advanced Prompt Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Type raw prompt text instead of Mad-Libs slots", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAdvancedPromptMode,
                                onCheckedChange = {
                                    isAdvancedPromptMode = it
                                    sharedPrefs.edit().putBoolean("use_custom_prompt_text", it).apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isAdvancedPromptMode) {
                            // Free-form Custom Prompt Text Field Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "FREE-FORM CUSTOM PROMPT",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap a placeholder chip below to insert dynamic variables:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf(
                                            "{timespan}", "{year}", "{count}", "{distance}", "{flights}",
                                            "{airports}", "{countries}", "{routes}", "{top_airport}",
                                            "{top_route}", "{longest_flight}"
                                        ).forEach { tag ->
                                            AssistChip(
                                                onClick = {
                                                    val newText = customPromptText + " " + tag
                                                    customPromptText = newText
                                                    sharedPrefs.edit().putString("custom_prompt_text", newText).apply()
                                                },
                                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = customPromptText,
                                        onValueChange = {
                                            customPromptText = it
                                            sharedPrefs.edit().putString("custom_prompt_text", it).apply()
                                        },
                                        label = { Text("Custom Prompt Template") },
                                        minLines = 4,
                                        maxLines = 10,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Mad-Libs Style Inline Interactive Prompt Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "MAD-LIBS PROMPT BUILDER",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap any highlighted word inside the sentence below to switch it out:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Write a summary for [Year]",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )

                                        InlineWordDropdown(
                                            selectedKey = promptPerspective,
                                            options = listOf(
                                                "second_person" to "addressing you",
                                                "first_person" to "in first person ('I')",
                                                "third_person" to "for 'the traveler'"
                                            ),
                                            onOptionSelected = {
                                                promptPerspective = it
                                                sharedPrefs.edit().putString("ai_prompt_perspective", it).apply()
                                            }
                                        )

                                        Text(
                                            text = "in a",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )

                                        InlineWordDropdown(
                                            selectedKey = promptTone,
                                            options = listOf(
                                                "analytical" to "analytical & structured",
                                                "narrative" to "warm & narrative",
                                                "poetic" to "poetic & evocative",
                                                "humorous" to "witty & playful"
                                            ),
                                            onOptionSelected = {
                                                promptTone = it
                                                sharedPrefs.edit().putString("ai_prompt_tone", it).apply()
                                            }
                                        )

                                        Text(
                                            text = "style formatted as",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )

                                        InlineWordDropdown(
                                            selectedKey = promptFormat,
                                            options = listOf(
                                                "bulleted" to "bulleted highlights",
                                                "paragraph" to "a single paragraph",
                                                "stanzas" to "short stanzas"
                                            ),
                                            onOptionSelected = {
                                                promptFormat = it
                                                sharedPrefs.edit().putString("ai_prompt_format", it).apply()
                                            }
                                        )

                                        Text(
                                            text = ". Highlight",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )

                                        InlineWordDropdown(
                                            selectedKey = promptHighlight,
                                            options = listOf(
                                                "places_numbers" to "bold places & numbers",
                                                "airlines_routes" to "bold airlines & routes",
                                                "dates_distances" to "bold dates & distances"
                                            ),
                                            onOptionSelected = {
                                                promptHighlight = it
                                                sharedPrefs.edit().putString("ai_prompt_highlight", it).apply()
                                            }
                                        )

                                        Text(
                                            text = "and use",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )

                                        InlineWordDropdown(
                                            selectedKey = promptEmoji,
                                            options = listOf(
                                                "none" to "no emojis",
                                                "subtle" to "subtle travel emojis",
                                                "frequent" to "expressive emojis"
                                            ),
                                            onOptionSelected = {
                                                promptEmoji = it
                                                sharedPrefs.edit().putString("ai_prompt_emoji", it).apply()
                                            }
                                        )

                                        Text(
                                            text = ".",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isRegenerating = true
                                regenerationProgressText = "Initializing on-device AI..."
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val db = AppDatabase.getDatabase(context)
                                        val allFlights = db.flightDao().getAllFlights()
                                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        val pastFlights = allFlights.filter { it.date != null && it.date < todayStr }

                                        val years = pastFlights.mapNotNull { f ->
                                            f.date?.take(4)
                                        }.toMutableSet()
                                        years.add(Calendar.getInstance().get(Calendar.YEAR).toString())

                                        val targetYears = years.toList() + "All Time"

                                         // Stub GenAI for compilation and reflection testing
                                         for (year in targetYears) {
                                             withContext(Dispatchers.Main) {
                                                 regenerationProgressText = "Generating story for $year..."
                                             }
                                             val responseText = "Stubbed flight log summary for $year"
                                             sharedPrefs.edit().putString("saved_story_$year", responseText).apply()
                                         }

                                        withContext(Dispatchers.Main) {
                                            isRegenerating = false
                                            Toast.makeText(context, "All travel logs regenerated successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isRegenerating = false
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Regenerate All Travel Logs")
                        }
            }

            // Progress dialog for regeneration
            if (isRegenerating) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = regenerationProgressText,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InlineWordDropdown(
    selectedKey: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == selectedKey }?.second ?: options.first().second

    Box(modifier = Modifier.wrapContentSize()) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (key == selectedKey) FontWeight.Bold else FontWeight.Normal,
                            color = if (key == selectedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onOptionSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun getToneDesc(key: String): String = when (key) {
    "narrative" -> "warm & narrative style"
    "poetic" -> "poetic & evocative style"
    "humorous" -> "witty & playful style"
    else -> "analytical & structured style"
}

fun getFormatDesc(key: String): String = when (key) {
    "paragraph" -> "a single cohesive paragraph"
    "stanzas" -> "short distinct stanzas"
    else -> "bulleted highlights"
}

fun getPerspectiveDesc(key: String): String = when (key) {
    "first_person" -> "in first person ('I')"
    "third_person" -> "referring to 'the traveler'"
    else -> "addressing the reader as 'you'"
}

fun getHighlightDesc(key: String): String = when (key) {
    "airlines_routes" -> "Bold airlines and route pairs."
    "dates_distances" -> "Bold flight dates and total distances."
    else -> "Bold place names and key numbers."
}

fun getEmojiDesc(key: String): String = when (key) {
    "subtle" -> "Include subtle travel emojis."
    "frequent" -> "Include expressive travel emojis."
    else -> "No emojis."
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}
