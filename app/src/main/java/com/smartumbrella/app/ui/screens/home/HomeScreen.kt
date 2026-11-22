package com.smartumbrella.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartumbrella.app.ui.components.DeviceConnectionBadge
import com.smartumbrella.app.ui.components.HardwareStatusCard
import com.smartumbrella.app.ui.components.MetricCard
import com.smartumbrella.app.ui.components.OutingPlannerCard
import com.smartumbrella.app.ui.components.RainSensorCard
import com.smartumbrella.app.ui.components.RainWarningBanner
import com.smartumbrella.app.ui.components.WeatherStatusCard
import com.smartumbrella.app.ui.theme.NormalGreen
import com.smartumbrella.app.ui.theme.NormalGreenBg
import com.smartumbrella.app.ui.theme.PrimaryTeal
import com.smartumbrella.app.ui.theme.RainRed
import com.smartumbrella.app.ui.theme.SoftBackground
import com.smartumbrella.app.ui.theme.SurfaceWhite
import com.smartumbrella.app.ui.theme.TealAccent
import com.smartumbrella.app.ui.theme.TextMuted
import com.smartumbrella.app.ui.theme.TextNavyPrimary
import com.smartumbrella.app.ui.theme.WarningOrange
import com.smartumbrella.app.ui.theme.WarningOrangeBg
import com.smartumbrella.app.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DashboardViewModel,
    onNavigateToPlanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val status = uiState.status
    val isOnline = uiState.isOnline

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
                                imageVector = Icons.Default.Umbrella,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Smart Umbrella",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextNavyPrimary
                            )
                            Text(
                                text = uiState.config.deviceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    DeviceConnectionBadge(isOnline = isOnline)
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextNavyPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SoftBackground
                )
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
            // Rain Warning Alert Banner (Visible when rain is detected)
            RainWarningBanner(visible = status.rainDetected)

            // Weather Status Card (Hero)
            WeatherStatusCard(
                rainDetected = status.rainDetected,
                lastUpdated = status.formattedLastUpdated(),
                rainIntensity = status.rainIntensityLevel()
            )

            // Smart Outing Planner Entry Card
            OutingPlannerCard(
                onNavigateToPlanner = onNavigateToPlanner
            )

            // Temperature & Humidity Metric Cards Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Temperature
                val tempVal = status.temperature?.let { "$it" } ?: "--"
                val tempBadge = when {
                    !status.dhtValid -> "Sensor Error"
                    status.temperature == null -> "Waiting"
                    status.temperature > 32f -> "Warm"
                    status.temperature < 20f -> "Cool"
                    else -> "Optimal"
                }
                MetricCard(
                    title = "Temperature",
                    value = tempVal,
                    unit = "°C",
                    icon = Icons.Default.Thermostat,
                    iconTint = PrimaryTeal,
                    badgeText = tempBadge,
                    badgeBg = if (!status.dhtValid) RainRed.copy(alpha = 0.12f) else PrimaryTeal.copy(alpha = 0.12f),
                    badgeTextColor = if (!status.dhtValid) RainRed else PrimaryTeal,
                    modifier = Modifier.weight(1f)
                )

                // Humidity
                val humVal = status.humidity?.let { "$it" } ?: "--"
                val humBadge = when {
                    !status.dhtValid -> "Sensor Error"
                    status.humidity == null -> "Waiting"
                    status.humidity > 70f -> "Humid"
                    status.humidity < 40f -> "Dry"
                    else -> "Comfortable"
                }
                MetricCard(
                    title = "Humidity",
                    value = humVal,
                    unit = "%",
                    icon = Icons.Default.WaterDrop,
                    iconTint = TealAccent,
                    badgeText = humBadge,
                    badgeBg = if (!status.dhtValid) RainRed.copy(alpha = 0.12f) else TealAccent.copy(alpha = 0.12f),
                    badgeTextColor = if (!status.dhtValid) RainRed else TealAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            // FC-37 Rain Sensor Card
            RainSensorCard(
                rainDetected = status.rainDetected,
                analogValue = status.rainAnalogValue,
                digitalValue = status.rainDigitalValue
            )

            // ESP32 Actuators & Hardware Subsystem Card
            HardwareStatusCard(
                buzzerOn = status.buzzerOn,
                rgbStatus = status.rgbStatus,
                deviceOnline = isOnline,
                lastReceivedTime = status.formattedLastUpdated()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

