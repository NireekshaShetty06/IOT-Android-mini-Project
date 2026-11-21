package com.smartumbrella.app.ui.screens.planner

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartumbrella.app.data.model.HourlyForecastPoint
import com.smartumbrella.app.data.model.OutingWeatherAnalysis
import com.smartumbrella.app.data.model.RecommendationCategory
import com.smartumbrella.app.data.model.UmbrellaRecommendation
import com.smartumbrella.app.ui.theme.BorderSubtle
import com.smartumbrella.app.ui.theme.NormalGreen
import com.smartumbrella.app.ui.theme.NormalGreenBg
import com.smartumbrella.app.ui.theme.NormalGreenBorder
import com.smartumbrella.app.ui.theme.PrimaryTeal
import com.smartumbrella.app.ui.theme.PrimaryTealDark
import com.smartumbrella.app.ui.theme.RainRed
import com.smartumbrella.app.ui.theme.RainRedBg
import com.smartumbrella.app.ui.theme.RainRedBorder
import com.smartumbrella.app.ui.theme.SoftBackground
import com.smartumbrella.app.ui.theme.SurfaceWhite
import com.smartumbrella.app.ui.theme.TealAccent
import com.smartumbrella.app.ui.theme.TextMuted
import com.smartumbrella.app.ui.theme.TextNavyPrimary
import com.smartumbrella.app.ui.theme.TextSecondary
import com.smartumbrella.app.ui.theme.WarningOrange
import com.smartumbrella.app.ui.theme.WarningOrangeBg
import com.smartumbrella.app.ui.theme.WarningOrangeBorder
import com.smartumbrella.app.viewmodel.DashboardViewModel
import com.smartumbrella.app.viewmodel.OutingPlannerViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingPlannerScreen(
    dashboardViewModel: DashboardViewModel,
    plannerViewModel: OutingPlannerViewModel = viewModel(),
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val plannerState by plannerViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission launcher for GPS location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            plannerViewModel.useCurrentLocation()
        }
    }

    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryTeal.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Smart Outing Planner",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextNavyPrimary
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextNavyPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftBackground)
            )
        },
        containerColor = SoftBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Contextual Hardware Status Card (Strictly separate from forecast)
            HardwareContextCard(
                isSimulationMode = dashboardState.config.simulationMode,
                isDeviceOnline = dashboardState.isOnline,
                rainDetected = dashboardState.status.rainDetected,
                temperature = dashboardState.status.temperature,
                humidity = dashboardState.status.humidity
            )

            // 2. Location Selection Card
            LocationSelectionCard(
                selectedLocation = plannerState.selectedLocation,
                searchQuery = plannerState.searchQuery,
                searchResults = plannerState.searchResults,
                isSearching = plannerState.isSearchingLocation,
                onQueryChanged = { plannerViewModel.updateSearchQuery(it) },
                onLocationSelected = { plannerViewModel.selectLocation(it) },
                onUseGpsClicked = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            // 3. Outing Duration Card (Date & Time Range)
            OutingDurationCard(
                startDate = plannerState.startDate,
                startTime = plannerState.startTime,
                endDate = plannerState.endDate,
                endTime = plannerState.endTime,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                onPickStartDate = {
                    val cal = Calendar.getInstance()
                    cal.set(plannerState.startDate.year, plannerState.startDate.monthValue - 1, plannerState.startDate.dayOfMonth)
                    DatePickerDialog(context, { _, year, month, day ->
                        plannerViewModel.setStartDate(LocalDate.of(year, month + 1, day))
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                onPickStartTime = {
                    TimePickerDialog(context, { _, hour, minute ->
                        plannerViewModel.setStartTime(LocalTime.of(hour, minute))
                    }, plannerState.startTime.hour, plannerState.startTime.minute, false).show()
                },
                onPickEndDate = {
                    val cal = Calendar.getInstance()
                    cal.set(plannerState.endDate.year, plannerState.endDate.monthValue - 1, plannerState.endDate.dayOfMonth)
                    DatePickerDialog(context, { _, year, month, day ->
                        plannerViewModel.setEndDate(LocalDate.of(year, month + 1, day))
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                onPickEndTime = {
                    TimePickerDialog(context, { _, hour, minute ->
                        plannerViewModel.setEndTime(LocalTime.of(hour, minute))
                    }, plannerState.endTime.hour, plannerState.endTime.minute, false).show()
                }
            )

            // 4. Action Button
            Button(
                onClick = { plannerViewModel.checkUmbrellaRequirement() },
                enabled = !plannerState.isLoadingForecast,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryTeal,
                    contentColor = SurfaceWhite
                )
            ) {
                if (plannerState.isLoadingForecast) {
                    CircularProgressIndicator(
                        color = SurfaceWhite,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Analyzing Open-Meteo Forecast...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Umbrella,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check Umbrella Requirement",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Error Display (if any)
            plannerState.errorMessage?.let { errorMsg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RainRedBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = RainRedBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = RainRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = RainRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { plannerViewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = RainRed
                            )
                        }
                    }
                }
            }

            // 5. Results Section (Generated once analysis completes)
            plannerState.analysisResult?.let { analysis ->
                plannerState.recommendation?.let { recommendation ->
                    OutingResultsSection(
                        analysis = analysis,
                        recommendation = recommendation
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =====================================================================================
// COMPONENT: HARDWARE CONTEXT CARD
// =====================================================================================
@Composable
private fun HardwareContextCard(
    isSimulationMode: Boolean,
    isDeviceOnline: Boolean,
    rainDetected: Boolean,
    temperature: Float?,
    humidity: Float?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Hardware Context",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextNavyPrimary
                )

                // Clear badge distinguishing mode
                when {
                    isSimulationMode -> {
                        BadgePill(
                            text = "SIMULATION MODE",
                            bgColor = WarningOrangeBg,
                            textColor = WarningOrange,
                            borderColor = WarningOrangeBorder
                        )
                    }
                    isDeviceOnline -> {
                        BadgePill(
                            text = "ESP32 ONLINE",
                            bgColor = NormalGreenBg,
                            textColor = NormalGreen,
                            borderColor = NormalGreenBorder
                        )
                    }
                    else -> {
                        BadgePill(
                            text = "ESP32 OFFLINE",
                            bgColor = Color(0xFFF1F5F9),
                            textColor = TextMuted,
                            borderColor = BorderSubtle
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rain Sensor State
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = if (rainDetected) RainRed else NormalGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (rainDetected) "Rain Active" else "No Rain",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (rainDetected) RainRed else NormalGreen
                    )
                }

                // Temp
                temperature?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$it°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Humidity
                humidity?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hum: $it%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Text(
                text = "Note: Hardware sensor reflects conditions at the physical umbrella right now, separate from your destination's future forecast.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                lineHeight = 14.sp
            )
        }
    }
}

// =====================================================================================
// COMPONENT: LOCATION SELECTION CARD
// =====================================================================================
@Composable
private fun LocationSelectionCard(
    selectedLocation: com.smartumbrella.app.data.model.LocationCoordinate?,
    searchQuery: String,
    searchResults: List<com.smartumbrella.app.data.model.LocationCoordinate>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onLocationSelected: (com.smartumbrella.app.data.model.LocationCoordinate) -> Unit,
    onUseGpsClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "1. Select Outing Destination",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextNavyPrimary
            )

            // Current Location Button
            OutlinedButton(
                onClick = onUseGpsClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Use Current Location (GPS)")
            }

            // Manual Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Or enter city/town (e.g. Nitte, Udupi)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PrimaryTeal
                    )
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryTeal,
                    unfocusedBorderColor = BorderSubtle
                )
            )

            // Search results list
            if (searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SoftBackground)
                ) {
                    Column {
                        searchResults.forEach { loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLocationSelected(loc) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = loc.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextNavyPrimary
                                    )
                                    Text(
                                        text = loc.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selected Location Summary Pill
            selectedLocation?.let { loc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryTeal.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Target: ${loc.displayName} (${String.format("%.2f", loc.latitude)}, ${String.format("%.2f", loc.longitude)})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = PrimaryTealDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =====================================================================================
// COMPONENT: OUTING DURATION CARD
// =====================================================================================
@Composable
private fun OutingDurationCard(
    startDate: LocalDate,
    startTime: LocalTime,
    endDate: LocalDate,
    endTime: LocalTime,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onPickStartDate: () -> Unit,
    onPickStartTime: () -> Unit,
    onPickEndDate: () -> Unit,
    onPickEndTime: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "2. Outing Duration & Time Window",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextNavyPrimary
            )

            // Start Window
            Text(text = "Outing Start:", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPickStartDate,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = startDate.format(dateFormatter), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onPickStartTime,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = startTime.format(timeFormatter), fontSize = 13.sp)
                }
            }

            // End Window
            Text(text = "Outing End:", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPickEndDate,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = endDate.format(dateFormatter), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onPickEndTime,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = endTime.format(timeFormatter), fontSize = 13.sp)
                }
            }

            if (startDate != endDate) {
                Text(
                    text = "Note: Outing crosses midnight into the next day.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryTealDark
                )
            }
        }
    }
}

