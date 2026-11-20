package com.smartumbrella.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Event log entry recorded when rain is detected or state changes.
 */
data class RainAlertEvent(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String = "Rain Detected",
    val rainStatus: String = "RAIN DETECTED",
    val rainAnalogValue: Int? = null,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val isAcknowledged: Boolean = false
) {
    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formattedTimeOnly(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

