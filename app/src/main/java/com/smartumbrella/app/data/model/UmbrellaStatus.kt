package com.smartumbrella.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model matching the ESP32 Smart Umbrella telemetry published to Firebase Realtime Database.
 * Represents the live sensor and actuator state.
 */
data class UmbrellaStatus(
    val deviceId: String = "umbrella_001",
    val temperature: Float? = null,
    val humidity: Float? = null,
    val rainDetected: Boolean = false,
    val rainAnalogValue: Int? = null,
    val rainDigitalValue: Int? = null,
    val buzzerOn: Boolean = false,
    val rgbStatus: String = "GREEN",
    val deviceOnline: Boolean = false,
    val lastUpdated: Long = 0L
) {
    /**
     * Determines whether data is stale based on heartbeat timeout.
     * Prevents displaying "Online" when ESP32 has disconnected or stopped transmitting.
     */
    fun isDataStale(timeoutMillis: Long = 30_000L): Boolean {
        if (lastUpdated <= 0L) return true
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastUpdated) > timeoutMillis
    }

    /**
     * Effective online status factoring in both ESP32 flag and timestamp freshness.
     */
    fun isEffectivelyOnline(timeoutMillis: Long = 30_000L): Boolean {
        return deviceOnline && !isDataStale(timeoutMillis)
    }

    fun formattedLastUpdated(): String {
        if (lastUpdated <= 0L) return "Waiting for data..."
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date(lastUpdated))
    }

    fun formattedDate(): String {
        if (lastUpdated <= 0L) return "N/A"
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(lastUpdated))
    }

    /**
     * Interprets analog rain reading based on firmware calibration:
     * - Dry: > 3600
     * - Moist / Droplets: 3300 - 3600
     * - Rain detected: < 3300
     * - Heavy Rain: < 2200
     */
    fun rainIntensityLevel(): String {
        val ao = rainAnalogValue ?: return if (rainDetected) "Rain Detected" else "No Rain"
        return when {
            ao < 2000 -> "Heavy Rainfall"
            ao < 3300 -> "Moderate Rain"
            ao < 3600 -> "Light Droplets"
            else -> "Dry Surface"
        }
    }
}

