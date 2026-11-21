package com.smartumbrella.app.data.repository

import com.smartumbrella.app.data.model.HourlyForecastPoint
import com.smartumbrella.app.data.model.LocationCoordinate
import com.smartumbrella.app.data.model.WmoWeatherCodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Repository for interacting with Open-Meteo Weather Forecast and Geocoding REST APIs.
 * Operates independently from Firebase Realtime Database.
 */
class OpenMeteoWeatherRepository {

    companion object {
        private const val FORECAST_BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"
        private const val MAX_FORECAST_DAYS_AHEAD = 14L
        private const val TIMEOUT_MILLIS = 10_000
    }

    /**
     * Converts a city/place name into coordinates using Open-Meteo Geocoding API.
     */
    suspend fun searchLocations(query: String): Result<List<LocationCoordinate>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.length < 2) {
                return@withContext Result.success(emptyList())
            }

            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "$GEOCODING_BASE_URL?name=$encoded&count=5&language=en&format=json"
            val responseJson = executeGetRequest(urlString)

            val root = JSONObject(responseJson)
            if (!root.has("results")) {
                return@withContext Result.success(emptyList())
            }

            val resultsArray = root.getJSONArray("results")
            val list = mutableListOf<LocationCoordinate>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val name = item.getString("name")
                val lat = item.getDouble("latitude")
                val lon = item.getDouble("longitude")
                val admin1 = if (item.has("admin1") && !item.isNull("admin1")) item.getString("admin1") else null
                val country = if (item.has("country") && !item.isNull("country")) item.getString("country") else null
                list.add(LocationCoordinate(name, lat, lon, admin1, country))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves actual hourly weather forecast for the specified date range.
     * Note: Dates must be within the API's valid forecast horizon (today up to 14 days ahead).
     */
    suspend fun fetchHourlyForecast(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<HourlyForecastPoint>> = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            if (startDate.isBefore(today)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Historical weather dates are outside forecast availability. Please select today or an upcoming date.")
                )
            }
            val daysBetween = ChronoUnit.DAYS.between(today, endDate)
            if (daysBetween > MAX_FORECAST_DAYS_AHEAD) {
                return@withContext Result.failure(
                    IllegalArgumentException("Weather forecasts are only available up to $MAX_FORECAST_DAYS_AHEAD days in advance.")
                )
            }

            val dateIsoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
            val startIso = startDate.format(dateIsoFormatter)
            val endIso = endDate.format(dateIsoFormatter)

            val urlString = buildString {
                append(FORECAST_BASE_URL)
                append("?latitude=").append(latitude)
                append("&longitude=").append(longitude)
                append("&hourly=precipitation_probability,precipitation,weather_code")
                append("&start_date=").append(startIso)
                append("&end_date=").append(endIso)
                append("&timezone=auto")
            }

            val responseJson = executeGetRequest(urlString)
            val root = JSONObject(responseJson)

            if (!root.has("hourly")) {
                return@withContext Result.failure(IllegalStateException("API response did not contain hourly forecast data."))
            }

            val hourly = root.getJSONObject("hourly")
            val timeArray = hourly.getJSONArray("time")
            val probArray = hourly.getJSONArray("precipitation_probability")
            val precipArray = hourly.getJSONArray("precipitation")
            val codeArray = hourly.getJSONArray("weather_code")

            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
            val points = mutableListOf<HourlyForecastPoint>()

            for (i in 0 until timeArray.length()) {
                val timeStr = timeArray.getString(i)
                val localDt = LocalDateTime.parse(timeStr)
                val prob = if (probArray.isNull(i)) 0 else probArray.getInt(i)
                val precip = if (precipArray.isNull(i)) 0.0f else precipArray.getDouble(i).toFloat()
                val code = if (codeArray.isNull(i)) 0 else codeArray.getInt(i)
                val formattedHour = localDt.format(timeFormatter)
                val desc = WmoWeatherCodeHelper.getDescription(code)

                points.add(
                    HourlyForecastPoint(
                        timeIso = timeStr,
                        epochSeconds = localDt.toEpochSecond(ZoneOffset.UTC),
                        formattedHour = formattedHour,
                        precipitationProbability = prob,
                        precipitationMm = precip,
                        weatherCode = code,
                        weatherDescription = desc
                    )
                )
            }

            Result.success(points)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGetRequest(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MILLIS
        conn.readTimeout = TIMEOUT_MILLIS
        conn.setRequestProperty("User-Agent", "SmartUmbrella-Android/1.0")

        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorStream = conn.errorStream
            val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            conn.disconnect()
            throw IllegalStateException("Weather API returned HTTP $responseCode: $errorBody")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return responseText
    }
}
