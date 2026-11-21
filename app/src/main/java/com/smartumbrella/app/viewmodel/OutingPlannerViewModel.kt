package com.smartumbrella.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartumbrella.app.data.engine.SmartRecommendationEngine
import com.smartumbrella.app.data.model.HourlyForecastPoint
import com.smartumbrella.app.data.model.LocationCoordinate
import com.smartumbrella.app.data.model.OutingWeatherAnalysis
import com.smartumbrella.app.data.model.UmbrellaRecommendation
import com.smartumbrella.app.data.repository.OpenMeteoWeatherRepository
import com.smartumbrella.app.util.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class OutingPlannerUiState(
    val selectedLocation: LocationCoordinate? = null,
    val searchQuery: String = "",
    val searchResults: List<LocationCoordinate> = emptyList(),
    val isSearchingLocation: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.now().plusHours(3).withMinute(0).withSecond(0),
    val isLoadingForecast: Boolean = false,
    val errorMessage: String? = null,
    val analysisResult: OutingWeatherAnalysis? = null,
    val recommendation: UmbrellaRecommendation? = null
)

class OutingPlannerViewModel(application: Application) : AndroidViewModel(application) {

    private val weatherRepo = OpenMeteoWeatherRepository()
    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableStateFlow(
        OutingPlannerUiState(
            selectedLocation = LocationCoordinate(
                name = "Nitte",
                latitude = 13.1818,
                longitude = 74.9339,
                admin1 = "Karnataka",
                country = "India"
            )
        )
    )
    val uiState: StateFlow<OutingPlannerUiState> = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.trim().length >= 2) {
            searchLocations(query)
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    private fun searchLocations(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingLocation = true)
            val result = weatherRepo.searchLocations(query)
            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    searchResults = list,
                    isSearchingLocation = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearchingLocation = false
                )
            }
        }
    }

    fun selectLocation(location: LocationCoordinate) {
        _uiState.value = _uiState.value.copy(
            selectedLocation = location,
            searchQuery = location.displayName,
            searchResults = emptyList(),
            errorMessage = null
        )
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingForecast = true, errorMessage = null)
            val result = locationHelper.getCurrentLocation()
            result.onSuccess { loc ->
                _uiState.value = _uiState.value.copy(
                    selectedLocation = loc,
                    searchQuery = loc.displayName,
                    searchResults = emptyList(),
                    isLoadingForecast = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingForecast = false,
                    errorMessage = error.message ?: "Failed to retrieve device location."
                )
            }
        }
    }

    fun setStartDate(date: LocalDate) {
        val current = _uiState.value
        // If end date was before new start date, advance end date
        val newEndDate = if (current.endDate.isBefore(date)) date else current.endDate
        _uiState.value = current.copy(startDate = date, endDate = newEndDate)
    }

    fun setStartTime(time: LocalTime) {
        _uiState.value = _uiState.value.copy(startTime = time)
    }

    fun setEndDate(date: LocalDate) {
        val current = _uiState.value
        if (date.isBefore(current.startDate)) {
            _uiState.value = current.copy(endDate = current.startDate)
        } else {
            _uiState.value = current.copy(endDate = date)
        }
    }

    fun setEndTime(time: LocalTime) {
        _uiState.value = _uiState.value.copy(endTime = time)
    }

    fun checkUmbrellaRequirement() {
        val state = _uiState.value
        val location = state.selectedLocation
        if (location == null) {
            _uiState.value = state.copy(errorMessage = "Please select or search for an outing location.")
            return
        }

        // Validate time range
        val startDateTime = LocalDateTime.of(state.startDate, state.startTime)
        var endDateTime = LocalDateTime.of(state.endDate, state.endTime)

        // Automatically handle midnight crossing if same-day and endTime <= startTime
        if (state.startDate == state.endDate && !endDateTime.isAfter(startDateTime)) {
            endDateTime = endDateTime.plusDays(1)
            _uiState.value = state.copy(endDate = endDateTime.toLocalDate())
        }

        if (!endDateTime.isAfter(startDateTime)) {
            _uiState.value = state.copy(errorMessage = "Outing end time must be after the starting time.")
            return
        }

        val today = LocalDate.now()
        if (state.startDate.isBefore(today)) {
            _uiState.value = state.copy(errorMessage = "Outing start date cannot be in the past.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingForecast = true,
                errorMessage = null,
                analysisResult = null,
                recommendation = null
            )

            val fetchResult = weatherRepo.fetchHourlyForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                startDate = state.startDate,
                endDate = endDateTime.toLocalDate()
            )

            fetchResult.onSuccess { allHourlyPoints ->
                // Filter strictly to the planned outing window [startDateTime, endDateTime]
                val filtered = allHourlyPoints.filter { point ->
                    val pointDt = LocalDateTime.parse(point.timeIso)
                    !pointDt.isBefore(startDateTime) && !pointDt.isAfter(endDateTime)
                }

                if (filtered.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingForecast = false,
                        errorMessage = "No forecast data available for the exact selected time window."
                    )
                    return@onSuccess
                }

                val maxProb = filtered.maxOfOrNull { it.precipitationProbability } ?: 0
                val totalPrecip = filtered.sumOf { it.precipitationMm.toDouble() }.toFloat()
                val maxHourlyPrecip = filtered.maxOfOrNull { it.precipitationMm } ?: 0f

                val rainHours = filtered
                    .filter { it.precipitationProbability >= SmartRecommendationEngine.PROBABILITY_THRESHOLD_LOW || it.precipitationMm > 0.05f }
                    .map { "${it.formattedHour} (${it.precipitationProbability}%)" }

                val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
                val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
                val durationStr = if (startDateTime.toLocalDate() == endDateTime.toLocalDate()) {
                    "${startDateTime.format(timeFormatter)} – ${endDateTime.format(timeFormatter)}, ${startDateTime.format(dateFormatter)}"
                } else {
                    "${startDateTime.format(timeFormatter)} (${startDateTime.format(dateFormatter)}) – ${endDateTime.format(timeFormatter)} (${endDateTime.format(dateFormatter)})"
                }

                val analysis = OutingWeatherAnalysis(
                    location = location,
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    formattedDuration = durationStr,
                    maxPrecipitationProbability = maxProb,
                    totalPrecipitationMm = totalPrecip,
                    maxHourlyPrecipitationMm = maxHourlyPrecip,
                    rainPeriods = rainHours,
                    hourlyForecasts = filtered,
                    fetchedAtMillis = System.currentTimeMillis()
                )

                val recommendation = SmartRecommendationEngine.evaluate(analysis)

                _uiState.value = _uiState.value.copy(
                    isLoadingForecast = false,
                    analysisResult = analysis,
                    recommendation = recommendation,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingForecast = false,
                    errorMessage = error.message ?: "Failed to fetch weather forecast from Open-Meteo."
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
