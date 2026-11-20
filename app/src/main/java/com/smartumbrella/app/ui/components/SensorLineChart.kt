package com.smartumbrella.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartumbrella.app.data.model.SensorHistoryItem
import com.smartumbrella.app.ui.theme.BorderSubtle
import com.smartumbrella.app.ui.theme.PrimaryTeal
import com.smartumbrella.app.ui.theme.SurfaceWhite
import com.smartumbrella.app.ui.theme.TealAccent
import com.smartumbrella.app.ui.theme.TextMuted
import com.smartumbrella.app.ui.theme.TextNavyPrimary
import com.smartumbrella.app.ui.theme.TextSecondary

@Composable
fun SensorLineChart(
    historyList: List<SensorHistoryItem>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL TRENDS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextMuted
                    )
                    Text(
                        text = "Temperature & Humidity",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextNavyPrimary
                    )
                }

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(PrimaryTeal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Temp (°C)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(8.dp).background(TealAccent, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Hum (%)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (historyList.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Gathering sensor telemetry data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                val tempValues = historyList.map { it.temperature }
                val humValues = historyList.map { it.humidity }

                val minTemp = (tempValues.minOrNull() ?: 20f) - 2f
                val maxTemp = (tempValues.maxOrNull() ?: 35f) + 2f
                val minHum = (humValues.minOrNull() ?: 40f) - 5f
                val maxHum = (humValues.maxOrNull() ?: 90f) + 5f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Draw 3 horizontal grid lines
                    val gridColor = Color(0xFFF1F5F9)
                    drawLine(gridColor, Offset(0f, 0f), Offset(width, 0f), strokeWidth = 2f)
                    drawLine(gridColor, Offset(0f, height / 2), Offset(width, height / 2), strokeWidth = 2f)
                    drawLine(gridColor, Offset(0f, height), Offset(width, height), strokeWidth = 2f)

                    val stepX = width / (historyList.size - 1)

                    // Draw Temperature Line (PrimaryTeal)
                    val tempPath = Path()
                    historyList.forEachIndexed { i, item ->
                        val x = i * stepX
                        val normalized = ((item.temperature - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                        val y = height - (normalized * height)
                        if (i == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                        drawCircle(PrimaryTeal, radius = 4f, center = Offset(x, y))
                    }
                    drawPath(
                        path = tempPath,
                        color = PrimaryTeal,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )

                    // Draw Humidity Line (TealAccent)
                    val humPath = Path()
                    historyList.forEachIndexed { i, item ->
                        val x = i * stepX
                        val normalized = ((item.humidity - minHum) / (maxHum - minHum)).coerceIn(0f, 1f)
                        val y = height - (normalized * height)
                        if (i == 0) humPath.moveTo(x, y) else humPath.lineTo(x, y)
                        drawCircle(TealAccent, radius = 4f, center = Offset(x, y))
                    }
                    drawPath(
                        path = humPath,
                        color = TealAccent,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time axis indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = historyList.first().formattedTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "Real-time Trend Window",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = historyList.last().formattedTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