// =====================================================================================
// COMPONENT: OUTING RESULTS SECTION
// =====================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutingResultsSection(
    analysis: OutingWeatherAnalysis,
    recommendation: UmbrellaRecommendation
) {
    val (cardBg, borderColor, iconColor, statusIcon) = when (recommendation.category) {
        RecommendationCategory.UMBRELLA_RECOMMENDED -> {
            Tuple4(RainRedBg, RainRedBorder, RainRed, Icons.Default.Warning)
        }
        RecommendationCategory.CARRY_JUST_IN_CASE -> {
            Tuple4(WarningOrangeBg, WarningOrangeBorder, WarningOrange, Icons.Default.Info)
        }
        RecommendationCategory.UMBRELLA_NOT_REQUIRED -> {
            Tuple4(NormalGreenBg, NormalGreenBorder, NormalGreen, Icons.Default.CheckCircle)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section Header
        Text(
            text = "SMART OUTING REPORT",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = TextNavyPrimary
        )

        // Recommendation Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, borderColor, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = recommendation.title.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = iconColor
                    )
                }

                Text(
                    text = recommendation.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextNavyPrimary
                )

                Text(
                    text = recommendation.forecastSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Outing Summary Metrics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Outing Summary",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextNavyPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Destination:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(
                        text = analysis.location.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextNavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Window:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(
                        text = analysis.formattedDuration,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextNavyPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Peak Rain Chance:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(
                        text = "${analysis.maxPrecipitationProbability}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (analysis.maxPrecipitationProbability >= 60) RainRed else if (analysis.maxPrecipitationProbability >= 30) WarningOrange else NormalGreen
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total Expected Rain:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(
                        text = "${String.format("%.2f", analysis.totalPrecipitationMm)} mm",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextNavyPrimary
                    )
                }
            }
        }

        // Actual Hourly Forecast Breakdown (No fabricated sub-hourly data)
        Text(
            text = "Hourly Forecast Intervals (Open-Meteo)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = TextNavyPrimary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analysis.hourlyForecasts.forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (point.precipitationProbability >= 50) RainRedBg else SoftBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = point.formattedHour,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TextNavyPrimary
                            )
                            Text(
                                text = point.weatherDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${point.precipitationProbability}%",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (point.precipitationProbability >= 60) RainRed else if (point.precipitationProbability >= 30) WarningOrange else NormalGreen
                                )
                            )
                            if (point.precipitationMm > 0.05f) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${point.precipitationMm} mm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
                Text(
                    text = "Forecast updated: ${sdf.format(Date(analysis.fetchedAtMillis))} via Open-Meteo API",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, bgColor: Color, textColor: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = textColor
        )
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
