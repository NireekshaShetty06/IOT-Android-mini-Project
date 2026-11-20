package com.smartumbrella.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historical telemetry point for chart visualization and trend analysis.
 */
data class SensorHistoryItem(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val rainAnalogValue: Int = 4095
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

