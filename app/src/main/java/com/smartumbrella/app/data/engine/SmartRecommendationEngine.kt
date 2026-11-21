package com.smartumbrella.app.data.engine

import com.smartumbrella.app.data.model.HourlyForecastPoint
import com.smartumbrella.app.data.model.OutingWeatherAnalysis
import com.smartumbrella.app.data.model.RecommendationCategory
import com.smartumbrella.app.data.model.UmbrellaRecommendation

/**
 * Recommendation Engine for the Smart Outing Planner.
 *
 * NOTE: The outing recommendation primarily depends on the future weather forecast
 * for the user's selected location and duration. Current ESP32 sensor readings are
 * treated as separate contextual status and not conflated with future predictions.
 */
object SmartRecommendationEngine {

    const val PROBABILITY_THRESHOLD_LOW = 30   // Below 30%: Umbrella not required
    const val PROBABILITY_THRESHOLD_HIGH = 60  // 60%+ : Umbrella recommended
    const val PRECIPITATION_THRESHOLD_LOW_MM = 0.2f  // mm
    const val PRECIPITATION_THRESHOLD_HIGH_MM = 1.0f // mm

    fun evaluate(analysis: OutingWeatherAnalysis): UmbrellaRecommendation {
        val maxProb = analysis.maxPrecipitationProbability
        val totalRain = analysis.totalPrecipitationMm
        val maxHourlyRain = analysis.maxHourlyPrecipitationMm
        val rainHours = analysis.rainPeriods

        return when {
            // Category 3: High rain expected -> UMBRELLA RECOMMENDED
            maxProb >= PROBABILITY_THRESHOLD_HIGH || totalRain >= PRECIPITATION_THRESHOLD_HIGH_MM || maxHourlyRain >= 0.5f -> {
                val periodsDesc = if (rainHours.isNotEmpty()) {
                    "Rain is anticipated during: ${rainHours.joinToString(", ")}."
                } else {
                    "Significant precipitation is anticipated during your outing."
                }
                UmbrellaRecommendation(
                    category = RecommendationCategory.UMBRELLA_RECOMMENDED,
                    title = "Umbrella Recommended",
                    message = "High chance of rainfall during your planned duration. Carrying an umbrella is strongly advised.",
                    forecastSummary = "Peak rain probability: $maxProb% with estimated ${String.format("%.1f", totalRain)} mm total rainfall. $periodsDesc"
                )
            }

            // Category 2: Moderate/possible rain -> CARRY AN UMBRELLA JUST IN CASE
            maxProb >= PROBABILITY_THRESHOLD_LOW || totalRain >= PRECIPITATION_THRESHOLD_LOW_MM || rainHours.isNotEmpty() -> {
                val periodsDesc = if (rainHours.isNotEmpty()) {
                    "Rain or showers possible around ${rainHours.joinToString(", ")}."
                } else {
                    "Isolated showers are possible during your outing window."
                }
                UmbrellaRecommendation(
                    category = RecommendationCategory.CARRY_JUST_IN_CASE,
                    title = "Carry an Umbrella Just in Case",
                    message = "Moderate rain chances predicted during portions of your outing. It is recommended to keep an umbrella handy.",
                    forecastSummary = "Peak rain probability: $maxProb% with estimated ${String.format("%.1f", totalRain)} mm total rainfall. $periodsDesc"
                )
            }

            // Category 1: Clear / Low rain -> UMBRELLA NOT REQUIRED
            else -> {
                UmbrellaRecommendation(
                    category = RecommendationCategory.UMBRELLA_NOT_REQUIRED,
                    title = "Umbrella Not Required",
                    message = "Dry and clear weather is forecast throughout your entire outing window. You should not need an umbrella.",
                    forecastSummary = "Peak rain probability is minimal ($maxProb%) with no significant precipitation predicted."
                )
            }
        }
    }
}
