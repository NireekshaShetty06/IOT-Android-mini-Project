package com.smartumbrella.app.data.model

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Geographic coordinates representing a selected outing location.
 */
data class LocationCoordinate(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (!admin1.isNullOrBlank()) append(", $admin1")
            if (!country.isNullOrBlank()) append(", $country")
        }
}

/**
 * An actual hourly forecast data point retrieved from Open-Meteo.
 * Does not interpolate or fabricate sub-hourly intervals.
 */
data class HourlyForecastPoint(
    val timeIso: String,
    val epochSeconds: Long,
    val formattedHour: String,
    val precipitationProbability: Int,
    val precipitationMm: Float,
    val weatherCode: Int,
    val weatherDescription: String
)

/**
 * Analyzed weather conditions covering the complete user-selected outing window.
 */
data class OutingWeatherAnalysis(
    val location: LocationCoordinate,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val formattedDuration: String,
    val maxPrecipitationProbability: Int,
    val totalPrecipitationMm: Float,
    val maxHourlyPrecipitationMm: Float,
    val rainPeriods: List<String>,
    val hourlyForecasts: List<HourlyForecastPoint>,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Categorical outing recommendations based strictly on forecast conditions.
 */
enum class RecommendationCategory {
    UMBRELLA_NOT_REQUIRED,
    CARRY_JUST_IN_CASE,
    UMBRELLA_RECOMMENDED
}

data class UmbrellaRecommendation(
    val category: RecommendationCategory,
    val title: String,
    val message: String,
    val forecastSummary: String
)

/**
 * Translates WMO weather codes into plain-language conditions.
 */
object WmoWeatherCodeHelper {
    fun getDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61 -> "Slight rain"
        62, 63 -> "Moderate rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snowfall"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Cloudy"
    }
}
