package com.smartumbrella.app.ui.screens.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartumbrella.app.ui.components.DeviceConnectionBadge
import com.smartumbrella.app.ui.components.RainSensorCard
import com.smartumbrella.app.ui.components.SensorLineChart
import com.smartumbrella.app.ui.theme.BorderSubtle
import com.smartumbrella.app.ui.theme.NormalGreen
import com.smartumbrella.app.ui.theme.PrimaryTeal
import com.smartumbrella.app.ui.theme.RainRed
import com.smartumbrella.app.ui.theme.SoftBackground
import com.smartumbrella.app.ui.theme.SurfaceWhite
import com.smartumbrella.app.ui.theme.TealAccent
import com.smartumbrella.app.ui.theme.TextMuted
import com.smartumbrella.app.ui.theme.TextNavyPrimary
import com.smartumbrella.app.ui.theme.TextSecondary
import com.smartumbrella.app.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailsScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val status = uiState.status
    val history = uiState.history

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
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sensor Telemetry",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextNavyPrimary
                            )
                            Text(
                                text = "Detailed Metrics & Historical Trends",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    DeviceConnectionBadge(isOnline = uiState.isOnline)
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
            // Real-time Historical Trends Chart
            SensorLineChart(historyList = history)

            // Rain Sensor Detailed Metrics
            RainSensorCard(
                rainDetected = status.rainDetected,
                analogValue = status.rainAnalogValue,
                digitalValue = status.rainDigitalValue
            )

            // Detailed Environmental Telemetry Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "ENVIRONMENTAL SENSOR (DHT22)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SensorMetricRow(
                        label = "Ambient Temperature",
                        value = if (status.temperature != null) "${status.temperature} °C" else "Waiting...",
                        subtext = "DHT22 on GPIO 4 | Range -40 to 80°C (±0.5°C)",
                        icon = Icons.Default.Thermostat,
                        iconTint = PrimaryTeal
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SensorMetricRow(
                        label = "Relative Humidity",
                        value = if (status.humidity != null) "${status.humidity} %" else "Waiting...",
                        subtext = "DHT22 on GPIO 4 | Range 0 to 100% RH (±2%)",
                        icon = Icons.Default.WaterDrop,
                        iconTint = TealAccent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SensorMetricRow(
                        label = "Telemetry Last Sync",
                        value = status.formattedLastUpdated(),
                        subtext = "ESP32 Heartbeat Telemetry Frequency: 2000 ms",
                        icon = Icons.Default.Info,
                        iconTint = TextMuted
                    )
                }
            }

            // Hardware Pinout & Calibration Specs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "HARDWARE PINOUT & CALIBRATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CalibrationItem(name = "Rain Trigger Threshold", value = "< 3300 (Droplets trigger alert)")
                    CalibrationItem(name = "Rain Clear Threshold", value = "> 3600 (Plate wiped dry clears alert)")
                    CalibrationItem(name = "Rain Sensor Pins", value = "AO: GPIO 34 | DO: GPIO 27")
                    CalibrationItem(name = "DHT22 Pin", value = "GPIO 4")
                    CalibrationItem(name = "Buzzer Pin", value = "GPIO 25 (Active Buzzer)")
                    CalibrationItem(name = "RGB LED Pins", value = "Red: GPIO 18, Green: 14, Blue: 26")
                    CalibrationItem(name = "I2C LCD 16x2", value = "SDA: GPIO 21, SCL: GPIO 22 (0x27)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SensorMetricRow(
    label: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftBackground, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextNavyPrimary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextNavyPrimary
                )
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun CalibrationItem(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextNavyPrimary
        )
    }
}

